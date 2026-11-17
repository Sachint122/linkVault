package com.linkvault.app.ui.categories;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.linkvault.app.App;
import com.linkvault.app.R;
import com.linkvault.app.data.saf.StorageException;
import com.linkvault.app.domain.model.Category;
import com.linkvault.app.domain.model.LinkRecord;
import com.linkvault.app.ui.common.Event;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Category management (FR-03): list+counts, create/rename/delete policies. */
public class CategoriesViewModel extends AndroidViewModel {

    public static final class Row {
        public final Category category;
        public final int linkCount;
        Row(Category category, int linkCount) {
            this.category = category;
            this.linkCount = linkCount;
        }
    }

    public static final class DeleteRequest {
        public final Category category;
        public final int linkCount;
        DeleteRequest(Category category, int linkCount) {
            this.category = category;
            this.linkCount = linkCount;
        }
    }

    private final App app;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private final MutableLiveData<List<Row>> rows = new MutableLiveData<>();
    private final MutableLiveData<Event<String>> toast = new MutableLiveData<>();
    private final MutableLiveData<Event<DeleteRequest>> deleteRequest = new MutableLiveData<>();

    public CategoriesViewModel(@NonNull Application application) {
        super(application);
        this.app = (App) application;
        refresh();
    }

    public LiveData<List<Row>> getRows() { return rows; }
    public LiveData<Event<String>> getToast() { return toast; }
    public LiveData<Event<DeleteRequest>> getDeleteRequest() { return deleteRequest; }

    public void refresh() {
        io.execute(() -> {
            try {
                var container = app.getContainer();
                List<Category> cats = container.categoryRepository().getAll();
                var records = container.recordRepository();
                List<Row> out = new ArrayList<>();
                for (Category c : cats) {
                    out.add(new Row(c, records.countIn(c.getId())));
                }
                rows.postValue(out);
            } catch (StorageException e) {
                toast.postValue(new Event(e.getMessage() == null
                        ? "error" : e.getMessage()));
            }
        });
    }

    public void create(String name) {
        io.execute(() -> {
            try {
                app.getContainer().categoryRepository().create(name);
                toast.postValue(new Event(getApplication().getString(R.string.category_created)));
                refresh();
            } catch (StorageException e) {
                toast.postValue(new Event(errorMessage(e)));
            }
        });
    }

    public void rename(Category category, String newName) {
        io.execute(() -> {
            try {
                app.getContainer().categoryRepository().rename(category.getId(), newName);
                toast.postValue(new Event(getApplication().getString(R.string.category_renamed)));
                refresh();
            } catch (StorageException e) {
                toast.postValue(new Event(errorMessage(e)));
            }
        });
    }

    /**
     * First step of delete: emits a DeleteRequest so the UI can show the
     * mandatory non-empty prompt (FR-03) before anything is destroyed.
     */
    public void requestDelete(Category category, int linkCount) {
        if (category.isSystem()) {
            toast.postValue(new Event(getApplication().getString(
                    R.string.category_error, "System category cannot be deleted")));
            return;
        }
        if (linkCount > 0) {
            deleteRequest.postValue(new Event(new DeleteRequest(category, linkCount)));
        } else {
            delete(category, true);
        }
    }

    /** @param moveItems true = move records to uncategorized before deleting. */
    public void delete(Category category, boolean moveItems) {
        io.execute(() -> {
            try {
                var container = app.getContainer();
                var records = container.recordRepository();
                if (moveItems) {
                    List<LinkRecord> all = records.getAllLinks();
                    for (LinkRecord r : all) {
                        if (r.getCategoryId().equals(category.getId())) {
                            records.moveTo(r.getId(), Category.UNGROUPED_ID);
                        }
                    }
                }
                container.categoryRepository().delete(category.getId());
                toast.postValue(new Event(getApplication().getString(R.string.category_deleted)));
                refresh();
            } catch (StorageException e) {
                toast.postValue(new Event(errorMessage(e)));
            }
        });
    }

    private static String errorMessage(StorageException e) {
        String msg = e.getMessage();
        if (msg == null) return "error";
        switch (e.getReason()) {
            case ALREADY_EXISTS:
            case INVALID_NAME:
            case NOT_FOUND:
            case VALIDATION_FAILED:
                return msg;
            default:
                return msg;
        }
    }

    public Map<String, String> categoryNamesSnapshot() {
        List<Row> r = rows.getValue();
        Map<String, String> out = new HashMap<>();
        if (r != null) {
            for (Row row : r) out.put(row.category.getId(), row.category.getName());
        }
        return out;
    }

    @Override
    protected void onCleared() {
        io.shutdown();
        super.onCleared();
    }
}
