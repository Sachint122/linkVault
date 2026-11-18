package com.linkvault.app.ui.settings;

import android.app.Application;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.linkvault.app.App;
import com.linkvault.app.data.backup.BackupEngine;
import com.linkvault.app.data.backup.RestoreEngine;
import com.linkvault.app.data.saf.StorageException;
import com.linkvault.app.data.saf.StoragePermissionManager;
import com.linkvault.app.domain.model.Category;
import com.linkvault.app.domain.model.FileRecord;
import com.linkvault.app.domain.model.LinkRecord;
import com.linkvault.app.domain.model.RecordIssue;

import java.io.OutputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Settings: storage status/recovery + Phase 1 self-test + issues scan + backup/restore. */
public class SettingsViewModel extends AndroidViewModel {

    public static final class UiState {
        public final StoragePermissionManager.AccessState accessState;
        @Nullable public final Uri treeUri;
        public final boolean folderAlive;

        UiState(StoragePermissionManager.AccessState accessState, @Nullable Uri treeUri,
                boolean folderAlive) {
            this.accessState = accessState;
            this.treeUri = treeUri;
            this.folderAlive = folderAlive;
        }
    }

    public static final class TestResult {
        public final boolean passed;
        public final String step;
        @Nullable public final String message;

        TestResult(boolean passed, String step, @Nullable String message) {
            this.passed = passed;
            this.step = step;
            this.message = message;
        }
    }

    public static final class AutoCatEntry {
        public final String domain;
        public final String categoryId;
        public final String categoryName;

        AutoCatEntry(String domain, String categoryId, String categoryName) {
            this.domain = domain;
            this.categoryId = categoryId;
            this.categoryName = categoryName;
        }
    }

    private final App app;
    private final StoragePermissionManager pm;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private volatile List<Category> cachedCategories = List.of();

    private final MutableLiveData<UiState> state = new MutableLiveData<>();
    private final MutableLiveData<TestResult> selfTest = new MutableLiveData<>();
    private final MutableLiveData<String> issuesReport = new MutableLiveData<>();
    private final MutableLiveData<String> busy = new MutableLiveData<>();
    private final MutableLiveData<String> backupStatus = new MutableLiveData<>();
    private final MutableLiveData<String> checkProgress = new MutableLiveData<>();
    private final MutableLiveData<List<AutoCatEntry>> autoCatRules = new MutableLiveData<>();
    private final MutableLiveData<Boolean> autoBackupEnabled = new MutableLiveData<>();
    private final MutableLiveData<String> autoBackupStatus = new MutableLiveData<>();
    private final MutableLiveData<RestoreEngine.RestorePreview> restorePreview = new MutableLiveData<>();
    private final MutableLiveData<RestoreEngine.RestoreResult> restoreResult = new MutableLiveData<>();

    public SettingsViewModel(@NonNull Application application) {
        super(application);
        this.app = (App) application;
        this.pm = app.getContainer().permissionManager();
        refresh();
        loadAutoCatRules();
        autoBackupEnabled.postValue(prefs().getBoolean(
                com.linkvault.app.backup.AutoBackupWorker.KEY_ENABLED, false));
        refreshAutoBackupStatus();
    }

    // ----------------------------------------------------------- Auto backup

    private android.content.SharedPreferences prefs() {
        return app.getSharedPreferences(
                com.linkvault.app.backup.AutoBackupWorker.PREFS, android.content.Context.MODE_PRIVATE);
    }

    public void toggleAutoBackup(boolean enable) {
        prefs().edit()
                .putBoolean(com.linkvault.app.backup.AutoBackupWorker.KEY_ENABLED, enable)
                .apply();
        autoBackupEnabled.postValue(enable);
        backupStatus.postValue(enable
                ? "Auto backup ON — daily vault me backups/ folder me."
                : "Auto backup OFF.");
        refreshAutoBackupStatus();
    }

    public void runAutoBackupNow() {
        androidx.work.OneTimeWorkRequest request =
                new androidx.work.OneTimeWorkRequest.Builder(
                        com.linkvault.app.backup.AutoBackupWorker.class).build();
        androidx.work.WorkManager.getInstance(app).enqueue(request);
        backupStatus.postValue("Auto backup shuru — thodi der me backups/ me dikhega.");
    }

