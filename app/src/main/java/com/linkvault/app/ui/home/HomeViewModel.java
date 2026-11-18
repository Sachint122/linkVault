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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final MutableLiveData<String> selectedTag = new MutableLiveData<>();
    private final MutableLiveData<List<String>> allTags = new MutableLiveData<>();
    private final MutableLiveData<StorageBanner> banner = new MutableLiveData<>();
    private final MutableLiveData<Event<String>> toast = new MutableLiveData<>();
    private final MutableLiveData<Boolean> refreshing = new MutableLiveData<>();

    private volatile String currentSearchQuery = "";
    private volatile String currentCategoryId = null;
    private volatile VaultItem.ItemType currentTypeFilter = null;
    private volatile String currentTagFilter = null;
    private volatile IndexRepository.SortOrder currentSort = IndexRepository.SortOrder.NEWEST;
    /** childId → parentId, rebuilt on refresh; used to expand subcategory filters. */
    private volatile Map<String, String> parentByChild = Map.of();

    // Windowed rendering (D-16): first paint shows a small window, scrolling
    // to the bottom extends it — keeps big vaults smooth.
    private static final int INITIAL_WINDOW = 50;
    private static final int WINDOW_STEP = 100;
    private volatile List<VaultItem> lastFullResult = List.of();
    private volatile int renderLimit = INITIAL_WINDOW;

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
    public LiveData<String> getSelectedTag() { return selectedTag; }
    public LiveData<List<String>> getAllTags() { return allTags; }
    public LiveData<StorageBanner> getBanner() { return banner; }
    public LiveData<Event<String>> getToast() { return toast; }
    public LiveData<Boolean> getRefreshing() { return refreshing; }

    public void refresh() {
        refreshing.postValue(true);
        io.execute(() -> {
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
                Map<String, String> parents = new HashMap<>();
                for (Category c : cats) {
                    names.put(c.getId(), c.getName());
                    if (c.getParentId() != null) parents.put(c.getId(), c.getParentId());
                }
                parentByChild = parents;

                categories.postValue(cats);
                categoryNames.postValue(names);

                // First paint: index.json fast path on cold start (single file read).
                applyQuery(names);

                // Self-heal: authoritative rescan after the fast paint, so a
                // stale index (kill mid-mutation) corrects itself immediately.
                if (container.indexRepository().consumeColdVerify()) {
                    container.indexRepository().rebuild();
                    applyQuery(names);
                }

                com.linkvault.app.widget.LinkVaultWidgetProvider.updateAll(app);
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

    public void setTagFilter(@Nullable String tag) {
        this.currentTagFilter = tag;
        selectedTag.postValue(tag);
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
                    expandCategory(currentCategoryId),
                    currentTypeFilter,
                    currentSort,
                    names);

            // Tag chips reflect the current search/category context (pre-tag filter).
            java.util.TreeSet<String> tags =
                    new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            for (VaultItem it : result) {
                if (it.getTags() != null) tags.addAll(it.getTags());
            }
            allTags.postValue(new ArrayList<>(tags));

            String tagFilter = currentTagFilter;
            if (tagFilter != null && !tagFilter.isEmpty()) {
                List<VaultItem> tagged = new ArrayList<>();
                for (VaultItem it : result) {
                    if (it.getTags() != null) {
                        for (String t : it.getTags()) {
                            if (t.equalsIgnoreCase(tagFilter)) {
                                tagged.add(it);
                                break;
                            }
                        }
                    }
                }
                result = tagged;
            }
            lastFullResult = result;
            renderLimit = INITIAL_WINDOW;
            publishWindow();
        } catch (Exception e) {
            lastFullResult = List.of();
            visibleItems.postValue(new ArrayList<>());
        }
    }

    private void publishWindow() {
        List<VaultItem> all = lastFullResult;
        int end = Math.min(renderLimit, all.size());
        visibleItems.postValue(new ArrayList<>(all.subList(0, end)));
    }

    /** Selected category + every descendant id (null = no category filter). */
    private Set<String> expandCategory(@Nullable String rootId) {
        if (rootId == null || rootId.isEmpty() || "all".equalsIgnoreCase(rootId)) {
            return null;
        }
        Set<String> out = new HashSet<>();
        out.add(rootId);
        boolean added = true;
        while (added) {
            added = false;
            for (Map.Entry<String, String> e : parentByChild.entrySet()) {
                if (out.contains(e.getValue()) && out.add(e.getKey())) {
                    added = true;
                }
            }
        }
        return out;
    }

    /** Called when the list scrolls near the bottom — extend the window. */
    public void loadMore() {
        if (renderLimit >= lastFullResult.size()) return;
        renderLimit += WINDOW_STEP;
        publishWindow();
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

    /** Manual dead-link check for one link (D-14). Result arrives as a toast. */
    public void checkLink(VaultItem item) {
        if (!item.isLink() || item.getLink() == null) return;
        final String id = item.getId();
        final String url = item.getLink().getUrl();
        io.execute(() -> {
            try {
                int status = com.linkvault.app.domain.DeadLinkChecker.check(url);
                LinkRecord updated = app.getContainer().recordRepository().markChecked(id, status);
                app.getContainer().indexRepository().replaceItem(new VaultItem(updated));
                applyQuery(categoryNames.getValue());
                toast.postValue(new Event(checkMessage(status)));
            } catch (Exception e) {
                toast.postValue(new Event(String.valueOf(e.getMessage())));
            }
        });
    }

    private String checkMessage(int status) {
        if (status == com.linkvault.app.domain.DeadLinkChecker.NETWORK_FAIL) {
            return getApplication().getString(R.string.check_result_offline);
        } else if (status >= 400) {
            return getApplication().getString(R.string.check_result_dead) + " (" + status + ")";
        }
        return getApplication().getString(R.string.check_result_live) + " (" + status + ")";
    }

    /**
     * Optimistic delete: the row animates out instantly from the in-memory
     * cache while the disk delete runs in the background. On failure the
     * item rolls back into the list with a toast.
     */
    public void delete(VaultItem item) {
        IndexRepository idx = app.getContainer().indexRepository();
        // Instant UI path only when the cache is already loaded (Home is on
        // screen, so it is) — pure memory, safe to query on the main thread.
        boolean optimistic = idx.removeItem(item.getId());
        if (optimistic) {
            applyQuery(categoryNames.getValue());
        }
        io.execute(() -> {
            try {
                if (item.isLink()) {
                    app.getContainer().recordRepository().delete(item.getId());
                } else if (item.isFile()) {
                    app.getContainer().fileRepository().delete(item.getId());
                }
                if (optimistic) {
                    idx.persistCache(); // one background write makes it durable
                } else {
                    idx.invalidate();
                }
                refresh();
            } catch (Exception e) {
                if (optimistic) {
                    idx.addItem(item);
                    applyQuery(categoryNames.getValue());
                }
                toast.postValue(new Event(String.valueOf(e.getMessage())));
            }
        });
    }

    /** Batch delete for multi-select mode — optimistic removal, one refresh. */
    public void deleteAll(List<VaultItem> items) {
        if (items == null || items.isEmpty()) return;
        IndexRepository idx = app.getContainer().indexRepository();
        boolean removedAll = true;
        for (VaultItem item : items) {
            removedAll &= idx.removeItem(item.getId());
        }
        final boolean optimistic = removedAll;
        if (optimistic) {
            applyQuery(categoryNames.getValue());
        }
        io.execute(() -> {
            int n = 0;
            List<VaultItem> failed = new ArrayList<>();
            for (VaultItem item : items) {
                try {
                    if (item.isLink()) {
                        app.getContainer().recordRepository().delete(item.getId());
                    } else if (item.isFile()) {
                        app.getContainer().fileRepository().delete(item.getId());
                    }
                    n++;
                } catch (Exception e) {
                    // Skip broken entries; keep deleting the rest.
                    failed.add(item);
                }
            }
            if (optimistic) {
                for (VaultItem item : failed) idx.addItem(item); // roll back misses
                idx.persistCache();
                if (!failed.isEmpty()) applyQuery(categoryNames.getValue());
            } else {
                idx.invalidate();
            }
            toast.postValue(new Event(failed.isEmpty()
                    ? n + " delete ho gaye"
                    : n + " delete, " + failed.size() + " fail"));
            refresh();
        });
    }

    /** Undo (Snackbar): optimistic re-insert, then persist in background. */
    public void restore(LinkRecord record) {
        IndexRepository idx = app.getContainer().indexRepository();
        boolean optimistic = idx.addItem(new VaultItem(record));
        if (optimistic) {
            applyQuery(categoryNames.getValue());
        }
        io.execute(() -> {
            try {
                app.getContainer().recordRepository().create(record);
                if (optimistic) {
                    idx.persistCache();
                } else {
                    idx.invalidate();
                }
                toast.postValue(new Event(getApplication().getString(R.string.undone_toast)));
                refresh();
            } catch (Exception e) {
                if (optimistic) {
                    idx.removeItem(record.getId());
                    applyQuery(categoryNames.getValue());
                }
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
