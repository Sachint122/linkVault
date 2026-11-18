package com.linkvault.app.ai;

import android.content.Context;

import androidx.annotation.Nullable;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.BackoffPolicy;
import androidx.work.WorkManager;

import java.io.File;
import java.util.concurrent.TimeUnit;

/**
 * Single sequential AI job (spec §7):
 *
 * - One unique WorkManager request ("ai_queue_worker", KEEP) — a new link
 *   saved mid-run is picked up by the running loop, never a second worker.
 * - Items live in {@link AiQueueStore} (persisted): dedupe, attempts,
 *   failure state — all survive process death; WorkManager reschedules the
 *   request itself across reboots, {@link #recover()} re-kicks on startup.
 * - Battery-not-low constraint; 8-minute budget per run (worker returns
 *   Result.retry() and continues later).
 */
public class AIQueueManager {

    static final String WORK_NAME = "ai_queue_worker";

    private final Context appContext;
    private final AiQueueStore store;
    private final Object lock = new Object();
    /** True while a request is outstanding (running or waiting for retry). */
    private boolean workerActive;

    public AIQueueManager(Context context) {
        this.appContext = context.getApplicationContext();
        this.store = new AiQueueStore(new File(appContext.getFilesDir(), AiContract.QUEUE_FILE));
    }

    /** Queues one record for analysis (deduped) and kicks the worker. */
    public void enqueue(String recordId) {
        if (recordId == null || recordId.isEmpty()) return;
        synchronized (lock) {
            if (!store.enqueue(recordId)) return;
            kickLocked();
        }
    }

    /** App startup / recovery: restart the job if items are waiting. */
    public void recover() {
        synchronized (lock) {
            kickLocked();
        }
    }

    /** Settings display: {pending, failed} counts. */
    public int[] stats() {
        synchronized (lock) {
            return new int[]{store.pendingCount(), store.failedCount()};
        }
    }

    /** True while the AI worker request is running or waiting for retry. */
    public boolean isWorkerActive() {
        synchronized (lock) {
            return workerActive;
        }
    }

    private void kickLocked() {
        if (!store.hasPending() || workerActive) return;
        try {
            Constraints constraints = new Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build();
            OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(AiWorker.class)
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
                    .build();
            WorkManager.getInstance(appContext)
                    .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request);
            workerActive = true;
        } catch (RuntimeException e) {
            // WorkManager unavailable (e.g. tests) — retry on next enqueue/recover.
            workerActive = false;
        }
    }

    // ------------------------------------------------- worker-facing API

    /**
     * Claims the next item and marks an attempt. Returns null when the queue
     * is empty (also releases the worker flag under the same lock — no
     * enqueue/exit race window).
     */
    @Nullable
    String beginItem() {
        synchronized (lock) {
            String id = store.peek();
            if (id == null) {
                workerActive = false;
                return null;
            }
            store.markAttempt(id);
            return id;
        }
    }

    /**
     * Records the outcome: success removes the item, a failed attempt rotates
     * it for the next run, {@link AiContract#MAX_ATTEMPTS} failures move it
     * to the failed list.
     */
    void finishItem(String id, boolean success, @Nullable String reason) {
        synchronized (lock) {
            if (success) {
                store.complete(id);
            } else if (store.attemptsOf(id) >= AiContract.MAX_ATTEMPTS) {
                store.fail(id, reason);
            } else {
                store.rotate(id);
            }
            if (!store.hasPending()) {
                workerActive = false;
            }
        }
    }
}
