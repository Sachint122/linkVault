package com.linkvault.app.data.json;

import com.linkvault.app.domain.model.LinkRecord;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Hand-written codec for link_*.json (schema v1). Unknown keys are ignored
 * on read (forward compat); a NEWER schemaVersion is rejected with
 * {@link MalformedRecordException.Kind#NEWER_SCHEMA} so callers never
 * overwrite records from a future app version.
 */
public final class LinkCodec {

    private static final int MAX_TITLE = 300;
    private static final int MAX_DESCRIPTION = 10_000;
    private static final int MAX_TAGS = 20;
    private static final int MAX_TAG_LENGTH = 30;

    public String toJson(LinkRecord r) {
        try {
            JSONObject o = new JSONObject();
            o.put("schemaVersion", r.getSchemaVersion());
            o.put("id", r.getId());
            o.put("type", LinkRecord.TYPE);
            o.put("title", r.getTitle());
            o.put("url", r.getUrl());
            o.put("description", r.getDescription() == null ? JSONObject.NULL : r.getDescription());
            o.put("categoryId", r.getCategoryId());
            JSONObject source = new JSONObject();
            source.put("appName", r.getSourceAppName() == null ? JSONObject.NULL : r.getSourceAppName());
            source.put("packageName", r.getSourcePackageName() == null ? JSONObject.NULL : r.getSourcePackageName());
            o.put("source", source);
            o.put("imageUrl", r.getImageUrl() == null ? JSONObject.NULL : r.getImageUrl());
            JSONArray tags = new JSONArray();
            for (String t : r.getTags()) tags.put(t);
            o.put("tags", tags);
            o.put("pinned", r.isPinned());
            o.put("createdAt", r.getCreatedAt());
            o.put("updatedAt", r.getUpdatedAt());
            return o.toString(2);
        } catch (JSONException e) {
            // Cannot happen with this shape; treat as programming error.
            throw new IllegalStateException("Failed to serialize link record " + r.getId(), e);
        }
    }

    public LinkRecord fromJson(String json, String relativePath) throws MalformedRecordException {
        final JSONObject o;
        try {
            o = new JSONObject(json);
        } catch (JSONException e) {
            throw new MalformedRecordException(
                    MalformedRecordException.Kind.MALFORMED_JSON, relativePath,
                    "Not valid JSON: " + e.getMessage(), e);
        }

        int schemaVersion = o.optInt("schemaVersion", -1);
        if (schemaVersion < 1) {
            throw new MalformedRecordException(
                    MalformedRecordException.Kind.SCHEMA_INVALID, relativePath,
                    "Missing or invalid schemaVersion");
        }
        if (schemaVersion > LinkRecord.SCHEMA_VERSION) {
            throw new MalformedRecordException(
                    MalformedRecordException.Kind.NEWER_SCHEMA, relativePath,
                    "Record schemaVersion " + schemaVersion + " is newer than supported "
                            + LinkRecord.SCHEMA_VERSION);
        }

        String id = requireString(o, "id", relativePath);
        String type = o.optString("type", "");
        if (!LinkRecord.TYPE.equals(type)) {
            throw new MalformedRecordException(
                    MalformedRecordException.Kind.SCHEMA_INVALID, relativePath,
                    "Expected type=link, got '" + type + "'");
        }
        String title = requireString(o, "title", relativePath);
        if (title.isEmpty() || title.length() > MAX_TITLE) {
            throw new MalformedRecordException(
                    MalformedRecordException.Kind.SCHEMA_INVALID, relativePath,
                    "title must be 1.." + MAX_TITLE + " chars");
        }
        String url = requireString(o, "url", relativePath);
        validateUrl(url, relativePath);

        String description = optNullableString(o, "description");
        if (description != null && description.length() > MAX_DESCRIPTION) {
            throw new MalformedRecordException(
                    MalformedRecordException.Kind.SCHEMA_INVALID, relativePath,
                    "description exceeds " + MAX_DESCRIPTION + " chars");
        }
        String categoryId = requireString(o, "categoryId", relativePath);

        String imageUrl = optNullableString(o, "imageUrl");
        if (imageUrl != null) validateUrl(imageUrl, relativePath);

        JSONObject source = o.optJSONObject("source");
        String sourceAppName = source == null ? null : optNullableString(source, "appName");
        String sourcePackageName = source == null ? null : optNullableString(source, "packageName");

        List<String> tags = parseTags(o, relativePath);
        boolean pinned = o.optBoolean("pinned", false);

        String createdAt = requireTimestamp(o, "createdAt", relativePath);
        String updatedAt = requireTimestamp(o, "updatedAt", relativePath);

        return new LinkRecord(schemaVersion, id, title, url, description, categoryId,
                sourceAppName, sourcePackageName, imageUrl, tags, pinned, createdAt, updatedAt);
    }

    private static List<String> parseTags(JSONObject o, String relativePath)
            throws MalformedRecordException {
        JSONArray arr = o.optJSONArray("tags");
        List<String> tags = new ArrayList<>();
        if (arr == null) return tags;
        if (arr.length() > MAX_TAGS) {
            throw new MalformedRecordException(
                    MalformedRecordException.Kind.SCHEMA_INVALID, relativePath,
                    "More than " + MAX_TAGS + " tags");
        }
        for (int i = 0; i < arr.length(); i++) {
            String t = arr.optString(i, null);
            if (t == null || t.trim().isEmpty()) continue;
            String n = LinkRecord.normalizeTag(t);
            if (n.isEmpty()) continue;
            if (n.length() > MAX_TAG_LENGTH) {
                throw new MalformedRecordException(
                        MalformedRecordException.Kind.SCHEMA_INVALID, relativePath,
                        "Tag exceeds " + MAX_TAG_LENGTH + " chars");
            }
            boolean dup = false;
            for (String existing : tags) {
                if (existing.toLowerCase(Locale.ROOT).equals(n.toLowerCase(Locale.ROOT))) {
                    dup = true;
                    break;
                }
            }
            if (!dup) tags.add(n);
        }
        return tags;
    }

    /** Accepts only http/https absolute URLs. */
    static void validateUrl(String url, String relativePath) throws MalformedRecordException {
        if (url == null || url.isEmpty()) {
            throw new MalformedRecordException(
                    MalformedRecordException.Kind.SCHEMA_INVALID, relativePath, "Empty URL");
        }
        try {
            java.net.URI uri = new java.net.URI(url);
            String scheme = uri.getScheme();
            boolean okScheme = "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
            boolean okHost = uri.getHost() != null && !uri.getHost().isEmpty();
            if (!okScheme || !okHost) {
                throw new MalformedRecordException(
                        MalformedRecordException.Kind.SCHEMA_INVALID, relativePath,
                        "URL must be absolute http/https: " + url);
            }
        } catch (java.net.URISyntaxException e) {
            throw new MalformedRecordException(
                    MalformedRecordException.Kind.SCHEMA_INVALID, relativePath,
                    "Malformed URL: " + url, e);
        }
    }

    private static String requireString(JSONObject o, String key, String relativePath)
            throws MalformedRecordException {
        String v = o.optString(key, null);
        if (v == null || v.trim().isEmpty()) {
            throw new MalformedRecordException(
                    MalformedRecordException.Kind.SCHEMA_INVALID, relativePath,
                    "Missing required field '" + key + "'");
        }
        return v;
    }

    private static String optNullableString(JSONObject o, String key) {
        if (!o.has(key) || o.isNull(key)) return null;
        String v = o.optString(key, null);
        return (v == null || v.isEmpty()) ? null : v;
    }

    private static String requireTimestamp(JSONObject o, String key, String relativePath)
            throws MalformedRecordException {
        String v = requireString(o, key, relativePath);
        try {
            Instant.parse(v);
            return v;
        } catch (Exception e) {
            throw new MalformedRecordException(
                    MalformedRecordException.Kind.SCHEMA_INVALID, relativePath,
                    "'" + key + "' is not ISO-8601 UTC: " + v, e);
        }
    }
}
