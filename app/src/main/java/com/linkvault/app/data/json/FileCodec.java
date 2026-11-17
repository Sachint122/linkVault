package com.linkvault.app.data.json;

import com.linkvault.app.domain.model.FileRecord;
import com.linkvault.app.domain.model.LinkRecord;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Hand-written codec for file_*.json (schema v1). See DATA_SCHEMA.md §4.
 */
public final class FileCodec {

    private static final int MAX_NAME = 255;
    private static final int MAX_TAGS = 20;
    private static final int MAX_TAG_LENGTH = 30;

    public String toJson(FileRecord r) {
        try {
            JSONObject o = new JSONObject();
            o.put("schemaVersion", r.getSchemaVersion());
            o.put("id", r.getId());
            o.put("type", FileRecord.TYPE);
            o.put("displayName", r.getDisplayName());
            o.put("relativePath", r.getRelativePath());
            o.put("mimeType", r.getMimeType());
            o.put("sizeBytes", r.getSizeBytes());
            o.put("categoryId", r.getCategoryId());
            JSONArray tags = new JSONArray();
            for (String t : r.getTags()) tags.put(t);
            o.put("tags", tags);
            o.put("createdAt", r.getCreatedAt());
            o.put("updatedAt", r.getUpdatedAt());
            return o.toString(2);
        } catch (JSONException e) {
            throw new IllegalStateException("Failed to serialize file record " + r.getId(), e);
        }
    }

    public FileRecord fromJson(String json, String relativePath) throws MalformedRecordException {
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
        if (schemaVersion > FileRecord.SCHEMA_VERSION) {
            throw new MalformedRecordException(
                    MalformedRecordException.Kind.NEWER_SCHEMA, relativePath,
                    "Record schemaVersion " + schemaVersion + " is newer than supported "
                            + FileRecord.SCHEMA_VERSION);
        }

        String id = requireString(o, "id", relativePath);
        String type = o.optString("type", "");
        if (!FileRecord.TYPE.equals(type)) {
            throw new MalformedRecordException(
                    MalformedRecordException.Kind.SCHEMA_INVALID, relativePath,
                    "Expected type=file, got '" + type + "'");
        }
        String displayName = requireString(o, "displayName", relativePath);
        if (displayName.isEmpty() || displayName.length() > MAX_NAME) {
            throw new MalformedRecordException(
                    MalformedRecordException.Kind.SCHEMA_INVALID, relativePath,
                    "displayName must be 1.." + MAX_NAME + " chars");
        }
        String fileRelPath = requireString(o, "relativePath", relativePath);
        String mimeType = o.optString("mimeType", "application/octet-stream");
        long sizeBytes = o.optLong("sizeBytes", 0);
        String categoryId = requireString(o, "categoryId", relativePath);

        List<String> tags = parseTags(o, relativePath);
        String createdAt = requireTimestamp(o, "createdAt", relativePath);
        String updatedAt = requireTimestamp(o, "updatedAt", relativePath);

        return new FileRecord(schemaVersion, id, displayName, fileRelPath, mimeType,
                sizeBytes, categoryId, tags, createdAt, updatedAt);
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
