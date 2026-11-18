package com.linkvault.app.ai;

import androidx.annotation.Nullable;

import com.linkvault.app.domain.model.Category;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Pure decision logic for the auto-clean pass (owner: "AI khud app clean
 * kare — link delete na kare").
 *
 * Hard invariant: this planner never produces anything that deletes or
 * rewrites a link/file record's existence — only category metadata and
 * redundant tags on records.
 */
public final class AutoCleanPlanner {

    /** An untouched (created/updated) category stays for at least this long. */
    public static final int GRACE_DAYS = 30;

    private AutoCleanPlanner() {}

    /**
     * Categories that are safe to remove: non-system, holding no links or
     * files, no child categories, not targeted by an auto-categorize rule,
     * and untouched for at least {@code graceDays}.
     */
    public static List<String> staleEmptyCategories(
            List<Category> categories,
            Set<String> occupiedCategoryIds,
            Set<String> autoCatTargetIds,
            Instant now,
            int graceDays) {
        List<String> out = new ArrayList<>();
        if (categories == null) return out;
        for (Category c : categories) {
            if (c == null || c.isSystem()) continue;
            String id = c.getId();
            if (Category.UNGROUPED_ID.equals(id)) continue;
            if (occupiedCategoryIds != null && occupiedCategoryIds.contains(id)) continue;
            if (autoCatTargetIds != null && autoCatTargetIds.contains(id)) continue;
            if (hasChild(categories, id)) continue;
            Instant newest = newestTouch(c);
            if (newest == null) continue; // unparsable dates → keep (safe)
            if (newest.isAfter(now.minusSeconds(graceDays * 86_400L))) continue;
            out.add(id);
        }
        return out;
    }

    private static boolean hasChild(List<Category> categories, String parentId) {
        for (Category c : categories) {
            if (c != null && parentId.equals(c.getParentId())) return true;
        }
        return false;
    }

    @Nullable
    private static Instant newestTouch(Category c) {
        Instant created = parse(c.getCreatedAt());
        Instant updated = parse(c.getUpdatedAt());
        if (created == null) return updated;
        if (updated == null) return created;
        return created.isAfter(updated) ? created : updated;
    }

    @Nullable
    private static Instant parse(String iso) {
        if (iso == null || iso.isEmpty()) return null;
        try {
            return Instant.parse(iso);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * Drops redundant tags from one record's tag list:
     * <ul>
     *   <li>tag equal to the record's folder name (the folder chip already
     *       filters it — the tag adds nothing),</li>
     *   <li>near-duplicate number forms on the same record
     *       ("video"/"videos", "tool"/"tools"; "news"/"css" untouched),</li>
     *   <li>blank entries.</li>
     * </ul>
     * Keeps original casing of surviving tags. Returns {@code null} when
     * nothing changes (caller skips the write).
     */
    @Nullable
    public static List<String> pruneTags(List<String> tags, @Nullable String categoryName) {
        if (tags == null || tags.isEmpty()) return null;
        String catNorm = categoryName == null || categoryName.trim().isEmpty()
                ? null : categoryName.trim().toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        Set<String> stems = new HashSet<>();
        boolean changed = false;
        for (String raw : tags) {
            if (raw == null || raw.trim().isEmpty()) {
                changed = true;
                continue;
            }
            String t = raw.trim();
            String lower = t.toLowerCase(Locale.ROOT);
            if (lower.equals(catNorm)) {
                changed = true;
                continue;
            }
            if (!stems.add(stem(lower))) {
                changed = true;
                continue;
            }
            out.add(t);
        }
        return changed ? out : null;
    }

    /** Singular stem for plural-pair detection; conservative on short words. */
    private static String stem(String lower) {
        if (lower.length() > 4 && lower.endsWith("s") && !lower.endsWith("ss")) {
            return lower.substring(0, lower.length() - 1);
        }
        return lower;
    }
}
