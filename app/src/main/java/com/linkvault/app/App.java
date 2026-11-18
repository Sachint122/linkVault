package com.linkvault.app;

import android.app.Application;

import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

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
    }

    /** Idempotent: KEEP keeps the existing chain; the worker self-gates on its flag. */
    private void scheduleAutoBackup() {
        PeriodicWorkRequest request =
                new PeriodicWorkRequest.Builder(AutoBackupWorker.class, 1, TimeUnit.DAYS).build();
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                AutoBackupWorker.WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request);
    }

    public AppContainer getContainer() {
        return container;
    }
}
