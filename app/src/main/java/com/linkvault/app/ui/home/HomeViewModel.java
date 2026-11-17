package com.linkvault.app.ui.home;

import android.app.Application;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.linkvault.app.App;
import com.linkvault.app.R;
import com.linkvault.app.data.repo.IndexRepository;
import com.linkvault.app.data.saf.StorageException;
import com.linkvault.app.data.saf.StoragePermissionManager;
import com.linkvault.app.domain.model.Category;
import com.linkvault.app.domain.model.FileRecord;
import com.linkvault.app.domain.model.LinkRecord;
import com.linkvault.app.domain.model.VaultItem;
import com.linkvault.app.ui.common.Event;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Browse-list & search state (FR-04, FR-05, FR-06).
 * Powered by IndexRepository for fast multi-token search, sorting, and item filtering.
 */
public class HomeViewModel extends AndroidViewModel {

    public static final class StorageBanner {
        public final boolean visible;
        public final String text;
        StorageBanner(boolean visible, String text) {
            this.visible = visible;
            this.text = text;
        }
    }

    private final App app;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private final MutableLiveData<List<VaultItem>> visibleItems = new MutableLiveData<>();
    private final MutableLiveData<List<Category>> categories = new MutableLiveData<>();
    private final MutableLiveData<Map<String, String>> categoryNames = new MutableLiveData<>();
    private final MutableLiveData<String> selectedCategoryId = new MutableLiveData<>();
    private final MutableLiveData<VaultItem.ItemType> selectedTypeFilter = new MutableLiveData<>();
    private final MutableLiveData<StorageBanner> banner = new MutableLiveData<>();
    private final MutableLiveData<Event<String>> toast = new MutableLiveData<>();
    private final MutableLiveData<Boolean> refreshing = new MutableLiveData<>();

    private volatile String currentSearchQuery = "";
    private volatile String currentCategoryId = null;
    private volatile VaultItem.ItemType currentTypeFilter = null;
    private volatile IndexRepository.SortOrder currentSort = IndexRepository.SortOrder.NEWEST;

    public HomeViewModel(@NonNull Application application) {
        super(application);
        this.app = (App) application;
        selectedCategoryId.postValue(null);
        selectedTypeFilter.postValue(null);
        // First load happens in HomeFragment.onResume() — avoids doing the
        // disk scan twice on open (constructor + onResume).
    }

    public LiveData<List<VaultItem>> getVisibleItems() { return visibleItems; }
    public LiveData<List<Category>> getCategories() { return categories; }
    public LiveData<Map<String, String>> getCategoryNames() { return categoryNames; }
    public LiveData<String> getSelectedCategoryId() { return selectedCategoryId; }
    public LiveData<VaultItem.ItemType> getSelectedTypeFilter() { return selectedTypeFilter; }
    public LiveData<StorageBanner> getBanner() { return banner; }
    public LiveData<Event<String>> getToast() { return toast; }
    public LiveData<Boolean> getRefreshing() { return refreshing; }

    public void refresh() {
        refreshing.postValue(true);
        io.execute(() -> {
            long t0 = android.os.SystemClock.elapsedRealtime();
            StoragePermissionManager pm = app.getContainer().permissionManager();
            StoragePermissionManager.AccessState s = pm.getAccessState();
            boolean alive = s == StoragePermissionManager.AccessState.GRANTED && pm.probeFolderAlive();
            if (s == StoragePermissionManager.AccessState.GRANTED && alive) {
                banner.postValue(new StorageBanner(false, ""));
            } else if (s == StoragePermissionManager.AccessState.NO_FOLDER) {
                banner.postValue(new StorageBanner(true,
                        getApplication().getString(R.string.storage_state_no_folder)));
            } else {
                banner.postValue(new StorageBanner(true,
                        getApplication().getString(R.string.storage_state_revoked)));
            }

            try {
                var container = app.getContainer();
                List<Category> cats = container.categoryRepository().getAll();
                Map<String, String> names = new HashMap<>();
                for (Category c : cats) names.put(c.getId(), c.getName());

                categories.postValue(cats);
                categoryNames.postValue(names);

                // First paint: index.json fast path on cold start (single file read).
                applyQuery(names);
                android.util.Log.d("LV", "first paint " +
                        (android.os.SystemClock.elapsedRealtime() - t0) + "ms");

                // Self-heal: authoritative rescan after the fast paint, so a
                // stale index (kill mid-mutation) corrects itself immediately.
                if (container.indexRepository().consumeColdVerify()) {
                    long t1 = android.os.SystemClock.elapsedRealtime();
                    container.indexRepository().rebuild();
                    applyQuery(names);
                    android.util.Log.d("LV", "verify rescan " +
                            (android.os.SystemClock.elapsedRealtime() - t1) + "ms");
                }
            } catch (StorageException e) {
                toast.postValue(new Event(e.getMessage() == null ? "storage error" : e.getMessage()));
                visibleItems.postValue(new ArrayList<>());
            } finally {
                refreshing.postValue(false);
            }
        });
    }

