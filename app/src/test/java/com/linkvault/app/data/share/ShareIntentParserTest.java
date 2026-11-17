package com.linkvault.app.data.share;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ShareIntentParserTest {

    @Test
    public void bareUrl_isSingleCandidate() {
        ShareIntentParser.SharePayload p = ShareIntentParser.parse(
                "https://example.com/article", null, null);
        assertTrue(p.hasUrl());
        assertEquals("https://example.com/article", p.singleUrl());
    }

    @Test
    public void proseWithUrl_extractsUrlAndLineAsTitle() {
        ShareIntentParser.SharePayload p = ShareIntentParser.parse(
                "Ye dekho mast article\nhttps://example.com/post", null, null);
        assertTrue(p.hasUrl());
        assertEquals("https://example.com/post", p.singleUrl());
        assertEquals("Ye dekho mast article", p.title);
    }

    @Test
    public void explicitTitle_wins() {
        ShareIntentParser.SharePayload p = ShareIntentParser.parse(
                "https://example.com", "  My Title  ", null);
        assertEquals("My Title", p.title);
    }

    @Test
    public void noUrl_inPayload() {
        ShareIntentParser.SharePayload p = ShareIntentParser.parse(
                "koi link nahi hai yahan", null, null);
        assertFalse(p.hasUrl());
        assertNull(p.singleUrl());
        assertEquals("koi link nahi hai yahan", p.title);
    }

    @Test
    public void nullText_safe() {
        ShareIntentParser.SharePayload p = ShareIntentParser.parse(null, null, null);
        assertFalse(p.hasUrl());
        assertNull(p.title);
        assertNull(p.sharedText);
    }

    @Test
    public void multipleUrls_twoCandidates_singleUrlNull() {
        ShareIntentParser.SharePayload p = ShareIntentParser.parse(
                "https://a.com/x https://b.com/y", null, null);
        assertEquals(2, p.urls.size());
        assertNull(p.singleUrl());
    }

    @Test
    public void invalidUrl_notSaved() {
        ShareIntentParser.SharePayload p = ShareIntentParser.parse(
                "javascript:alert(1)", null, null);
        assertFalse(p.hasUrl());
    }

    @Test
    public void referrer_androidAppParsed() {
        ShareIntentParser.SharePayload p = ShareIntentParser.parse(
                "https://example.com", null,
                "android-app://com.instagram.android");
        assertEquals("com.instagram.android", p.sourcePackage);
    }

    @Test
    public void referrer_plainPackageParsed() {
        ShareIntentParser.SharePayload p = ShareIntentParser.parse(
                "https://example.com", null, "com.android.chrome");
        assertEquals("com.android.chrome", p.sourcePackage);
    }

    @Test
    public void referrer_urlOrGarbage_ignored() {
        ShareIntentParser.SharePayload p = ShareIntentParser.parse(
                "https://example.com", null, "https://example.com/page");
        assertNull(p.sourcePackage);
        ShareIntentParser.SharePayload p2 = ShareIntentParser.parse(
                "https://example.com", null, "not a package");
        assertNull(p2.sourcePackage);
        ShareIntentParser.SharePayload p3 = ShareIntentParser.parse(
                "https://example.com", null, null);
        assertNull(p3.sourcePackage);
    }

    @Test
    public void longTitle_capped() {
        String longTitle = "T".repeat(500);
        ShareIntentParser.SharePayload p = ShareIntentParser.parse(
                "https://example.com", longTitle, null);
        assertEquals(300, p.title.length());
    }
}
