package com.linkvault.app.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable file record (schema v1). See DATA_SCHEMA.md §4. */
public final class FileRecord {

    public static final int SCHEMA_VERSION = 1;
    public static final String TYPE = "file";

    private final int schemaVersion;
    private final String id;
    private final String displayName;
    private final String relativePath;
    private final String mimeType;
    private final long sizeBytes;
    private final String categoryId;
    private final List<String> tags;
    private final String createdAt;
    private final String updatedAt;

    public FileRecord(
            int schemaVersion,
            String id,
            String displayName,
            String relativePath,
            String mimeType,
            long sizeBytes,
            String categoryId,
            List<String> tags,
            String createdAt,
            String updatedAt) {
        this.schemaVersion = schemaVersion;
        this.id = id;
        this.displayName = displayName;
        this.relativePath = relativePath;
        this.mimeType = mimeType == null ? "application/octet-stream" : mimeType;
        this.sizeBytes = sizeBytes;
        this.categoryId = categoryId;
        this.tags = tags == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(tags));
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static FileRecord create(
            String id, String displayName, String relativePath, String mimeType,
            long sizeBytes, String categoryId, List<String> tags, String nowIso) {
        return new FileRecord(SCHEMA_VERSION, id, displayName, relativePath, mimeType,
                sizeBytes, categoryId, tags, nowIso, nowIso);
    }

    public FileRecord withDisplayName(String newName) {
        return new FileRecord(schemaVersion, id, newName, relativePath, mimeType,
                sizeBytes, categoryId, tags, createdAt, updatedAt);
    }

    public FileRecord withCategoryId(String newCategoryId) {
        return new FileRecord(schemaVersion, id, displayName, relativePath, mimeType,
                sizeBytes, newCategoryId, tags, createdAt, updatedAt);
    }

    public FileRecord withTags(List<String> newTags) {
        return new FileRecord(schemaVersion, id, displayName, relativePath, mimeType,
                sizeBytes, categoryId, newTags, createdAt, updatedAt);
    }

    public FileRecord withUpdatedAt(String nowIso) {
        return new FileRecord(schemaVersion, id, displayName, relativePath, mimeType,
                sizeBytes, categoryId, tags, createdAt, nowIso);
    }

    public int getSchemaVersion() { return schemaVersion; }
    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getRelativePath() { return relativePath; }
    public String getMimeType() { return mimeType; }
    public long getSizeBytes() { return sizeBytes; }
    public String getCategoryId() { return categoryId; }
    public List<String> getTags() { return tags; }
    public String getCreatedAt() { return createdAt; }
    public String getUpdatedAt() { return updatedAt; }

    public static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0);
        return String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    @Override
    public String toString() {
        return "FileRecord{" + id + " " + displayName + " (" + formatSize(sizeBytes) + ")"
                + " cat=" + categoryId + " tags=" + tags.size() + "}";
    }
}
