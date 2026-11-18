package com.linkvault.app.ai;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Parses + sanitizes the raw LLM reply into an {@link AiResult}.
 * Contract: one JSON object {category, tags, summary, confidence};
 * everything else is stripped/normalized. Returns null when the reply is
 * unusable — callers then fall back to the rule-based result (never a retry
 * loop: a malformed reply for the same prompt is treated as deterministic).
 */
public final class AiResultValidator {

    private AiResultValidator() {}

    @Nullable
    public static AiResult parse(@Nullable String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start < 0 || end <= start) return null;
        JSONObject o;
        try {
            o = new JSONObject(s.substring(start, end + 1));
        } catch (Exception e) {
            return null;
        }

        String category = sanitizeCategory(optString(o, "category"));
        String summary = sanitizeSummary(optString(o, "summary"));
        List<String> tags = sanitizeTags(o.opt("tags"));

        double confidence = 0.5;
        try {
            double c = o.optDouble("confidence", 0.5);
            if (!Double.isNaN(c)) confidence = Math.max(0, Math.min(1, c));
        } catch (Exception ignored) {
            // keep default
        }

        AiResult result = new AiResult(category, tags, summary, confidence);
        return result.hasAnyValue() ? result : null;
    }

    /** Single short word-ish category, 1..24 chars; null when unusable. */
    @Nullable
    public static String sanitizeCategory(@Nullable String raw) {
        if (raw == null) return null;
        String c = raw.trim().replaceAll("[^A-Za-z0-9 _-]", "");
        c = c.replaceAll("\\s+", " ").trim();
        if (c.isEmpty()) return null;
        if (c.length() > AiContract.MAX_CATEGORY_CHARS) {
            c = c.substring(0, AiContract.MAX_CATEGORY_CHARS).trim();
        }
        return c.isEmpty() ? null : c;
    }

    /** Normalized lowercase tags, max {@link AiContract#MAX_AI_TAGS}, deduped. */
    public static List<String> sanitizeTags(@Nullable Object raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) return out;
        List<String> candidates = new ArrayList<>();
        if (raw instanceof JSONArray arr) {
            for (int i = 0; i < arr.length(); i++) {
                String t = arr.optString(i, null);
                if (t != null) candidates.add(t);
            }
        } else if (raw instanceof String str) {
            for (String t : str.split(",")) candidates.add(t);
        } else {
            return out;
        }
        for (String t : candidates) {
            if (out.size() >= AiContract.MAX_AI_TAGS) break;
            String n = LinkRecordTags.normalize(t);
            if (n.isEmpty()) continue;
            boolean dup = false;
            for (String e : out) {
                if (e.equalsIgnoreCase(n)) {
                    dup = true;
                    break;
                }
            }
            if (!dup) out.add(n);
        }
        return out;
    }

    /** One line, <= 200 chars, word-boundary cut; null when empty. */
    @Nullable
    public static String sanitizeSummary(@Nullable String raw) {
        if (raw == null) return null;
        String s = raw.replaceAll("\\s+", " ").trim();
        if (s.isEmpty()) return null;
        if (s.length() > AiContract.MAX_SUMMARY_CHARS) {
            int cut = s.lastIndexOf(' ', AiContract.MAX_SUMMARY_CHARS - 1);
            if (cut < AiContract.MAX_SUMMARY_CHARS / 2) {
                cut = AiContract.MAX_SUMMARY_CHARS - 1;
            }
            s = s.substring(0, cut).trim();
        }
        return s.isEmpty() ? null : s;
    }

    @Nullable
    private static String optString(JSONObject o, String key) {
        if (!o.has(key) || o.isNull(key)) return null;
        String v = o.optString(key, null);
        return (v == null || v.trim().isEmpty()) ? null : v;
    }

    /** Tiny indirection so the validator stays org.json-only (D-05). */
    private static final class LinkRecordTags {
        static String normalize(String tag) {
            String t = tag.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
            if (t.length() > 30) t = t.substring(0, 30);
            return t;
        }
    }
}
