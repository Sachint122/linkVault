package com.linkvault.app.domain.policy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PathSanitizerTest {

    @Test
    public void sanitize_stripsUnsafeCharacters() {
        assertEquals("my_file_name.json", PathSanitizer.sanitizeFileName("my file/name.json"));
    }

    @Test
    public void sanitize_rejectsTraversalNames() {
        assertEquals("", PathSanitizer.sanitizeFileName(".."));
        assertEquals("", PathSanitizer.sanitizeFileName("."));
        assertEquals("", PathSanitizer.sanitizeFileName("   "));
        assertEquals("", PathSanitizer.sanitizeFileName(null));
    }

    @Test
    public void sanitize_collapsesRunsAndTrimsEdges() {
        assertEquals("a_b_c", PathSanitizer.sanitizeFileName("a___b__c"));
        assertEquals("name", PathSanitizer.sanitizeFileName("..name.."));
    }

    @Test
    public void sanitize_capsLength() {
        String longName = "x".repeat(200) + ".json";
        String out = PathSanitizer.sanitizeFileName(longName);
        assertTrue(out.length() <= PathSanitizer.MAX_FILE_NAME_LENGTH);
    }

    @Test
    public void safePath_acceptsNormalRelativePaths() {
        assertTrue(PathSanitizer.isSafeRelativePath("user/categories/cat_abc"));
        assertTrue(PathSanitizer.isSafeRelativePath("user/files/report_1.pdf"));
    }

    @Test
    public void safePath_rejectsTraversalAndAbsolute() {
        assertFalse(PathSanitizer.isSafeRelativePath("../etc/passwd"));
        assertFalse(PathSanitizer.isSafeRelativePath("user/../../etc"));
        assertFalse(PathSanitizer.isSafeRelativePath("/absolute/path"));
        assertFalse(PathSanitizer.isSafeRelativePath("C:\\windows"));
        assertFalse(PathSanitizer.isSafeRelativePath("user\\..\\..\\x"));
        assertFalse(PathSanitizer.isSafeRelativePath(""));
        assertFalse(PathSanitizer.isSafeRelativePath(null));
    }

    @Test
    public void insideRoot_checksContainment() {
        assertTrue(PathSanitizer.isInsideRoot("user", "user/categories/x.json"));
        assertTrue(PathSanitizer.isInsideRoot("user", "user"));
        assertFalse(PathSanitizer.isInsideRoot("user", "userdata/x.json"));
        assertFalse(PathSanitizer.isInsideRoot("user", "other/x.json"));
        assertFalse(PathSanitizer.isInsideRoot("user", "user/../evil.json"));
    }
}
