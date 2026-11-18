package com.linkvault.app.ai;

import android.content.Context;

import androidx.annotation.Nullable;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Process-wide facade over the on-device LLM. Lazy singleton-style (held by
 * {@link com.linkvault.app.di.AppContainer}):
 *
 * - No model file → {@link #isModelReady()} false → pipeline stays rule-only,
 *   zero cost, zero crash (graceful fallback, spec §5).
 * - Model present → first use loads it on the WORKER thread (~10s), then it
 *   stays hot for the process lifetime.
 * - Every generation runs on a throwaway daemon thread with a hard timeout,
 *   so a hung native call can never wedge the queue (the thread is
 *   abandoned; the next call gets a fresh one).
 * - {@link #release()} (onTrimMemory) is a no-op while a generation is in
 *   flight — never closes the engine under a running inference.
 */
public class LocalAiManager {

    private static final long MODEL_MIN_BYTES = 1024L * 1024L; // >1MB sanity

    private final File modelFile;
    private final File cacheDir;
    private final Object lock = new Object();

    @Nullable private LocalAiEngine engine;
    private boolean initFailed;   // latched after an actual init failure
    private int inFlight;

    public LocalAiManager(Context context) {
        this.modelFile = new File(context.getFilesDir(), AiContract.MODEL_FILE_NAME);
        this.cacheDir = new File(context.getFilesDir(), "ai_cache");
    }

    /** Model file present (and plausibly sized). Checked cheaply every call. */
    public boolean isModelReady() {
        return modelFile.isFile() && modelFile.length() >= MODEL_MIN_BYTES;
    }

    /** True when the engine can generate right now (model + init OK). */
    public boolean ensureReady() {
        synchronized (lock) {
            if (engine != null && engine.isReady()) return true;
            if (initFailed) return false;
            if (!isModelReady()) return false;
            if (!cacheDir.exists()) cacheDir.mkdirs();
            LocalAiEngine e = new LocalAiEngine(
                    modelFile.getAbsolutePath(), cacheDir.getAbsolutePath());
            if (!e.initialize()) {
                initFailed = true; // don't retry a 10s load every job
                return false;
            }
            engine = e;
            return true;
        }
    }

    /**
     * Blocking generation with {@link AiContract#GEN_TIMEOUT_MS} cap.
     * Returns null when unavailable/failed/timed out — callers fall back
     * to the rule-based result. Never called on the main thread.
     */
    @Nullable
    public String generate(String system, String prompt, int maxOutputToken) {
        final LocalAiEngine e;
        synchronized (lock) {
            if (!ensureReady()) return null;
            e = engine;
            if (e == null) return null;
            inFlight++;
        }
        try {
            ExecutorService ex = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "linkvault-ai-gen");
                t.setDaemon(true);
                return t;
            });
            try {
                Future<String> future =
                        ex.submit(() -> e.generate(system, prompt, maxOutputToken));
                return future.get(AiContract.GEN_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (TimeoutException te) {
                return null; // hung call: abandon the thread, keep the queue moving
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return null;
            } catch (Exception other) {
                return null;
            } finally {
                ex.shutdownNow();
            }
        } finally {
            synchronized (lock) {
                inFlight--;
            }
        }
    }

    /** Frees the model under memory pressure; no-op while generating. */
    public void release() {
        LocalAiEngine e;
        synchronized (lock) {
            if (inFlight > 0) return; // busy — next onTrimMemory retries
            e = engine;
            engine = null;
            initFailed = false; // allow a fresh attempt later
        }
        if (e != null) e.release();
    }
}
