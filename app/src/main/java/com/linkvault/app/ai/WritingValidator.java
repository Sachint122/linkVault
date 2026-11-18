package com.linkvault.app.ai;

import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * Output validation + sanitization for writing assistant replies
 * (spec §5: detect malformed or empty responses).
 *
 * The model is instructed to reply with only the rewritten text, but small
 * models still leak fences/preambles — strip the known wrappers, then reject
 * anything empty. An unusable reply is returned as null so the UI can show a
 * clean error instead of garbage.
 */
public final class WritingValidator {

    private WritingValidator() {}

    /** Output sanity cap (keeps history/prefs small; generous for Expand). */
    public static final int MAX_OUTPUT_CHARS = 12_000;

    private static final String[] PREAMBLE_PREFIXES = {
            "here is", "here's", "here are", "certainly", "sure", "of course",
            "rewritten", "improved", "translated", "shortened", "expanded",
            "the rewritten text", "the improved version", "the translation"
    };

    @Nullable
    public static String sanitize(@Nullable String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;

        s = stripCodeFences(s);
        s = stripPreambleLines(s);

        // Collapse 3+ blank lines down to one blank line.
        s = s.replaceAll("\n{3,}", "\n\n").trim();
        if (s.isEmpty()) return null;

        if (s.length() > MAX_OUTPUT_CHARS) {
            s = s.substring(0, MAX_OUTPUT_CHARS);
        }
        return s;
    }

    /** Removes a leading and/or trailing ``` / ```text fence pair. */
    private static String stripCodeFences(String s) {
        String out = s;
        if (out.startsWith("```")) {
            int firstNl = out.indexOf('\n');
            out = firstNl >= 0 ? out.substring(firstNl + 1) : out.substring(3);
            int fence = out.lastIndexOf("```");
            if (fence >= 0) out = out.substring(0, fence);
        }
        return out.trim();
    }

    /** Drops obvious "Here is the rewrite:"-style opening lines (max 3). */
    private static String stripPreambleLines(String s) {
        String out = s;
        for (int i = 0; i < 3; i++) {
            int nl = out.indexOf('\n');
            String firstLine = nl >= 0 ? out.substring(0, nl) : out;
            String norm = firstLine.toLowerCase(Locale.ROOT).trim();
            if (norm.isEmpty()) break;
            boolean known = false;
            for (String p : PREAMBLE_PREFIXES) {
                if (norm.startsWith(p)) {
                    known = true;
                    break;
                }
            }
            if (!known) break;
            // Strip only clear preambles: a colon-ended label line
            // ("Here is the rewrite:") or a short greeting exclamation
            // ("Sure!", "Certainly!"). A plain sentence without a colon is
            // real content — keep it even when it starts with "rewritten…".
            boolean endsColon = norm.endsWith(":") || norm.endsWith("：");
            boolean shortGreeting = norm.length() <= 40
                    && (norm.endsWith("!") || norm.endsWith("?"));
            if (!endsColon && !shortGreeting) break;
            if (nl < 0) return ""; // whole reply was just a preamble → reject
            out = out.substring(nl + 1);
        }
        return out.trim();
    }
}
