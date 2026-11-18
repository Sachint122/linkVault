package com.linkvault.app.ai;

import androidx.annotation.Nullable;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Centralized inference request manager (spec §6): ONE shared queue in front
 * of the single {@link LocalAiManager} engine, used by BOTH the link
 * summarization worker and the AI Writing Assistant sidebar.
 *
 * Guarantees:
 * - Strict FIFO: requests run sequentially on one daemon thread — never two
 *   inferences at once (memory control on mid-range devices).
 * - Pending cancellation: a request cancelled before it starts is never run.
 * - In-flight cancellation: LiteRT-LM 0.19.1 has NO cancel API for a running
 *   {@code sendMessage} — the safest supported behaviour is: let the running
 *   inference finish, then DISCARD its output (listener gets
 *   {@code cancelled=true, output=null}). The CPU cost is documented.
 * - No silent drops: every submitted request gets exactly one
 *   {@link Listener#onDone} delivery (result, failure as null output, or
 *   cancellation), proven by the per-request state machine:
 *
 *   PENDING(0) --worker picks--> RUNNING(1) --done--> delivered(3)
 *   PENDING(0) --cancel--> CANCELLED(2): cancel() itself delivers.
 *   RUNNING(1)  --cancel--> CANCELLED(2): worker delivers on completion.
 *
 * Listener callbacks arrive on the queue thread OR on the cancel() caller
 * thread (pending case) — UI callers must marshal to the main thread.
 */
public class InferenceQueue {

    /** The single engine behind the queue (production: LocalAiManager::generate). */
    public interface Generator {
        @Nullable
        String generate(String system, String prompt, int maxOutputToken);
    }

    /** Exactly-once completion callback (see state machine above). */
    public interface Listener {
        /** output == null && cancelled == false → generation failed/empty; cancelled → user/system cancelled. */
        void onDone(@Nullable String output, boolean cancelled);
    }

    private static final int ST_PENDING = 0;
    private static final int ST_RUNNING = 1;
    private static final int ST_CANCELLED = 2;
    private static final int ST_DONE = 3;

    private static final class Req {
        final long id;
        final String system;
        final String prompt;
        final int maxTokens;
        final Listener listener;
        final AtomicInteger state = new AtomicInteger(ST_PENDING);

        Req(long id, String system, String prompt, int maxTokens, Listener listener) {
            this.id = id;
            this.system = system;
            this.prompt = prompt;
            this.maxTokens = maxTokens;
            this.listener = listener;
        }
    }

    private final Generator generator;
    private final LinkedBlockingQueue<Req> queue = new LinkedBlockingQueue<>();
    private final ConcurrentHashMap<Long, Req> tracked = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1);
    private final Thread worker;

    public InferenceQueue(Generator generator) {
        this.generator = generator;
        this.worker = new Thread(this::runLoop, "linkvault-ai-queue");
        this.worker.setDaemon(true);
        this.worker.start();
    }

    /** FIFO submission; returns a handle id usable with {@link #cancel(long)}. */
    public long submit(String system, String prompt, int maxOutputToken, Listener listener) {
        Req r = new Req(nextId.getAndIncrement(), system, prompt, maxOutputToken, listener);
        tracked.put(r.id, r);
        try {
            queue.put(r);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            tracked.remove(r.id);
            listener.onDone(null, true);
        }
        return r.id;
    }

    /**
     * Cancels a request. Pending → removed from execution (never runs).
     * Running → output will be discarded on completion. Already finished →
     * no-op. Returns true if this call triggered the cancellation.
     */
    public boolean cancel(long id) {
        Req r = tracked.get(id);
        if (r == null) return false;
        // PENDING: we take delivery ownership and notify immediately.
        if (r.state.compareAndSet(ST_PENDING, ST_CANCELLED)) {
            finish(r);
            r.listener.onDone(null, true);
            return true;
        }
        // RUNNING: flip to CANCELLED; the worker discards + delivers.
        if (r.state.compareAndSet(ST_RUNNING, ST_CANCELLED)) {
            return true;
        }
        return false;
    }

    /** Requests waiting in line (not counting the running one). */
    public int pendingCount() {
        return queue.size();
    }

    /**
     * Blocking helper for sequential callers (AiWorker): waits up to
     * {@code timeoutMs} INCLUDING queue wait; on timeout the request is
     * cancelled. Returns null on failure/timeout (caller falls back).
     */
    @Nullable
    public String generateSync(String system, String prompt, int maxOutputToken, long timeoutMs) {
        final AtomicReference<String> out = new AtomicReference<>();
        final CountDownLatch latch = new CountDownLatch(1);
        long id = submit(system, prompt, maxOutputToken, (output, cancelled) -> {
            if (!cancelled) out.set(output);
            latch.countDown();
        });
        try {
            if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                cancel(id); // queued or running → cancelled safely
                return null;
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            cancel(id);
            return null;
        }
        return out.get();
    }

    private void runLoop() {
        for (;;) {
            final Req r;
            try {
                r = queue.take();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return; // shutdown — tests only; production thread lives with the process
            }
            // Claim; fails when cancelled while still pending (cancel() already delivered).
            if (!r.state.compareAndSet(ST_PENDING, ST_RUNNING)) {
                tracked.remove(r.id);
                continue;
            }
            String result = null;
            try {
                result = generator.generate(r.system, r.prompt, r.maxTokens);
            } catch (Throwable ignored) {
                // engine-level failure → delivered as null output
            }
            String output = result == null || result.trim().isEmpty() ? null : result;
            // Complete: normal → deliver output; cancelled mid-run → deliver cancelled.
            if (r.state.compareAndSet(ST_RUNNING, ST_DONE)) {
                finish(r);
                r.listener.onDone(output, false);
            } else {
                // state == CANCELLED (cancel-during-run) — discard output.
                finish(r);
                r.listener.onDone(null, true);
            }
        }
    }

    private void finish(Req r) {
        tracked.remove(r.id);
    }
}
