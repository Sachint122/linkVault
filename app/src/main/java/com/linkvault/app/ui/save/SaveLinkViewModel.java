package com.linkvault.app.ui.save;

import android.app.Application;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.linkvault.app.App;
import com.linkvault.app.data.saf.StorageException;
import com.linkvault.app.domain.model.Category;
import com.linkvault.app.domain.model.LinkRecord;
import com.linkvault.app.domain.policy.UrlPolicy;
import com.linkvault.app.ui.common.Event;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Save/edit review screen state (FR-01, FR-02, D-07, D-09). */
public class SaveLinkViewModel extends AndroidViewModel {

    public static final class Existing {
        public final LinkRecord record;
        public final List<String> otherMatches;
        Existing(LinkRecord record, List<String> otherMatches) {
            this.record = record;
            this.otherMatches = otherMatches;
        }
    }

    private final App app;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private final MutableLiveData<List<Category>> categories = new MutableLiveData<>();
    private final MutableLiveData<Event<List<LinkRecord>>> duplicateCheck =
            new MutableLiveData<>();
    private final MutableLiveData<Event<Boolean>> saved = new MutableLiveData<>();
    private final MutableLiveData<Event<String>> error = new MutableLiveData<>();
    private final MutableLiveData<LinkRecord> editRecord = new MutableLiveData<>();
    private final MutableLiveData<String> sourceLabel = new MutableLiveData<>();

    @Nullable private String loadedRecordId;

    public SaveLinkViewModel(@NonNull Application application) {
        super(application);
        this.app = (App) application;
        io.execute(() -> {
            try {
                categories.postValue(app.getContainer().categoryRepository().getAll());
            } catch (StorageException e) {
                error.postValue(new Event(e.getMessage() == null
                        ? "categories unreadable" : e.getMessage()));
            }
        });
    }

    public LiveData<List<Category>> getCategories() { return categories; }
    public LiveData<Event<List<LinkRecord>>> getDuplicateCheck() { return duplicateCheck; }
    public LiveData<Event<Boolean>> getSaved() { return saved; }
    public LiveData<Event<String>> getError() { return error; }
    public LiveData<LinkRecord> getEditRecord() { return editRecord; }
    public LiveData<String> getSourceLabel() { return sourceLabel; }

    /** Loads an existing record for edit mode. Emits error when missing. */
    public void loadForEdit(String recordId) {
        loadedRecordId = recordId;
        io.execute(() -> {
            try {
                LinkRecord r = app.getContainer().recordRepository().findById(recordId);
                editRecord.postValue(r);
            } catch (Exception e) {
                error.postValue(new Event("Record nahi mila"));
            }
        });
    }

    /** Resolves a source package name to a human label. */
    public void resolveSourceLabel(@Nullable String packageName) {
        if (packageName == null) {
            sourceLabel.postValue(null);
            return;
        }
        io.execute(() -> {
            try {
                PackageManager pm = getApplication().getPackageManager();
                ApplicationInfo ai = pm.getApplicationInfo(packageName, 0);
                sourceLabel.postValue(pm.getApplicationLabel(ai).toString());
            } catch (Exception e) {
                sourceLabel.postValue(packageName);
            }
        });
    }

    /** Async duplicate probe (D-07) — never blocks, caller decides. */
    public void checkDuplicate(String url, @Nullable String selfRecordId) {
        io.execute(() -> {
            try {
                List<LinkRecord> matches =
                        app.getContainer().recordRepository().findDuplicates(url);
                List<LinkRecord> others = new ArrayList<>();
                for (LinkRecord m : matches) {
                    if (selfRecordId == null || !m.getId().equals(selfRecordId)) {
                        others.add(m);
                    }
                }
                duplicateCheck.postValue(new Event<>(others));
            } catch (Exception e) {
                duplicateCheck.postValue(new Event<>(new ArrayList<>()));
            }
        });
    }

