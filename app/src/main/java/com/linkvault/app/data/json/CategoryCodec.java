package com.linkvault.app.data.json;

import com.linkvault.app.domain.model.Category;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Codec for categories.json (schema v1). See DATA_SCHEMA.md §5. */
public final class CategoryCodec {

    public String toJson(List<Category> categories, String updatedAtIso) {
        try {
            JSONObject root = new JSONObject();
            root.put("schemaVersion", Category.SCHEMA_VERSION);
            root.put("updatedAt", updatedAtIso);
            JSONArray arr = new JSONArray();
            for (Category c : categories) {
                JSONObject o = new JSONObject();
                o.put("id", c.getId());
                o.put("name", c.getName());
                o.put("createdAt", c.getCreatedAt());
                o.put("updatedAt", c.getUpdatedAt());
                o.put("sortOrder", c.getSortOrder());
                o.put("system", c.isSystem());
                arr.put(o);
            }
            root.put("categories", arr);
            return root.toString(2);
        } catch (JSONException e) {
            throw new IllegalStateException("Failed to serialize categories", e);
        }
    }

    public List<Category> fromJson(String json, String relativePath) throws MalformedRecordException {
        final JSONObject root;
        try {
            root = new JSONObject(json);
        } catch (JSONException e) {
            throw new MalformedRecordException(
                    MalformedRecordException.Kind.MALFORMED_JSON, relativePath,
                    "Not valid JSON: " + e.getMessage(), e);
        }

        int schemaVersion = root.optInt("schemaVersion", -1);
        if (schemaVersion < 1) {
            throw new MalformedRecordException(
                    MalformedRecordException.Kind.SCHEMA_INVALID, relativePath,
                    "Missing or invalid schemaVersion");
        }
        if (schemaVersion > Category.SCHEMA_VERSION) {
            throw new MalformedRecordException(
                    MalformedRecordException.Kind.NEWER_SCHEMA, relativePath,
                    "categories.json schemaVersion " + schemaVersion + " is newer than supported");
        }

        JSONArray arr = root.optJSONArray("categories");
        if (arr == null) {
            throw new MalformedRecordException(
                    MalformedRecordException.Kind.SCHEMA_INVALID, relativePath,
                    "Missing 'categories' array");
        }

        List<Category> out = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) {
                throw new MalformedRecordException(
                        MalformedRecordException.Kind.SCHEMA_INVALID, relativePath,
                        "categories[" + i + "] is not an object");
            }
            String id = o.optString("id", "");
            String name = o.optString("name", "");
            if (id.isEmpty() || name.isEmpty()) {
                throw new MalformedRecordException(
                        MalformedRecordException.Kind.SCHEMA_INVALID, relativePath,
                        "categories[" + i + "] missing id/name");
            }
            String createdAt = parseTs(o, "createdAt", relativePath, i);
            String updatedAt = parseTs(o, "updatedAt", relativePath, i);
            int sortOrder = o.optInt("sortOrder", i);
            boolean system = o.optBoolean("system", false);
            out.add(new Category(id, name, createdAt, updatedAt, sortOrder, system));
        }
        return out;
    }

    private static String parseTs(JSONObject o, String key, String relativePath, int idx)
            throws MalformedRecordException {
        String v = o.optString(key, null);
        if (v == null || v.isEmpty()) {
            throw new MalformedRecordException(
                    MalformedRecordException.Kind.SCHEMA_INVALID, relativePath,
                    "categories[" + idx + "] missing '" + key + "'");
        }
        try {
            Instant.parse(v);
            return v;
        } catch (Exception e) {
            throw new MalformedRecordException(
                    MalformedRecordException.Kind.SCHEMA_INVALID, relativePath,
                    "categories[" + idx + "]." + key + " not ISO-8601: " + v, e);
        }
    }
}
