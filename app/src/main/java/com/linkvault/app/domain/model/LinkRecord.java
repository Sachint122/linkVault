package com.linkvault.app.domain.model;

import androidx.annotation.Nullable;

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
    /** ISO time of the last manual dead-link check; null = never checked. */
    @Nullable private final String checkedAt;
    /** HTTP status from the last check: 0 = unset, -1 = unreachable, else code. */
    private final int httpStatus;

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
        this(schemaVersion, id, title, url, description, categoryId, sourceAppName,
                sourcePackageName, imageUrl, tags, pinned, createdAt, updatedAt, null, 0);
    }

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
            String updatedAt,
            @Nullable String checkedAt,
            int httpStatus) {
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
        this.checkedAt = checkedAt;
        this.httpStatus = httpStatus;
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
                sourceAppName, sourcePackageName, imageUrl, tags, pinned, createdAt, nowIso,
                checkedAt, httpStatus);
    }

    public LinkRecord withTitle(String newTitle) {
        return new LinkRecord(schemaVersion, id, newTitle, url, description, categoryId,
                sourceAppName, sourcePackageName, imageUrl, tags, pinned, createdAt, updatedAt,
                checkedAt, httpStatus);
    }

    public LinkRecord withDescription(String newDescription) {
        return new LinkRecord(schemaVersion, id, title, url, newDescription, categoryId,
                sourceAppName, sourcePackageName, imageUrl, tags, pinned, createdAt, updatedAt,
                checkedAt, httpStatus);
    }

    public LinkRecord withCategoryId(String newCategoryId) {
        return new LinkRecord(schemaVersion, id, title, url, description, newCategoryId,
                sourceAppName, sourcePackageName, imageUrl, tags, pinned, createdAt, updatedAt,
                checkedAt, httpStatus);
    }

    public LinkRecord withTags(List<String> newTags) {
        return new LinkRecord(schemaVersion, id, title, url, description, categoryId,
                sourceAppName, sourcePackageName, imageUrl, newTags, pinned, createdAt, updatedAt,
                checkedAt, httpStatus);
    }

    public LinkRecord withPinned(boolean newPinned) {
        return new LinkRecord(schemaVersion, id, title, url, description, categoryId,
                sourceAppName, sourcePackageName, imageUrl, tags, newPinned, createdAt, updatedAt,
                checkedAt, httpStatus);
    }

    /** Records a dead-link check result. Does NOT bump updatedAt (not a content edit). */
    public LinkRecord withCheck(String checkedAtIso, int status) {
        return new LinkRecord(schemaVersion, id, title, url, description, categoryId,
                sourceAppName, sourcePackageName, imageUrl, tags, pinned, createdAt, updatedAt,
                checkedAtIso, status);
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
    @Nullable public String getCheckedAt() { return checkedAt; }
    public int getHttpStatus() { return httpStatus; }

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
