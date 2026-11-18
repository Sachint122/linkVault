package com.linkvault.app.domain.model;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable link record (schema v1). See DATA_SCHEMA.md §3. */
public final class LinkRecord {

    public static final int SCHEMA_VERSION = 1;
    public static final String TYPE = "link";

    /** AI processing status values (null = never queued). */
    public static final String AI_STATUS_PENDING = "pending";
    public static final String AI_STATUS_DONE = "done";
    public static final String AI_STATUS_FAILED = "failed";
    /** AI result sources. */
    public static final String AI_SOURCE_RULE = "rule";
    public static final String AI_SOURCE_LOCAL_AI = "local_ai";
    /** aiConfidence when no AI result was stored. */
    public static final double AI_CONFIDENCE_UNSET = -1;

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
    // --------------------------------------------------- on-device AI fields
    /** Auto-generated one-line summary; null = none yet. Never overwritten once set. */
    @Nullable private final String summary;
    /** One of AI_STATUS_*; null = never queued for analysis. */
    @Nullable private final String aiStatus;
    /** 0..1 confidence of the last analysis; {@link #AI_CONFIDENCE_UNSET} = none. */
    private final double aiConfidence;
    /** One of AI_SOURCE_*; null = none. */
    @Nullable private final String aiSource;
    /** ISO time of the last analysis; null = never. */
    @Nullable private final String aiAnalyzedAt;
    /** Analyzer contract version that produced the result; 0 = none. */
    private final int aiVersion;

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
        this(schemaVersion, id, title, url, description, categoryId, sourceAppName,
                sourcePackageName, imageUrl, tags, pinned, createdAt, updatedAt,
                checkedAt, httpStatus, null, null, AI_CONFIDENCE_UNSET, null, null, 0);
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
            int httpStatus,
            @Nullable String summary,
            @Nullable String aiStatus,
            double aiConfidence,
            @Nullable String aiSource,
            @Nullable String aiAnalyzedAt,
            int aiVersion) {
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
        this.summary = summary;
        this.aiStatus = aiStatus;
        this.aiConfidence = aiConfidence;
        this.aiSource = aiSource;
        this.aiAnalyzedAt = aiAnalyzedAt;
        this.aiVersion = aiVersion;
    }

    public static LinkRecord create(
            String id, String title, String url, String description,
            String categoryId, String sourceAppName, String sourcePackageName,
            String imageUrl, List<String> tags, boolean pinned, String nowIso) {
        return new LinkRecord(SCHEMA_VERSION, id, title, url, description, categoryId,
                sourceAppName, sourcePackageName, imageUrl, tags, pinned, nowIso, nowIso);
    }

    public LinkRecord withUpdatedAt(String nowIso) {
        return copy(title, url, description, categoryId, tags, pinned, nowIso,
                checkedAt, httpStatus, summary, aiStatus, aiConfidence, aiSource,
                aiAnalyzedAt, aiVersion);
    }

    public LinkRecord withTitle(String newTitle) {
        return copy(newTitle, url, description, categoryId, tags, pinned, updatedAt,
                checkedAt, httpStatus, summary, aiStatus, aiConfidence, aiSource,
                aiAnalyzedAt, aiVersion);
    }

    public LinkRecord withDescription(String newDescription) {
        return copy(title, url, newDescription, categoryId, tags, pinned, updatedAt,
                checkedAt, httpStatus, summary, aiStatus, aiConfidence, aiSource,
                aiAnalyzedAt, aiVersion);
    }

    public LinkRecord withCategoryId(String newCategoryId) {
        return copy(title, url, description, newCategoryId, tags, pinned, updatedAt,
                checkedAt, httpStatus, summary, aiStatus, aiConfidence, aiSource,
                aiAnalyzedAt, aiVersion);
    }

    public LinkRecord withTags(List<String> newTags) {
        return copy(title, url, description, categoryId, newTags, pinned, updatedAt,
                checkedAt, httpStatus, summary, aiStatus, aiConfidence, aiSource,
                aiAnalyzedAt, aiVersion);
    }

    public LinkRecord withPinned(boolean newPinned) {
        return copy(title, url, description, categoryId, tags, newPinned, updatedAt,
                checkedAt, httpStatus, summary, aiStatus, aiConfidence, aiSource,
                aiAnalyzedAt, aiVersion);
    }

    /** Records a dead-link check result. Does NOT bump updatedAt (not a content edit). */
    public LinkRecord withCheck(String checkedAtIso, int status) {
        return copy(title, url, description, categoryId, tags, pinned, updatedAt,
                checkedAtIso, status, summary, aiStatus, aiConfidence, aiSource,
                aiAnalyzedAt, aiVersion);
    }

    /**
     * Applies an on-device AI analysis result. Does NOT bump updatedAt
     * (not a user content edit).
     *
     * @param summaryFinal  resolved summary (caller enforces fill-if-null)
     * @param tagsFinal     resolved tags (caller enforces additive union)
     * @param categoryFinal resolved category id (caller enforces ungrouped-only)
     */
    public LinkRecord withAiApplied(String summaryFinal, List<String> tagsFinal,
                                    String categoryFinal, String status, String source,
                                    double confidence, String analyzedAt, int version) {
        return copy(title, url, description, categoryFinal, tagsFinal, pinned, updatedAt,
                checkedAt, httpStatus, summaryFinal, status, confidence, source,
                analyzedAt, version);
    }

    /** Copies AI fields from {@code other} when it has an analysis (restore paths). */
    public LinkRecord withAiFrom(LinkRecord other) {
        if (other == null || other.getAiStatus() == null) return this;
        return copy(title, url, description, categoryId, tags, pinned, updatedAt,
                checkedAt, httpStatus, other.getSummary(), other.getAiStatus(),
                other.getAiConfidence(), other.getAiSource(), other.getAiAnalyzedAt(),
                other.getAiVersion());
    }

    private LinkRecord copy(String title, String url, String description,
                            String categoryId, List<String> tags, boolean pinned,
                            String updatedAt, String checkedAt, int httpStatus,
                            String summary, String aiStatus, double aiConfidence,
                            String aiSource, String aiAnalyzedAt, int aiVersion) {
        return new LinkRecord(schemaVersion, id, title, url, description, categoryId,
                sourceAppName, sourcePackageName, imageUrl, tags, pinned, createdAt,
                updatedAt, checkedAt, httpStatus, summary, aiStatus, aiConfidence,
                aiSource, aiAnalyzedAt, aiVersion);
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
    @Nullable public String getSummary() { return summary; }
    @Nullable public String getAiStatus() { return aiStatus; }
    public double getAiConfidence() { return aiConfidence; }
    @Nullable public String getAiSource() { return aiSource; }
    @Nullable public String getAiAnalyzedAt() { return aiAnalyzedAt; }
    public int getAiVersion() { return aiVersion; }

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