    private void refreshAutoBackupStatus() {
        long last = prefs().getLong(
                com.linkvault.app.backup.AutoBackupWorker.KEY_LAST_RUN, 0);
        autoBackupStatus.postValue(last == 0
                ? "Abhi tak koi auto backup nahi."
                : "Last backup: " + java.time.Instant.ofEpochMilli(last)
                        .atZone(java.time.ZoneId.systemDefault())
                        .format(java.time.format.DateTimeFormatter
                                .ofPattern("dd MMM yyyy, HH:mm")));
    }

    public LiveData<UiState> getState() { return state; }
    public LiveData<TestResult> getSelfTest() { return selfTest; }
    public LiveData<String> getIssuesReport() { return issuesReport; }
    public LiveData<String> getBusy() { return busy; }
    public LiveData<String> getBackupStatus() { return backupStatus; }
    public LiveData<String> getCheckProgress() { return checkProgress; }
    public LiveData<List<AutoCatEntry>> getAutoCatRules() { return autoCatRules; }
    public LiveData<Boolean> getAutoBackupEnabled() { return autoBackupEnabled; }
    public LiveData<String> getAutoBackupStatus() { return autoBackupStatus; }

    /** Snapshot of categories for dialogs (loaded during loadAutoCatRules). */
    public List<Category> categoriesSnapshot() { return cachedCategories; }
    public LiveData<RestoreEngine.RestorePreview> getRestorePreview() { return restorePreview; }
    public LiveData<RestoreEngine.RestoreResult> getRestoreResult() { return restoreResult; }

    public void refresh() {
        io.execute(() -> {
            StoragePermissionManager.AccessState s = pm.getAccessState();
            boolean alive = s == StoragePermissionManager.AccessState.GRANTED && pm.probeFolderAlive();
            state.postValue(new UiState(s, pm.getTreeUri(), alive));
        });
    }

    public void reselectFolder(@NonNull Uri treeUri) {
        io.execute(() -> {
            boolean ok = pm.onTreePicked(treeUri);
            if (ok) {
                app.getContainer().onStorageFolderChanged();
                try {
                    app.getContainer().categoryRepository().getAll();
                } catch (StorageException ignored) {
                    // surfaced through refresh()
                }
            }
            refresh();
        });
    }

    public void runSelfTest() {
        busy.postValue("self-test");
        selfTest.postValue(null);
        io.execute(() -> {
            selfTest.postValue(doSelfTest());
            busy.postValue(null);
        });
    }

    private TestResult doSelfTest() {
        String step = "access";
        try {
            if (pm.getAccessState() != StoragePermissionManager.AccessState.GRANTED
                    || !pm.probeFolderAlive()) {
                return new TestResult(false, step, "Folder access not available");
            }

            step = "structure";
            var container = app.getContainer();
            var categories = container.categoryRepository();
            categories.getAll();

            step = "create-category";
            String tempCatName = "zz_selftest_" + System.currentTimeMillis();
            Category tempCat = categories.create(tempCatName);

            step = "create-record";
            var records = container.recordRepository();
            String id = UUID.randomUUID().toString();
            String now = Instant.now().toString();
            LinkRecord rec = LinkRecord.create(
                    id, "Self-test link", "https://example.com/selftest",
                    "created by storage self-test", tempCat.getId(),
                    "LinkVault", null, null,
                    java.util.List.of("selftest"), false, now);
            records.create(rec);

            step = "read-record";
            LinkRecord read = records.read(id, tempCat.getId());
            if (!"https://example.com/selftest".equals(read.getUrl())) {
                return new TestResult(false, step, "URL mismatch after read");
            }

            step = "update-record";
            LinkRecord updated = records.update(read.withTitle("Self-test link v2"));
            if (!"Self-test link v2".equals(updated.getTitle())) {
                return new TestResult(false, step, "Title mismatch after update");
            }
            LinkRecord reread = records.read(id, tempCat.getId());
            if (!"Self-test link v2".equals(reread.getTitle())) {
                return new TestResult(false, step, "Update not persisted across re-read");
            }

            step = "move-record";
            records.moveTo(id, Category.UNGROUPED_ID);
            LinkRecord moved = records.read(id, Category.UNGROUPED_ID);
            if (!Category.UNGROUPED_ID.equals(moved.getCategoryId())) {
                return new TestResult(false, step, "Category mismatch after move");
            }

            step = "delete-record";
            records.delete(id);
            try {
                records.findById(id);
                return new TestResult(false, step, "Record still readable after delete");
            } catch (StorageException expected) {
                // NOT_FOUND — correct.
            }

            step = "cleanup-category";
            categories.delete(tempCat.getId());

            step = "issues-scan";
            List<RecordIssue> issues = records.scanIssues();
            if (!issues.isEmpty()) {
                return new TestResult(false, step,
                        issues.size() + " issue(s): " + issues.get(0));
            }

            return new TestResult(true, "all",
                    "create/read/update/move/delete passed, 0 issues");
        } catch (Exception e) {
            return new TestResult(false, step, String.valueOf(e.getMessage()));
        }
    }

