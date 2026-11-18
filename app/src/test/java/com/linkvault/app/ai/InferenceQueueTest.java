package com.linkvault.app.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Shared inference queue (spec §6): FIFO order, one-at-a-time execution,
 * pending + in-flight cancellation, exactly-once delivery, sync helper.
 */
public class InferenceQueueTest {

    /** Scripted generator: records calls, optionally blocks until released. */
    private static final class FakeGen implements InferenceQueue.Generator {
        final List<String> calls = Collections.synchronizedList(new ArrayList<>());
        final AtomicInteger concurrent = new AtomicInteger();
        final AtomicInteger maxConcurrent = new AtomicInteger();
        volatile CountDownLatch blockUntil;   // when set, generate() waits on it
        volatile String reply = "reply";

        @Override
        public String generate(String system, String prompt, int maxOutputToken) {
            calls.add(prompt);
            int now = concurrent.incrementAndGet();
            synchronized (maxConcurrent) {
                if (now > maxConcurrent.get()) maxConcurrent.set(now);
            }
            try {
                CountDownLatch l = blockUntil;
                if (l != null) l.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                concurrent.decrementAndGet();
            }
            return reply;
        }
    }

    private static void waitFor(CountDownLatch l) throws InterruptedException {
        assertTrue("timed out", l.await(5, TimeUnit.SECONDS));
    }

    @Test
    public void fifo_order_preserved() throws Exception {
        FakeGen gen = new FakeGen();
        gen.blockUntil = new CountDownLatch(1);
        InferenceQueue q = new InferenceQueue(gen);
        List<String> order = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch done = new CountDownLatch(3);

        for (int i = 0; i < 3; i++) {
            final String name = "req" + i;
            q.submit("s", name, 10, (out, cancelled) -> {
                order.add(name);
                done.countDown();
            });
        }
        gen.blockUntil.countDown(); // release the pipeline
        waitFor(done);
        assertEquals(List.of("req0", "req1", "req2"), order);
    }

    @Test
    public void never_two_inferences_at_once() throws Exception {
        FakeGen gen = new FakeGen();
        InferenceQueue q = new InferenceQueue(gen);
        CountDownLatch done = new CountDownLatch(8);
        for (int i = 0; i < 8; i++) {
            q.submit("s", "p" + i, 10, (o, c) -> done.countDown());
        }
        waitFor(done);
        assertEquals(1, gen.maxConcurrent.get());
    }

    @Test
    public void pending_cancel_never_runs_and_delivers_cancelled() throws Exception {
        FakeGen gen = new FakeGen();
        gen.blockUntil = new CountDownLatch(1); // first request occupies the worker
        InferenceQueue q = new InferenceQueue(gen);

        q.submit("s", "running", 10, (o, c) -> { });
        // Ensure worker picked it up before queueing the victim.
        long deadline = System.currentTimeMillis() + 2000;
        while (gen.calls.isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }

        AtomicBoolean gotCancelled = new AtomicBoolean(false);
        CountDownLatch delivered = new CountDownLatch(1);
        long victim = q.submit("s", "victim", 10, (o, c) -> {
            gotCancelled.set(c);
            assertNull(o);
            delivered.countDown();
        });
        assertTrue(q.cancel(victim));
        gen.blockUntil.countDown();

        waitFor(delivered);
        assertTrue(gotCancelled.get());
        assertFalse(gen.calls.contains("victim")); // never executed
    }

