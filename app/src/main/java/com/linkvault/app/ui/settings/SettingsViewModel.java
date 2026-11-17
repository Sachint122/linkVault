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

    private final App app;
    private final StoragePermissionManager pm;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private final MutableLiveData<UiState> state = new MutableLiveData<>();
    private final MutableLiveData<TestResult> selfTest = new MutableLiveData<>();
    private final MutableLiveData<String> issuesReport = new MutableLiveData<>();
    private final MutableLiveData<String> busy = new MutableLiveData<>();
    private final MutableLiveData<String> backupStatus = new MutableLiveData<>();
    private final MutableLiveData<RestoreEngine.RestorePreview> restorePreview = new MutableLiveData<>();
    private final MutableLiveData<RestoreEngine.RestoreResult> restoreResult = new MutableLiveData<>();

    public SettingsViewModel(@NonNull Application application) {
        super(application);
        this.app = (App) application;
        this.pm = app.getContainer().permissionManager();
        refresh();
    }

    public LiveData<UiState> getState() { return state; }
    public LiveData<TestResult> getSelfTest() { return selfTest; }
    public LiveData<String> getIssuesReport() { return issuesReport; }
    public LiveData<String> getBusy() { return busy; }
    public LiveData<String> getBackupStatus() { return backupStatus; }
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

    @Override
    protected void onCleared() {
        io.shutdown();
        super.onCleared();
    }
}
