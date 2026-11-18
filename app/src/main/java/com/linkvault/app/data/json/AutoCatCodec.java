package com.linkvault.app.data.json;

import com.linkvault.app.domain.model.AutoRule;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Codec for user/autorules.json (schema v1). Unknown keys ignored on read;
 * a NEWER schemaVersion is rejected so a future file is never clobbered.
 */
public final class AutoCatCodec {

    public static final int SCHEMA_VERSION = 1;
    static final int MAX_RULES = 200;

    public String toJson(List<AutoRule> rules) {
        try {
            JSONObject o = new JSONObject();
            o.put("schemaVersion", SCHEMA_VERSION);
            o.put("type", "autorules");
            o.put("updatedAt", Instant.now().toString());
            JSONArray arr = new JSONArray();
            for (AutoRule r : rules) {
                JSONObject rule = new JSONObject();
                rule.put("domain", r.domain);
                rule.put("categoryId", r.categoryId);
                arr.put(rule);
            }
            o.put("rules", arr);
            return o.toString(2);
        } catch (JSONException e) {
            throw new IllegalStateException("Failed to serialize autorules", e);
        }
    }

    public List<AutoRule> fromJson(String json, String relativePath)
            throws MalformedRecordException {
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
        if (schemaVersion > SCHEMA_VERSION) {
            throw new MalformedRecordException(
                    MalformedRecordException.Kind.NEWER_SCHEMA, relativePath,
                    "autorules schemaVersion " + schemaVersion + " is newer than supported "
                            + SCHEMA_VERSION);
        }

        JSONArray arr = o.optJSONArray("rules");
        List<AutoRule> rules = new ArrayList<>();
        if (arr == null) return rules;
        if (arr.length() > MAX_RULES) {
            throw new MalformedRecordException(
                    MalformedRecordException.Kind.SCHEMA_INVALID, relativePath,
                    "More than " + MAX_RULES + " rules");
        }
        for (int i = 0; i < arr.length(); i++) {
            JSONObject rule = arr.optJSONObject(i);
            if (rule == null) continue;
            String domain = rule.optString("domain", "").trim().toLowerCase(Locale.ROOT);
            String categoryId = rule.optString("categoryId", "").trim();
            if (domain.isEmpty() || categoryId.isEmpty()) continue;
            boolean dup = false;
            for (AutoRule existing : rules) {
                if (existing.domain.equals(domain)) {
                    dup = true;
                    break;
                }
            }
            if (!dup) rules.add(new AutoRule(domain, categoryId));
        }
        return rules;
    }
}