    public void scanIssues() {
        busy.postValue("issues");
        io.execute(() -> {
            try {
                List<RecordIssue> issues = app.getContainer().recordRepository().scanIssues();
                if (issues.isEmpty()) {
                    issuesReport.postValue("NONE");
                } else {
                    StringBuilder sb = new StringBuilder();
                    sb.append(issues.size()).append(" issue(s):\n");
                    for (int i = 0; i < Math.min(issues.size(), 20); i++) {
                        sb.append("• ").append(issues.get(i)).append('\n');
                    }
                    issuesReport.postValue(sb.toString());
                }
            } catch (Exception e) {
                issuesReport.postValue("ERROR: " + e.getMessage());
            }
            busy.postValue(null);
        });
    }

    public void exportBackup(Uri destinationUri) {
        busy.postValue("exporting");
        backupStatus.postValue("Exporting backup to ZIP...");
        io.execute(() -> {
            try (OutputStream out = app.getContentResolver().openOutputStream(destinationUri)) {
                if (out == null) throw new StorageException(StorageException.Reason.IO_ERROR, "Cannot open output stream");
                BackupEngine.BackupSummary summary = app.getContainer().backupEngine().exportBackup(out);
                backupStatus.postValue("Backup complete! Exported " + summary.linkCount + " links, "
                        + summary.fileCount + " files in " + summary.categoryCount + " categories ("
                        + FileRecord.formatSize(summary.totalBytesWritten) + ").");
            } catch (Exception e) {
                backupStatus.postValue("Export failed: " + e.getMessage());
            } finally {
                busy.postValue(null);
            }
        });
    }

    public void previewRestore(byte[] zipBytes) {
        busy.postValue("inspecting");
        io.execute(() -> {
            try {
                RestoreEngine.RestorePreview preview = app.getContainer().restoreEngine().preview(zipBytes);
                restorePreview.postValue(preview);
            } catch (Exception e) {
                backupStatus.postValue("Invalid backup: " + e.getMessage());
            } finally {
                busy.postValue(null);
            }
        });
    }

    public void executeRestore(byte[] zipBytes, RestoreEngine.ConflictPolicy policy) {
        busy.postValue("restoring");
        backupStatus.postValue("Restoring vault data...");
        io.execute(() -> {
            try {
                RestoreEngine.RestoreResult result = app.getContainer().restoreEngine().restore(zipBytes, policy);
                backupStatus.postValue("Restore finished! Added " + result.restoredLinks + " links, "
                        + result.restoredFiles + " files, " + result.restoredCategories + " categories. Skipped: "
                        + result.skippedCount + ".");
            } catch (Exception e) {
                backupStatus.postValue("Restore failed: " + e.getMessage());
            } finally {
                busy.postValue(null);
            }
        });
    }

    public void rebuildIndex() {
        busy.postValue("indexing");
        io.execute(() -> {
            try {
                var list = app.getContainer().indexRepository().rebuild();
                backupStatus.postValue("Search index rebuilt! " + list.size() + " total items indexed.");
            } catch (Exception e) {
                backupStatus.postValue("Index rebuild error: " + e.getMessage());
            } finally {
                busy.postValue(null);
            }
        });
    }

    /** Batch dead-link check (D-14): user-initiated, sequential, shows progress. */
    public void checkAllLinks() {
        busy.postValue("checking");
        io.execute(() -> {
            try {
                var records = app.getContainer().recordRepository();
                List<LinkRecord> links = records.getAllLinks();
                int total = links.size();
                if (total == 0) {
                    backupStatus.postValue("No links to check.");
                    return;
                }
                int live = 0, dead = 0, offline = 0;
                for (int i = 0; i < total; i++) {
                    LinkRecord r = links.get(i);
                    checkProgress.postValue("Checking " + (i + 1) + "/" + total + "…");
                    int status = com.linkvault.app.domain.DeadLinkChecker.check(r.getUrl());
                    records.markChecked(r.getId(), status);
                    if (status == com.linkvault.app.domain.DeadLinkChecker.NETWORK_FAIL) offline++;
                    else if (status >= 400) dead++;
                    else live++;
                }
                app.getContainer().indexRepository().invalidate();
                backupStatus.postValue("Link check done: " + live + " live, "
                        + dead + " dead, " + offline + " unreachable.");
            } catch (Exception e) {
                backupStatus.postValue("Link check failed: " + e.getMessage());
            } finally {
                checkProgress.postValue(null);
                busy.postValue(null);
            }
        });
    }

