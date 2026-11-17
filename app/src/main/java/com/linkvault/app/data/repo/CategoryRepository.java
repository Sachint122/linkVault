package com.linkvault.app.data.repo;

import androidx.annotation.Nullable;

import com.linkvault.app.data.json.CategoryCodec;
import com.linkvault.app.data.json.MalformedRecordException;
import com.linkvault.app.data.saf.DocumentTreeClient;
import com.linkvault.app.data.saf.SafeWriteEngine;
import com.linkvault.app.data.saf.StorageException;
import com.linkvault.app.domain.model.Category;
import com.linkvault.app.domain.policy.PathSanitizer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * categories.json CRUD. The file is the source of truth; an in-memory cache
 * is allowed but rebuilt from the file whenever needed. The uncategorized
 * category always exists and cannot be deleted (FR-03).
 */
public class CategoryRepository {

    static final String REL_DIR = "user/categories";
    static final String FILE_NAME = "categories.json";
    static final String REL_FILE = REL_DIR + "/" + FILE_NAME;

    private final DocumentTreeClient tree;
    private final SafeWriteEngine engine;
    private final CategoryCodec codec;

    private List<Category> cache;

    public CategoryRepository(DocumentTreeClient tree, SafeWriteEngine engine,
                              CategoryCodec codec) {
        this.tree = tree;
        this.engine = engine;
        this.codec = codec;
    }

    /** Loads (and self-heals a missing file), always including uncategorized. */
    public synchronized List<Category> getAll() throws StorageException {
        if (cache != null) return cache;

        android.net.Uri uri = tree.resolveUri(REL_FILE);
        List<Category> loaded;
        if (uri == null) {
            loaded = new ArrayList<>();
            loaded.add(Category.ungrouped(nowIso()));
            persist(loaded);
        } else {
            String json;
            try {
                json = tree.readUtf8(uri);
            } catch (StorageException e) {
                throw e;
            }
            try {
                loaded = codec.fromJson(json, REL_FILE);
            } catch (MalformedRecordException e) {
                // Never overwrite a malformed categories.json — surface it.
                throw new StorageException(StorageException.Reason.VALIDATION_FAILED,
                        "categories.json invalid: " + e.getMessage(), e);
            }
            if (!containsId(loaded, Category.UNGROUPED_ID)) {
                loaded.add(0, Category.ungrouped(nowIso()));
                persist(loaded);
            }
        }
        sortInPlace(loaded);
        cache = Collections.unmodifiableList(loaded);
        return cache;
    }

    @Nullable
    public synchronized Category findById(String id) throws StorageException {
        for (Category c : getAll()) {
            if (c.getId().equals(id)) return c;
        }
        return null;
    }

    public synchronized Category create(String rawName) throws StorageException {
        String name = validateName(rawName, null);
        List<Category> all = new ArrayList<>(getAll());
        String id = "cat_" + UUID.randomUUID();
        String now = nowIso();
        int maxOrder = 0;
        for (Category c : all) maxOrder = Math.max(maxOrder, c.getSortOrder());
        Category created = new Category(id, name, now, now, maxOrder + 1, false);
        all.add(created);
        persist(all);
        return created;
    }

    public synchronized Category rename(String id, String rawName) throws StorageException {
        Category existing = require(id);
        if (existing.isSystem()) {
            throw new StorageException(StorageException.Reason.INVALID_NAME,
                    "System category cannot be renamed");
        }
        String name = validateName(rawName, id);
        List<Category> all = new ArrayList<>(getAll());
        replaceIn(all, existing.withName(name, nowIso()));
        persist(all);
        return existing.withName(name, nowIso());
    }

    /**
     * Deletes a non-system category. Callers (UI) must first move or confirm
     * deletion of its records — this repository only guards the invariant.
     */
    public synchronized void delete(String id) throws StorageException {
        Category existing = require(id);
        if (existing.isSystem()) {
            throw new StorageException(StorageException.Reason.INVALID_NAME,
                    "System category cannot be deleted");
        }
        List<Category> all = new ArrayList<>(getAll());
        all.removeIf(c -> c.getId().equals(id));
        persist(all);
    }

    /** Relative folder that holds this category's records (= its stable ID). */
    public String folderFor(String categoryId) {
        if (!PathSanitizer.isSafeRelativePath(categoryId)) {
            // Should never happen: ids are generated, not user text.
            return REL_DIR + "/" + Category.UNGROUPED_ID;
        }
        return REL_DIR + "/" + categoryId;
    }

    // -------------------------------------------------------------- helpers

    private Category require(String id) throws StorageException {
        Category c = findById(id);
        if (c == null) {
            throw new StorageException(StorageException.Reason.NOT_FOUND,
                    "Category not found: " + id);
        }
        return c;
    }

    private String validateName(String rawName, String selfId) throws StorageException {
        String name = rawName == null ? "" : rawName.trim().replaceAll("\\s+", " ");
        if (name.isEmpty() || name.length() > 60) {
            throw new StorageException(StorageException.Reason.INVALID_NAME,
                    "Category name must be 1-60 characters");
        }
        for (Category c : getAll()) {
            if (c.getId().equals(selfId)) continue;
            if (c.getName().toLowerCase(Locale.ROOT).equals(name.toLowerCase(Locale.ROOT))) {
                throw new StorageException(StorageException.Reason.ALREADY_EXISTS,
                        "Category '" + name + "' already exists");
            }
        }
        return name;
    }

    private void persist(List<Category> categories) throws StorageException {
        String now = nowIso();
        String json = codec.toJson(categories, now);
        engine.writeJson(REL_DIR, FILE_NAME, json,
                s -> codec.fromJson(s, REL_FILE));
        cache = Collections.unmodifiableList(new ArrayList<>(categories));
    }

    private static boolean containsId(List<Category> list, String id) {
        for (Category c : list) if (c.getId().equals(id)) return true;
        return false;
    }

    private static void replaceIn(List<Category> list, Category updated) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId().equals(updated.getId())) {
                list.set(i, updated);
                return;
            }
        }
    }

    private static void sortInPlace(List<Category> list) {
        list.sort(Comparator.comparingInt(Category::getSortOrder)
                .thenComparing(Category::getName, String.CASE_INSENSITIVE_ORDER));
    }

    private static String nowIso() {
        return Instant.now().toString();
    }
}