    /**
     * Validates + persists. For edit mode pass the loaded record (createdAt
     * and source preserved unless overridden).
     */
    public void save(String url, String title, String description,
                     String categoryId, List<String> tags, boolean pinned,
                     @Nullable String sourcePackage, @Nullable String sourceAppName) {
        if (!UrlPolicy.isValid(url)) {
            error.postValue(new Event("URL_INVALID"));
            return;
        }
        String finalTitle = (title == null || title.trim().isEmpty())
                ? LinkRecord.fallbackTitle(url) : title.trim();
        String finalCategory = (categoryId == null || categoryId.isEmpty())
                ? Category.UNGROUPED_ID : categoryId;
        String finalUrl = url.trim();

        io.execute(() -> {
            try {
                var repo = app.getContainer().recordRepository();
                if (loadedRecordId != null) {
                    LinkRecord existing = repo.findById(loadedRecordId);
                    LinkRecord updated = new LinkRecord(
                            existing.getSchemaVersion(),
                            existing.getId(),
                            finalTitle,
                            finalUrl,
                            (description == null || description.trim().isEmpty())
                                    ? existing.getDescription() : description.trim(),
                            finalCategory,
                            sourceAppName != null ? sourceAppName : existing.getSourceAppName(),
                            sourcePackage != null ? sourcePackage : existing.getSourcePackageName(),
                            existing.getImageUrl(),
                            tags,
                            pinned,
                            existing.getCreatedAt(),
                            Instant.now().toString());
                    repo.update(updated);
                } else {
                    // Auto-categorize rule wins when the user picked no folder.
                    String cat = finalCategory;
                    if (Category.UNGROUPED_ID.equals(cat)) {
                        String ruled = app.getContainer().autoCatRepository().matchFor(finalUrl);
                        if (ruled != null) cat = ruled;
                    }
                    LinkRecord rec = LinkRecord.create(
                            UUID.randomUUID().toString(),
                            finalTitle, finalUrl,
                            (description == null || description.trim().isEmpty())
                                    ? null : description.trim(),
                            cat,
                            sourceAppName, sourcePackage, null,
                            tags, pinned, Instant.now().toString());
                    repo.create(rec);
                    // Queue background AI analysis (dedupe + rule-only
                    // fallback handled in the worker; never blocks save).
                    app.getContainer().aiQueueManager().enqueue(rec.getId());
                }
                app.getContainer().indexRepository().invalidate();
                com.linkvault.app.widget.LinkVaultWidgetProvider.updateAll(app);
                saved.postValue(new Event<>(true));
            } catch (StorageException e) {
                error.postValue(new Event(e.getMessage() == null ? "save failed" : e.getMessage()));
            } catch (Exception e) {
                error.postValue(new Event(String.valueOf(e.getMessage())));
            }
        });
    }

    /**
     * Saves an imported file (FR-06) into the selected category with tags.
     */
    public void saveFile(android.net.Uri uri, String displayName, @Nullable String mime,
                         String categoryId, List<String> tags) {
        String finalCategory = (categoryId == null || categoryId.isEmpty())
                ? Category.UNGROUPED_ID : categoryId;
        io.execute(() -> {
            try {
                app.getContainer().fileRepository().importFile(
                        uri, displayName, mime, finalCategory, tags);
                app.getContainer().indexRepository().invalidate();
                saved.postValue(new Event<>(true));
            } catch (StorageException e) {
                error.postValue(new Event(e.getMessage() == null ? "file save failed" : e.getMessage()));
            } catch (Exception e) {
                error.postValue(new Event(String.valueOf(e.getMessage())));
            }
        });
    }

    /**
     * Saves a batch of incoming shared files into the selected category.
     */
    public void saveBatchFiles(List<android.net.Uri> uris, String categoryId, List<String> tags) {
        String finalCategory = (categoryId == null || categoryId.isEmpty())
                ? Category.UNGROUPED_ID : categoryId;
        io.execute(() -> {
            try {
                for (android.net.Uri u : uris) {
                    com.linkvault.app.data.share.SharedFilePayload fp =
                            com.linkvault.app.data.share.SharedFilePayload.fromUri(
                                    app.getContentResolver(), u, null);
                    app.getContainer().fileRepository().importFile(
                            u, fp.displayName, fp.mimeType, finalCategory, tags);
                }
                app.getContainer().indexRepository().invalidate();
                saved.postValue(new Event<>(true));
            } catch (Exception e) {
                error.postValue(new Event("Batch save error: " + e.getMessage()));
            }
        });
    }

    @Override
    protected void onCleared() {
        io.shutdown();
        super.onCleared();
    }
}