    // -------------------------------------------------- Auto-categorize rules

    public void loadAutoCatRules() {
        io.execute(() -> {
            try {
                var container = app.getContainer();
                List<com.linkvault.app.domain.model.AutoRule> rules =
                        container.autoCatRepository().getAll();
                List<Category> cats = container.categoryRepository().getAll();
                cachedCategories = cats;
                java.util.Map<String, String> names = new java.util.HashMap<>();
                for (Category c : cats) names.put(c.getId(), c.getName());
                List<AutoCatEntry> out = new ArrayList<>();
                for (var r : rules) {
                    out.add(new AutoCatEntry(r.domain, r.categoryId,
                            names.getOrDefault(r.categoryId, r.categoryId)));
                }
                autoCatRules.postValue(out);
            } catch (Exception e) {
                autoCatRules.postValue(new ArrayList<>());
            }
        });
    }

    public void addAutoCatRule(String domain, String categoryId) {
        io.execute(() -> {
            try {
                app.getContainer().autoCatRepository().addRule(domain, categoryId);
                backupStatus.postValue("Rule added: "
                        + com.linkvault.app.data.repo.AutoCatRepository.normalizeDomain(domain));
                loadAutoCatRules();
            } catch (Exception e) {
                backupStatus.postValue("Rule add failed: " + e.getMessage());
            }
        });
    }

    public void removeAutoCatRule(String domain) {
        io.execute(() -> {
            try {
                app.getContainer().autoCatRepository().removeRule(domain);
                backupStatus.postValue("Rule removed: " + domain);
                loadAutoCatRules();
            } catch (Exception e) {
                backupStatus.postValue("Rule remove failed: " + e.getMessage());
            }
        });
    }

    // ------------------------------------------------------------- Export

    public void exportCsv(Uri destinationUri) {
        exportLinks(destinationUri, true);
    }

    public void exportText(Uri destinationUri) {
        exportLinks(destinationUri, false);
    }

    private void exportLinks(Uri destinationUri, boolean csv) {
        busy.postValue("exporting");
        io.execute(() -> {
            try {
                var container = app.getContainer();
                List<LinkRecord> links = container.recordRepository().getAllLinks();
                List<Category> cats = container.categoryRepository().getAll();
                java.util.Map<String, String> names = new java.util.HashMap<>();
                for (Category c : cats) names.put(c.getId(), c.getName());

                StringBuilder sb = new StringBuilder();
                if (csv) sb.append("title,url,description,category,tags,createdAt\r\n");
                for (LinkRecord r : links) {
                    String cat = names.getOrDefault(r.getCategoryId(), r.getCategoryId());
                    if (csv) {
                        sb.append(csvField(r.getTitle())).append(',')
                                .append(csvField(r.getUrl())).append(',')
                                .append(csvField(r.getDescription() == null ? "" : r.getDescription())).append(',')
                                .append(csvField(cat)).append(',')
                                .append(csvField(String.join(";", r.getTags()))).append(',')
                                .append(csvField(r.getCreatedAt())).append("\r\n");
                    } else {
                        sb.append(r.getTitle()).append('\n').append(r.getUrl());
                        if (r.getDescription() != null && !r.getDescription().isEmpty()) {
                            sb.append('\n').append(r.getDescription());
                        }
                        sb.append("  [").append(cat).append("]\n\n");
                    }
                }
                try (OutputStream out = app.getContentResolver().openOutputStream(destinationUri)) {
                    if (out == null) {
                        throw new StorageException(StorageException.Reason.IO_ERROR,
                                "Cannot open output stream");
                    }
                    out.write(sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
                backupStatus.postValue((csv ? "CSV" : "Text") + " export done: "
                        + links.size() + " links.");
            } catch (Exception e) {
                backupStatus.postValue("Export failed: " + e.getMessage());
            } finally {
                busy.postValue(null);
            }
        });
    }

    private static String csvField(String v) {
        if (v == null) return "";
        boolean quote = v.contains(",") || v.contains("\"")
                || v.contains("\n") || v.contains("\r");
        String esc = v.replace("\"", "\"\"");
        return quote ? "\"" + esc + "\"" : esc;
    }

    @Override
    protected void onCleared() {
        io.shutdown();
        super.onCleared();
    }
}
