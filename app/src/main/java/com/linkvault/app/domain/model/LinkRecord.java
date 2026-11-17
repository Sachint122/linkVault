package com.linkvault.app.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable link record (schema v1). See DATA_SCHEMA.md §3. */
public final class LinkRecord {

    public static final int SCHEMA_VERSION = 1;
    public static final String TYPE = "link";

    private final int schemaVersion;
    private final String id;
    private final String title;
    private final String url;
    private final String description;
    private final String categoryId;
    private final String sourceAppName;
    private final String sourcePackageName;
    private final String imageUrl;
    private final List<String> tags;
    private final boolean pinned;
    private final String createdAt;
    private final String updatedAt;

    public LinkRecord(
            int schemaVersion,
            String id,
            String title,
            String url,
            String description,
            String categoryId,
            String sourceAppName,
            String sourcePackageName,
            String imageUrl,
            List<String> tags,
            boolean pinned,
            String createdAt,
            String updatedAt) {
        this.schemaVersion = schemaVersion;
        this.id = id;
        this.title = title;
        this.url = url;
        this.description = description;
        this.categoryId = categoryId;
        this.sourceAppName = sourceAppName;
        this.sourcePackageName = sourcePackageName;
        this.imageUrl = imageUrl;
        this.tags = tags == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(tags));
        this.pinned = pinned;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static LinkRecord create(
            String id, String title, String url, String description,
            String categoryId, String sourceAppName, String sourcePackageName,
            String imageUrl, List<String> tags, boolean pinned, String nowIso) {
        return new LinkRecord(SCHEMA_VERSION, id, title, url, description, categoryId,
                sourceAppName, sourcePackageName, imageUrl, tags, pinned, nowIso, nowIso);
    }

    public LinkRecord withUpdatedAt(String nowIso) {
        return new LinkRecord(schemaVersion, id, title, url, description, categoryId,
                sourceAppName, sourcePackageName, imageUrl, tags, pinned, createdAt, nowIso);
    }

    public LinkRecord withTitle(String newTitle) {
        return new LinkRecord(schemaVersion, id, newTitle, url, description, categoryId,
                sourceAppName, sourcePackageName, imageUrl, tags, pinned, createdAt, updatedAt);
    }

    public LinkRecord withDescription(String newDescription) {
        return new LinkRecord(schemaVersion, id, title, url, newDescription, categoryId,
                sourceAppName, sourcePackageName, imageUrl, tags, pinned, createdAt, updatedAt);
    }

    public LinkRecord withCategoryId(String newCategoryId) {
        return new LinkRecord(schemaVersion, id, title, url, description, newCategoryId,
                sourceAppName, sourcePackageName, imageUrl, tags, pinned, createdAt, updatedAt);
    }

    public LinkRecord withTags(List<String> newTags) {
        return new LinkRecord(schemaVersion, id, title, url, description, categoryId,
                sourceAppName, sourcePackageName, imageUrl, newTags, pinned, createdAt, updatedAt);
    }

    public LinkRecord withPinned(boolean newPinned) {
        return new LinkRecord(schemaVersion, id, title, url, description, categoryId,
                sourceAppName, sourcePackageName, imageUrl, tags, newPinned, createdAt, updatedAt);
    }

    public int getSchemaVersion() { return schemaVersion; }
    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getUrl() { return url; }
    public String getDescription() { return description; }
    public String getCategoryId() { return categoryId; }
    public String getSourceAppName() { return sourceAppName; }
    public String getSourcePackageName() { return sourcePackageName; }
    public String getImageUrl() { return imageUrl; }
    public List<String> getTags() { return tags; }
    public boolean isPinned() { return pinned; }
    public String getCreatedAt() { return createdAt; }
    public String getUpdatedAt() { return updatedAt; }

    /** Fallback title from the URL host when no title was provided. */
    public static String fallbackTitle(String url) {
        try {
            java.net.URI uri = java.net.URI.create(url);
            String host = uri.getHost();
            if (host == null || host.isEmpty()) return url;
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (Exception e) {
            return url;
        }
    }

    /** Normalizes a tag: trim, collapse inner spaces, cap length. */
    public static String normalizeTag(String tag) {
        if (tag == null) return "";
        String t = tag.trim().replaceAll("\\s+", " ");
        if (t.length() > 30) t = t.substring(0, 30);
        return t;
    }

    @Override
    public String toString() {
        return "LinkRecord{" + id + " " + title + " " + url
                + " cat=" + categoryId + " tags=" + tags.size()
                + " pinned=" + pinned + "}";
    }
}
