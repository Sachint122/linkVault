package com.linkvault.app.domain.policy;

/**
 * Pure path/filename safety rules. Nothing user-provided ever becomes a
 * path component without passing through here (DATA_SCHEMA.md §8).
 */
public final class PathSanitizer {

    public static final int MAX_FILE_NAME_LENGTH = 60;
    private static final String SAFE_CHARS = "[^a-zA-Z0-9._-]";

    private PathSanitizer() {
    }

    /**
     * Whitelists [a-zA-Z0-9._-], collapses runs, strips leading dots/dashes.
     * Returns "" when nothing safe remains. Never returns "." or "..".
     */
    public static String sanitizeFileName(String name) {
        if (name == null) return "";
        String s = name.trim().replaceAll(SAFE_CHARS, "_");
        s = s.replaceAll("_{2,}", "_");
        s = s.replaceAll("^[._-]+", "");
        s = s.replaceAll("[._-]+$", ""); // avoid trailing extension-like junk? keep simple: strip trailing dots
        if (s.isEmpty() || s.equals(".") || s.equals("..")) return "";
        if (s.length() > MAX_FILE_NAME_LENGTH) {
            s = s.substring(0, MAX_FILE_NAME_LENGTH);
            s = s.replaceAll("[._-]+$", "");
        }
        return s;
    }

    /**
     * Validates a storage-root-relative path: no leading slash, no drive
     * letters, no "..", no backslash traversal, every segment non-empty.
     */
    public static boolean isSafeRelativePath(String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) return false;
        if (relativePath.contains("\\")) return false;
        if (relativePath.startsWith("/")) return false;
        if (relativePath.contains("..")) return false;
        if (relativePath.contains(":")) return false;
        String[] segments = relativePath.split("/");
        for (String seg : segments) {
            if (seg.isEmpty() || seg.equals(".") || seg.equals("..")) return false;
        }
        return true;
    }

    /**
     * True when {@code candidate} resolves inside {@code root} (both
     * root-relative). Guards every read/write of stored content.
     */
    public static boolean isInsideRoot(String rootRelative, String candidate) {
        if (!isSafeRelativePath(rootRelative) && !rootRelative.isEmpty()) return false;
        if (!isSafeRelativePath(candidate)) return false;
        String root = rootRelative.isEmpty() || rootRelative.equals(".") ? "" : rootRelative + "/";
        return candidate.equals(rootRelative) || candidate.startsWith(root);
    }
}