    @Test
    public void in_flight_cancel_discards_output_and_delivers_cancelled() throws Exception {
        FakeGen gen = new FakeGen();
        gen.blockUntil = new CountDownLatch(1);
        InferenceQueue q = new InferenceQueue(gen);

        AtomicReference<Boolean> cancelledFlag = new AtomicReference<>();
        CountDownLatch delivered = new CountDownLatch(1);
        long id = q.submit("s", "long", 10, (o, c) -> {
            cancelledFlag.set(c);
            assertNull(o); // output discarded, never surfaced
            delivered.countDown();
        });
        long deadline = System.currentTimeMillis() + 2000;
        while (gen.calls.isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertTrue(q.cancel(id));
        gen.blockUntil.countDown(); // inference completes normally…

        waitFor(delivered);
        assertEquals(Boolean.TRUE, cancelledFlag.get()); // …but result dropped
    }

    @Test
    public void normal_completion_delivers_output_once() throws Exception {
        FakeGen gen = new FakeGen();
        gen.reply = "clean output";
        InferenceQueue q = new InferenceQueue(gen);
        AtomicReference<String> got = new AtomicReference<>();
        AtomicInteger deliveries = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(1);
        q.submit("s", "p", 10, (o, c) -> {
            got.set(o);
            deliveries.incrementAndGet();
            done.countDown();
        });
        waitFor(done);
        assertEquals("clean output", got.get());
        assertEquals(1, deliveries.get());
        Thread.sleep(50); // no late duplicate delivery
        assertEquals(1, deliveries.get());
    }

    @Test
    public void generator_exception_is_failed_not_cancelled() throws Exception {
        InferenceQueue q = new InferenceQueue((s, p, m) -> {
            throw new IllegalStateException("boom");
        });
        AtomicReference<String> out = new AtomicReference<>("sentinel");
        AtomicReference<Boolean> cancelled = new AtomicReference<>(false);
        CountDownLatch done = new CountDownLatch(1);
        q.submit("s", "p", 10, (o, c) -> {
            out.set(o);
            cancelled.set(c);
            done.countDown();
        });
        waitFor(done);
        assertNull(out.get());                       // failure = null output
        assertEquals(Boolean.FALSE, cancelled.get()); // but NOT a cancellation
    }

    @Test
    public void generateSync_returns_output_on_success() {
        FakeGen gen = new FakeGen();
        gen.reply = "sync result";
        InferenceQueue q = new InferenceQueue(gen);
        assertEquals("sync result",
                q.generateSync("s", "p", 10, 5000));
    }

    @Test
    public void generateSync_times_out_and_cancels() throws Exception {
        FakeGen gen = new FakeGen();
        gen.blockUntil = new CountDownLatch(1);
        InferenceQueue q = new InferenceQueue(gen);
        CountDownLatch done = new CountDownLatch(1);
        q.submit("s", "hold", 10, (o, c) -> done.countDown());

        long deadline = System.currentTimeMillis() + 2000;
        while (gen.calls.isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        // Second call must time out while the first holds the worker.
        long start = System.currentTimeMillis();
        String out = q.generateSync("s", "slow", 10, 200);
        long took = System.currentTimeMillis() - start;
        assertNull(out);
        assertTrue("returned too fast: " + took, took >= 150);

        gen.blockUntil.countDown();
        waitFor(done);
        // The timed-out request was cancelled — worker never runs "slow".
        assertFalse(gen.calls.contains("slow"));
    }

    @Test
    public void cancel_unknown_id_is_safe_noop() {
        InferenceQueue q = new InferenceQueue((s, p, m) -> "x");
        assertFalse(q.cancel(9999L));
    }

    @Test
    public void empty_reply_counts_as_failure() throws Exception {
        FakeGen gen = new FakeGen();
        gen.reply = "   ";
        InferenceQueue q = new InferenceQueue(gen);
        AtomicReference<String> out = new AtomicReference<>("sentinel");
        CountDownLatch done = new CountDownLatch(1);
        q.submit("s", "p", 10, (o, c) -> {
            out.set(o);
            done.countDown();
        });
        waitFor(done);
        assertNull(out.get());
    }

    @Test
    public void pending_count_reflects_queue() throws Exception {
        FakeGen gen = new FakeGen();
        gen.blockUntil = new CountDownLatch(1);
        InferenceQueue q = new InferenceQueue(gen);
        q.submit("s", "a", 10, (o, c) -> { });
        long deadline = System.currentTimeMillis() + 2000;
        while (gen.calls.isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        q.submit("s", "b", 10, (o, c) -> { });
        q.submit("s", "c", 10, (o, c) -> { });
        assertEquals(2, q.pendingCount());
        gen.blockUntil.countDown();
    }
}
