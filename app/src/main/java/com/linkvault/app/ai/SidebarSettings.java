package com.linkvault.app.ai;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import org.json.JSONObject;

/**
 * AI Sidebar preferences (spec §10 settings + §3 pill position + §8 result
 * management). One app-private prefs file — the single source of truth read
 * by both the Settings card and the overlay panel.
 *
 * Completion indicator is the fixed "Small dot" (spec §10 lists exactly one
 * mode — stored as a value row, not a toggle).
 */
public class SidebarSettings {

    private static final String PREFS = "ai_sidebar_prefs";

    private static final String K_ENABLED = "enabled";
    private static final String K_AUTO_COLLAPSE = "auto_collapse_copy";
    private static final String K_CLIP_AUTOFILL = "clipboard_autofill";
    private static final String K_BACKGROUND = "background_processing";
    private static final String K_HAPTIC = "haptic_feedback";
    private static final String K_INDICATOR = "completion_indicator";
    private static final String K_PILL_EDGE = "pill_edge";
    private static final String K_PILL_Y = "pill_y_frac";
    private static final String K_UNVIEWED = "result_unviewed";
    private static final String K_CURRENT = "current_result";
    private static final String K_PENDING = "pending_request";

    public static final String EDGE_LEFT = "left";
    public static final String EDGE_RIGHT = "right";
    public static final String INDICATOR_DOT = "dot";

    /** Latest completed result (spec §8) — shown until a newer one arrives. */
    public static final class Result {
        public final String input;
        public final String output;
        public final String tone;      // WritingTone.label
        public final String action;    // WritingAction.name()
        public final String custom;
        public final long timestamp;

        public Result(String input, String output, String tone, String action,
                      String custom, long timestamp) {
            this.input = input;
            this.output = output;
            this.tone = tone;
            this.action = action;
            this.custom = custom;
            this.timestamp = timestamp;
        }

        JSONObject toJson() {
            try {
                JSONObject o = new JSONObject();
                o.put("input", input == null ? "" : input);
                o.put("output", output == null ? "" : output);
                o.put("tone", tone == null ? WritingTone.DEFAULT.label : tone);
                o.put("action", action == null ? WritingAction.REWRITE.name() : action);
                o.put("custom", custom == null ? "" : custom);
                o.put("ts", timestamp);
                return o;
            } catch (Exception e) {
                return new JSONObject();
            }
        }

        @Nullable
        static Result fromJson(String raw) {
            if (raw == null || raw.isEmpty()) return null;
            try {
                JSONObject o = new JSONObject(raw);
                String output = o.optString("output", "");
                if (output.isEmpty()) return null;
                return new Result(o.optString("input", ""), output,
                        o.optString("tone", WritingTone.DEFAULT.label),
                        o.optString("action", WritingAction.REWRITE.name()),
                        o.optString("custom", ""),
                        o.optLong("ts", 0L));
            } catch (Exception e) {
                return null;
            }
        }
    }

    /** Request in flight when the process died (spec §7 — handled on start). */
    public static final class PendingRequest {
        public final String input;
        public final String tone;
        public final String action;
        public final String custom;
        public final long timestamp;

        public PendingRequest(String input, String tone, String action,
                              String custom, long timestamp) {
            this.input = input;
            this.tone = tone;
            this.action = action;
            this.custom = custom;
            this.timestamp = timestamp;
        }

        String toJson() {
            try {
                JSONObject o = new JSONObject();
                o.put("input", input == null ? "" : input);
                o.put("tone", tone == null ? WritingTone.DEFAULT.label : tone);
                o.put("action", action == null ? WritingAction.REWRITE.name() : action);
                o.put("custom", custom == null ? "" : custom);
                o.put("ts", timestamp);
                return o.toString();
            } catch (Exception e) {
                return "{}";
            }
        }

        @Nullable
        static PendingRequest fromJson(String raw) {
            if (raw == null || raw.isEmpty()) return null;
            try {
                JSONObject o = new JSONObject(raw);
                return new PendingRequest(o.optString("input", ""),
                        o.optString("tone", WritingTone.DEFAULT.label),
                        o.optString("action", WritingAction.REWRITE.name()),
                        o.optString("custom", ""),
                        o.optLong("ts", 0L));
            } catch (Exception e) {
                return null;
            }
        }
    }

    private final SharedPreferences prefs;

    public SidebarSettings(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ------------------------------------------------------- §10 toggles

    public boolean isEnabled() {
        return prefs.getBoolean(K_ENABLED, true);
    }

    public void setEnabled(boolean on) {
        prefs.edit().putBoolean(K_ENABLED, on).apply();
    }

    public boolean isAutoCollapseCopy() {
        return prefs.getBoolean(K_AUTO_COLLAPSE, true);
    }

    public void setAutoCollapseCopy(boolean on) {
        prefs.edit().putBoolean(K_AUTO_COLLAPSE, on).apply();
    }

    public boolean isClipboardAutofill() {
        return prefs.getBoolean(K_CLIP_AUTOFILL, true);
    }

    public void setClipboardAutofill(boolean on) {
        prefs.edit().putBoolean(K_CLIP_AUTOFILL, on).apply();
    }

    public boolean isBackgroundProcessing() {
        return prefs.getBoolean(K_BACKGROUND, true);
    }

    public void setBackgroundProcessing(boolean on) {
        prefs.edit().putBoolean(K_BACKGROUND, on).apply();
    }

    public boolean isHaptic() {
        return prefs.getBoolean(K_HAPTIC, true);
    }

    public void setHaptic(boolean on) {
        prefs.edit().putBoolean(K_HAPTIC, on).apply();
    }

    /** Fixed value row (spec §10: "Small dot"). */
    public String getCompletionIndicator() {
        return prefs.getString(K_INDICATOR, INDICATOR_DOT);
    }

    // -------------------------------------------------- §3 pill position

    public String getPillEdge() {
        return prefs.getString(K_PILL_EDGE, EDGE_RIGHT);
    }

    public float getPillYFraction() {
        return prefs.getFloat(K_PILL_Y, 0.32f);
    }

    public void setPillPosition(String edge, float yFraction) {
        prefs.edit()
                .putString(K_PILL_EDGE, edge)
                .putFloat(K_PILL_Y, Math.max(0f, Math.min(1f, yFraction)))
                .apply();
    }

    // ------------------------------------------- §7/§8 result management

    public boolean isUnviewed() {
        return prefs.getBoolean(K_UNVIEWED, false);
    }

    public void setUnviewed(boolean unviewed) {
        prefs.edit().putBoolean(K_UNVIEWED, unviewed).apply();
    }

    @Nullable
    public Result getCurrentResult() {
        return Result.fromJson(prefs.getString(K_CURRENT, null));
    }

    public void setCurrentResult(Result result) {
        prefs.edit().putString(K_CURRENT,
                result == null ? null : result.toJson().toString()).apply();
    }

    @Nullable
    public PendingRequest getPendingRequest() {
        return PendingRequest.fromJson(prefs.getString(K_PENDING, null));
    }

    public void setPendingRequest(PendingRequest pending) {
        prefs.edit().putString(K_PENDING,
                pending == null ? null : pending.toJson()).apply();
    }

    public void clearPendingRequest() {
        prefs.edit().remove(K_PENDING).apply();
    }
}