    public void selectCategory(@Nullable String categoryId) {
        this.currentCategoryId = categoryId;
        selectedCategoryId.postValue(categoryId);
        io.execute(() -> applyQuery(categoryNames.getValue()));
    }

    public void setTypeFilter(@Nullable VaultItem.ItemType type) {
        this.currentTypeFilter = type;
        selectedTypeFilter.postValue(type);
        io.execute(() -> applyQuery(categoryNames.getValue()));
    }

    public void search(String query) {
        this.currentSearchQuery = query;
        io.execute(() -> applyQuery(categoryNames.getValue()));
    }

    public void setSortOrder(IndexRepository.SortOrder sortOrder) {
        this.currentSort = sortOrder;
        io.execute(() -> applyQuery(categoryNames.getValue()));
    }

    private void applyQuery(Map<String, String> names) {
        try {
            var indexRepo = app.getContainer().indexRepository();
            List<VaultItem> result = indexRepo.query(
                    currentSearchQuery,
                    currentCategoryId,
                    currentTypeFilter,
                    currentSort,
                    names);
            visibleItems.postValue(result);
        } catch (Exception e) {
            visibleItems.postValue(new ArrayList<>());
        }
    }

    // ------------------------------------------------------------ Actions

    public void togglePin(LinkRecord record) {
        io.execute(() -> {
            try {
                app.getContainer().recordRepository()
                        .update(record.withPinned(!record.isPinned()));
                app.getContainer().indexRepository().invalidate();
                refresh();
            } catch (Exception e) {
                toast.postValue(new Event(String.valueOf(e.getMessage())));
            }
        });
    }

    public void moveTo(VaultItem item, String newCategoryId) {
        io.execute(() -> {
            try {
                if (item.isLink() && item.getLink() != null) {
                    app.getContainer().recordRepository().moveTo(item.getId(), newCategoryId);
                } else if (item.isFile() && item.getFile() != null) {
                    app.getContainer().fileRepository().moveTo(item.getId(), newCategoryId);
                }
                app.getContainer().indexRepository().invalidate();
                toast.postValue(new Event(getApplication().getString(R.string.move_done)));
                refresh();
            } catch (Exception e) {
                toast.postValue(new Event(String.valueOf(e.getMessage())));
            }
        });
    }

    public void delete(VaultItem item) {
        io.execute(() -> {
            try {
                if (item.isLink()) {
                    app.getContainer().recordRepository().delete(item.getId());
                } else if (item.isFile()) {
                    app.getContainer().fileRepository().delete(item.getId());
                }
                app.getContainer().indexRepository().invalidate();
                refresh();
            } catch (Exception e) {
                toast.postValue(new Event(String.valueOf(e.getMessage())));
            }
        });
    }

    public void restore(LinkRecord record) {
        io.execute(() -> {
            try {
                app.getContainer().recordRepository().create(record);
                app.getContainer().indexRepository().invalidate();
                toast.postValue(new Event(getApplication().getString(R.string.undone_toast)));
                refresh();
            } catch (Exception e) {
                toast.postValue(new Event(String.valueOf(e.getMessage())));
            }
        });
    }

    @Nullable
    public Uri resolveFileUri(FileRecord record) {
        try {
            return app.getContainer().fileRepository().resolveFileUri(record);
        } catch (Exception e) {
            return null;
        }
    }

    public List<Category> categoriesSnapshot() {
        List<Category> c = categories.getValue();
        return c == null ? new ArrayList<>() : c;
    }

    @Override
    protected void onCleared() {
        io.shutdown();
        super.onCleared();
    }
}
