package com.linkvault.app.domain.policy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class UrlPolicyTest {

    @Test
    public void isValid_acceptsHttpAndHttps() {
        assertTrue(UrlPolicy.isValid("https://example.com"));
        assertTrue(UrlPolicy.isValid("http://example.com/path?q=1"));
        assertTrue(UrlPolicy.isValid("https://sub.example.co.uk/a/b"));
    }

    @Test
    public void isValid_rejectsBadSchemesAndShapes() {
        assertFalse(UrlPolicy.isValid("javascript:alert(1)"));
        assertFalse(UrlPolicy.isValid("file:///etc/passwd"));
        assertFalse(UrlPolicy.isValid("ftp://example.com"));
        assertFalse(UrlPolicy.isValid("notaurl"));
        assertFalse(UrlPolicy.isValid("https://"));
        assertFalse(UrlPolicy.isValid(""));
        assertFalse(UrlPolicy.isValid(null));
    }

    @Test
    public void normalize_stripsTrackingParams() {
        assertEquals("https://example.com/a",
                UrlPolicy.normalizeForCompare("https://example.com/a?utm_source=x&utm_medium=y"));
        assertEquals("https://example.com/a",
                UrlPolicy.normalizeForCompare("https://example.com/a?fbclid=abc"));
        assertEquals("https://example.com/a?keep=1",
                UrlPolicy.normalizeForCompare("https://example.com/a?keep=1&utm_source=x"));
    }

    @Test
    public void normalize_lowercasesSchemeAndHost() {
        assertEquals("https://example.com/x",
                UrlPolicy.normalizeForCompare("HTTPS://EXAMPLE.COM/x"));
    }

    @Test
    public void normalize_stripsTrailingSlashAndFragment() {
        assertEquals("https://example.com/x",
                UrlPolicy.normalizeForCompare("https://example.com/x/"));
        assertEquals("https://example.com/x",
                UrlPolicy.normalizeForCompare("https://example.com/x#section"));
        assertEquals("https://example.com",
                UrlPolicy.normalizeForCompare("https://example.com/"));
    }

    @Test
    public void duplicates_sameResourceDifferentTracking_equal() {
        assertTrue(UrlPolicy.areDuplicates(
                "https://youtube.com/watch?v=abc&utm_source=share",
                "https://www.youtube.com/watch?v=abc"));
    }

    @Test
    public void duplicates_differentContent_notEqual() {
        assertFalse(UrlPolicy.areDuplicates(
                "https://example.com/a", "https://example.com/b"));
        assertFalse(UrlPolicy.areDuplicates(
                "https://example.com/a?v=1", "https://example.com/a?v=2"));
        assertFalse(UrlPolicy.areDuplicates(
                "https://example.com/a", "https://other.com/a"));
    }

    @Test
    public void duplicates_invalidInputs_neverEqual() {
        assertFalse(UrlPolicy.areDuplicates("javascript:x", "javascript:x"));
        assertFalse(UrlPolicy.areDuplicates(null, null));
        assertEquals("", UrlPolicy.normalizeForCompare("garbage"));
    }

    @Test
    public void extract_findsUrlInsideProse() {
        List<String> urls = UrlPolicy.extractUrls(
                "Check this out https://example.com/post?id=5 ok");
        assertEquals(1, urls.size());
        assertEquals("https://example.com/post?id=5", urls.get(0));
    }

    @Test
    public void extract_multipleUrls_returnsAll() {
        List<String> urls = UrlPolicy.extractUrls(
                "first https://a.com/x and second https://b.com/y done");
        assertEquals(2, urls.size());
        assertEquals("https://a.com/x", urls.get(0));
        assertEquals("https://b.com/y", urls.get(1));
    }

    @Test
    public void extract_trimsTrailingPunctuation() {
        List<String> urls = UrlPolicy.extractUrls("see https://example.com/p.");
        assertEquals("https://example.com/p", urls.get(0));
        List<String> paren = UrlPolicy.extractUrls("(https://example.com/p)");
        assertEquals("https://example.com/p", paren.get(0));
    }

    @Test
    public void extract_plainTextWithoutUrl_returnsEmpty() {
        assertTrue(UrlPolicy.extractUrls("just some random text").isEmpty());
        assertTrue(UrlPolicy.extractUrls(null).isEmpty());
        assertNull(UrlPolicy.extractFirstUrl("no links here"));
    }

    @Test
    public void extract_invalidSchemesDropped() {
        assertTrue(UrlPolicy.extractUrls("javascript:alert(1) file:///x").isEmpty());
    }
}
