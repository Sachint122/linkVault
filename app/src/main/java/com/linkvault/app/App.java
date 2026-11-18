package com.linkvault.app;

import android.app.Application;
import android.content.ComponentCallbacks2;

import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.linkvault.app.ai.AutoCleanWorker;
import com.linkvault.app.backup.AutoBackupWorker;
import com.linkvault.app.di.AppContainer;

import java.util.concurrent.TimeUnit;

public class App extends Application {

    private AppContainer container;

    @Override
    public void onCreate() {
        super.onCreate();
        container = new AppContainer(this);
        scheduleAutoBackup();
        scheduleAutoClean();
        // AI queue recovery: re-kick pending analysis jobs after process
        // start (WorkManager itself reschedules across reboots).
        container.aiQueueManager().recover();
    }

    /** Frees the on-device model under real memory pressure (spec §5). */
    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND && container != null) {
            container.localAiManager().release();
        }
    }

    /** Idempotent: KEEP keeps the existing chain; the worker self-gates on its flag. */
    private void scheduleAutoBackup() {
        PeriodicWorkRequest request =
                new PeriodicWorkRequest.Builder(AutoBackupWorker.class, 1, TimeUnit.DAYS).build();
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                AutoBackupWorker.WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request);
    }

    /** Daily auto-clean (D-19): stale empty folders + redundant tags. Links are never touched. */
    private void scheduleAutoClean() {
        PeriodicWorkRequest request =
                new PeriodicWorkRequest.Builder(AutoCleanWorker.class, 1, TimeUnit.DAYS).build();
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                AutoCleanWorker.WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request);
    }

    public AppContainer getContainer() {
        return container;
    }
}
