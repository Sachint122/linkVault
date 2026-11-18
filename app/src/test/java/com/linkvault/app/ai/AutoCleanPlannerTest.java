package com.linkvault.app.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.linkvault.app.domain.model.Category;

import org.junit.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public class AutoCleanPlannerTest {

    private static final Instant NOW = Instant.parse("2026-11-18T00:00:00Z");
    private static final String OLD = "2026-09-01T00:00:00Z";   // ~79 days ago
    private static final String NEW = "2026-11-10T00:00:00Z";   // 8 days ago

    private static Category cat(String id, String created, String updated, String parent) {
        return new Category(id, id, created, updated, 1, false, parent);
    }

    // ------------------------------------------------- staleEmptyCategories

    @Test
    public void oldEmptyLeaf_isRemoved() {
        List<String> out = AutoCleanPlanner.staleEmptyCategories(
                List.of(cat("cat_a", OLD, OLD, null)),
                Set.of(), Set.of(), NOW, AutoCleanPlanner.GRACE_DAYS);
        assertEquals(List.of("cat_a"), out);
    }

    @Test
    public void recentlyTouched_isKept() {
        List<String> out = AutoCleanPlanner.staleEmptyCategories(
                List.of(cat("cat_a", NEW, NEW, null)),
                Set.of(), Set.of(), NOW, AutoCleanPlanner.GRACE_DAYS);
        assertTrue(out.isEmpty());
    }

    @Test
    public void categoryWithRecords_isKept() {
        List<String> out = AutoCleanPlanner.staleEmptyCategories(
                List.of(cat("cat_a", OLD, OLD, null)),
                Set.of("cat_a"), Set.of(), NOW, AutoCleanPlanner.GRACE_DAYS);
        assertTrue(out.isEmpty());
    }

    @Test
    public void autoCatReferenced_isKept() {
        List<String> out = AutoCleanPlanner.staleEmptyCategories(
                List.of(cat("cat_a", OLD, OLD, null)),
                Set.of(), Set.of("cat_a"), NOW, AutoCleanPlanner.GRACE_DAYS);
        assertTrue(out.isEmpty());
    }

    @Test
    public void parentOfChild_isKept() {
        List<String> out = AutoCleanPlanner.staleEmptyCategories(
                List.of(cat("cat_parent", OLD, OLD, null),
                        cat("cat_child", OLD, OLD, "cat_parent")),
                Set.of(), Set.of(), NOW, AutoCleanPlanner.GRACE_DAYS);
        assertEquals(List.of("cat_child"), out);
    }

    @Test
    public void ungroupedSystem_isNeverRemoved() {
        List<String> out = AutoCleanPlanner.staleEmptyCategories(
                List.of(Category.ungrouped(OLD)),
                Set.of(), Set.of(), NOW, AutoCleanPlanner.GRACE_DAYS);
        assertTrue(out.isEmpty());
    }

    @Test
    public void unparsableDates_areKept() {
        Category c = new Category("cat_a", "a", "not-a-date", null, 1, false);
        List<String> out = AutoCleanPlanner.staleEmptyCategories(
                List.of(c), Set.of(), Set.of(), NOW, AutoCleanPlanner.GRACE_DAYS);
        assertTrue(out.isEmpty());
    }

    // ---------------------------------------------------------- pruneTags

    @Test
    public void tagEqualToFolderName_isDropped() {
        List<String> out = AutoCleanPlanner.pruneTags(
                List.of("Tech", "ai", "tools"), "Tech");
        assertEquals(List.of("ai", "tools"), out);
    }

    @Test
    public void pluralPair_keepsFirst() {
        List<String> out = AutoCleanPlanner.pruneTags(
                List.of("video", "videos", "tech"), null);
        assertEquals(List.of("video", "tech"), out);
    }

    @Test
    public void shortWords_likeNewsAreNotTreatedAsPlurals() {
        List<String> out = AutoCleanPlanner.pruneTags(
                List.of("new", "news"), null);
        assertNull(out); // distinct words — nothing changes
    }

    @Test
    public void noChange_returnsNull() {
        assertNull(AutoCleanPlanner.pruneTags(List.of("ai", "tools"), "Tech"));
        assertNull(AutoCleanPlanner.pruneTags(null, "Tech"));
        assertNull(AutoCleanPlanner.pruneTags(List.of(), "Tech"));
    }

    @Test
    public void blanks_areRemoved() {
        List<String> out = AutoCleanPlanner.pruneTags(
                List.of("ai", "  ", "tools"), null);
        assertEquals(List.of("ai", "tools"), out);
    }
}
