package com.linkvault.app.data.share;

import androidx.annotation.Nullable;

import com.linkvault.app.domain.policy.UrlPolicy;

import java.util.List;

/**
 * Pure parser for ACTION_SEND payloads (FR-01). Extracts and validates URL
 * candidates from shared plain text — arbitrary text is never treated as a
 * saved URL; the user always reviews first.
 */
public final class ShareIntentParser {

    public static final class SharePayload {
        /** Valid extracted URL candidates (may be empty). */
        public final List<String> urls;
        /** Optional title hint from the source app. */
        @Nullable public final String title;
        /** Source package parsed from the referrer, if available. */
        @Nullable public final String sourcePackage;
        /** Full shared text (kept for review screen display/recovery). */
        @Nullable public final String sharedText;

        public SharePayload(List<String> urls, @Nullable String title,
                            @Nullable String sourcePackage, @Nullable String sharedText) {
            this.urls = urls;
            this.title = title;
            this.sourcePackage = sourcePackage;
            this.sharedText = sharedText;
        }

        public boolean hasUrl() {
            return !urls.isEmpty();
        }

        /** The single URL when exactly one candidate; null otherwise. */
        @Nullable
        public String singleUrl() {
            return urls.size() == 1 ? urls.get(0) : null;
        }
    }

    private ShareIntentParser() {
    }

    /**
     * @param sharedText  EXTRA_TEXT payload (may contain prose + URL(s))
     * @param titleHint   EXTRA_TITLE / EXTRA_SUBJECT if provided
     * @param referrer    EXTRA_REFERRER raw value (android-app://pkg or URL)
     */
    public static SharePayload parse(@Nullable String sharedText,
                                     @Nullable String titleHint,
                                     @Nullable String referrer) {
        List<String> urls = UrlPolicy.extractUrls(sharedText);
        String sourcePackage = extractPackage(referrer);

        String title = normalizeTitle(titleHint);
        if (title == null) {
            // First non-URL line as weak title hint (still reviewable).
            title = firstNonUrlLine(sharedText, urls);
        }
        return new SharePayload(urls, title, sourcePackage, sharedText);
    }

    /** Referrer forms: "android-app://com.foo.bar", plain package, or a URL. */
    @Nullable
    static String extractPackage(@Nullable String referrer) {
        if (referrer == null || referrer.trim().isEmpty()) return null;
        String r = referrer.trim();
        if (r.startsWith("android-app://")) {
            String pkg = r.substring("android-app://".length());
            int slash = pkg.indexOf('/');
            if (slash >= 0) pkg = pkg.substring(0, slash);
            return isValidPackageName(pkg) ? pkg : null;
        }
        if (isValidPackageName(r)) return r;
        return null;
    }

    private static boolean isValidPackageName(String s) {
        if (s == null || s.isEmpty() || !s.contains(".")) return false;
        for (char c : s.toCharArray()) {
            boolean ok = Character.isLetterOrDigit(c) || c == '.' || c == '_' || c == '$';
            if (!ok) return false;
        }
        return true;
    }

    @Nullable
    private static String normalizeTitle(@Nullable String title) {
        if (title == null) return null;
        String t = title.trim();
        if (t.isEmpty()) return null;
        return t.length() > 300 ? t.substring(0, 300) : t;
    }

    @Nullable
    private static String firstNonUrlLine(@Nullable String text, List<String> urls) {
        if (text == null) return null;
        for (String line : text.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            boolean isUrlLine = false;
            for (String u : urls) {
                if (trimmed.equals(u)) {
                    isUrlLine = true;
                    break;
                }
            }
            if (!isUrlLine && UrlPolicy.extractFirstUrl(trimmed) == null) {
                return normalizeTitle(trimmed);
            }
        }
        return null;
    }
}
