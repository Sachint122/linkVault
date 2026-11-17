package com.linkvault.app.domain.policy;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * URL validation, normalization for duplicate detection (D-07), and safe
 * extraction from shared plain text (FR-01).
 */
public final class UrlPolicy {

    /** Tracking params stripped for duplicate comparison only (D-07). */
    private static final Set<String> STRIP_PARAMS = new HashSet<>(Arrays.asList(
            "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
            "utm_id", "fbclid", "gclid", "igshid", "ref", "ref_src", "mc_cid",
            "mc_eid", "si"));

    /** URL-looking tokens: scheme://… up to whitespace. */
    private static final String URL_REGEX = "https?://[^\\s<>\"']+";

    private UrlPolicy() {
    }

    /** True for absolute http/https URLs with a host. */
    public static boolean isValid(String url) {
        if (url == null || url.trim().isEmpty()) return false;
        try {
            URI uri = new URI(url.trim());
            String scheme = uri.getScheme();
            return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    && uri.getHost() != null && !uri.getHost().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Normalized comparison form: lowercased scheme/host, leading "www." and
     * default port stripped, no fragment, tracking params removed, trailing
     * slash trimmed. Original URL is never modified for storage — this is
     * comparison only. Returns "" for invalid input.
     */
    public static String normalizeForCompare(String url) {
        if (!isValid(url)) return "";
        try {
            URI u = new URI(url.trim());
            String scheme = u.getScheme().toLowerCase(Locale.ROOT);
            String host = u.getHost().toLowerCase(Locale.ROOT);
            if (host.startsWith("www.") && host.substring(4).contains(".")) {
                host = host.substring(4);
            }
            int port = u.getPort();
            String path = u.getPath() == null ? "" : u.getPath();
            if (path.endsWith("/") && path.length() > 1) {
                path = path.substring(0, path.length() - 1);
            }
            if (path.equals("/")) path = "";

            String query = stripTrackingParams(u.getRawQuery());

            StringBuilder sb = new StringBuilder();
            sb.append(scheme).append("://").append(host);
            if (port != -1 && !isDefaultPort(scheme, port)) sb.append(':').append(port);
            sb.append(path);
            if (query != null && !query.isEmpty()) sb.append('?').append(query);
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /** Two URLs are duplicates when both normalize to the same non-empty form. */
    public static boolean areDuplicates(String a, String b) {
        String na = normalizeForCompare(a);
        String nb = normalizeForCompare(b);
        return !na.isEmpty() && na.equals(nb);
    }

    /**
     * Extracts valid http/https URL candidates from arbitrary shared text.
     * Invalid/malformed/javascript:/file: candidates are dropped — arbitrary
     * text is never treated as a URL (FR-01).
     */
    public static List<String> extractUrls(String text) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isEmpty()) return out;
        java.util.regex.Matcher m =
                java.util.regex.Pattern.compile(URL_REGEX).matcher(text);
        while (m.find()) {
            String candidate = trimTrailingPunctuation(m.group());
            if (isValid(candidate) && !out.contains(candidate)) {
                out.add(candidate);
            }
        }
        return out;
    }

    /** Single-URL convenience: first candidate or null. */
    public static String extractFirstUrl(String text) {
        List<String> urls = extractUrls(text);
        return urls.isEmpty() ? null : urls.get(0);
    }

    private static String trimTrailingPunctuation(String s) {
        String out = s;
        while (!out.isEmpty()) {
            char c = out.charAt(out.length() - 1);
            if (c == '.' || c == ',' || c == ';' || c == ':' || c == '!'
                    || c == ')' || c == ']' || c == '\'' || c == '"' || c == '>') {
                out = out.substring(0, out.length() - 1);
            } else {
                break;
            }
        }
        return out;
    }

    private static String stripTrackingParams(String rawQuery) {
        if (rawQuery == null || rawQuery.isEmpty()) return null;
        StringBuilder kept = new StringBuilder();
        for (String pair : rawQuery.split("&")) {
            if (pair.isEmpty()) continue;
            int eq = pair.indexOf('=');
            String key = (eq >= 0 ? pair.substring(0, eq) : pair).toLowerCase(Locale.ROOT);
            if (STRIP_PARAMS.contains(key)) continue;
            if (kept.length() > 0) kept.append('&');
            kept.append(pair);
        }
        return kept.toString();
    }

    private static boolean isDefaultPort(String scheme, int port) {
        return ("http".equals(scheme) && port == 80)
                || ("https".equals(scheme) && port == 443);
    }
}
