package com.linkvault.app.domain.model;

/** Immutable category metadata (categories.json entry). See DATA_SCHEMA.md §5. */
public final class Category {

    public static final String UNGROUPED_ID = "cat_ungrouped";
    public static final String UNGROUPED_NAME = "Uncategorized";
    public static final int SCHEMA_VERSION = 1;

    private final String id;
    private final String name;
    private final String createdAt;
    private final String updatedAt;
    private final int sortOrder;
    private final boolean system;
    /** Parent category id, or null for root-level (nullable). */
    private final String parentId;

    public Category(String id, String name, String createdAt, String updatedAt,
                    int sortOrder, boolean system) {
        this(id, name, createdAt, updatedAt, sortOrder, system, null);
    }

    public Category(String id, String name, String createdAt, String updatedAt,
                    int sortOrder, boolean system, String parentId) {
        this.id = id;
        this.name = name;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.sortOrder = sortOrder;
        this.system = system;
        this.parentId = (parentId == null || parentId.isEmpty()) ? null : parentId;
    }

    public static Category ungrouped(String nowIso) {
        return new Category(UNGROUPED_ID, UNGROUPED_NAME, nowIso, nowIso, 0, true);
    }

    public Category withName(String newName, String nowIso) {
        return new Category(id, newName, createdAt, nowIso, sortOrder, system, parentId);
    }

    public Category withParent(String newParentId, String nowIso) {
        return new Category(id, name, createdAt, nowIso, sortOrder, system, newParentId);
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getCreatedAt() { return createdAt; }
    public String getUpdatedAt() { return updatedAt; }
    public int getSortOrder() { return sortOrder; }
    public boolean isSystem() { return system; }
    /** Nullable parent id (null = root level). */
    public String getParentId() { return parentId; }

    /**
     * Filesystem folder for this category. Folder name IS the stable ID
     * (ids are already path-safe: "cat_" + uuid / "cat_ungrouped") — renaming
     * a category never touches the filesystem (D-06).
     */
    public String folderName() {
        return id;
    }

    @Override
    public String toString() {
        return "Category{" + id + " '" + name + "' order=" + sortOrder + "}";
    }
}
