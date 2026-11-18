package com.linkvault.app.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RuleBasedAnalyzerTest {

    @Test
    public void youtube_detectedWithPlatformTags() {
        AiResult r = RuleBasedAnalyzer.analyze(
                "https://www.youtube.com/watch?v=abc123", "Cool Video - YouTube");
        assertEquals("Videos", r.category);
        assertTrue(r.tags.contains("youtube"));
        assertTrue(r.tags.contains("video"));
        assertNotNull(r.summary);
        assertTrue(r.summary.startsWith("YouTube video: Cool Video"));
        assertTrue(r.confidence >= 0.9);
    }

    @Test
    public void github_detected() {
        AiResult r = RuleBasedAnalyzer.analyze(
                "https://github.com/google/mediapipe", "google/mediapipe: README");
        assertEquals("Development", r.category);
        assertTrue(r.tags.contains("development"));
    }

    @Test
    public void subdomainAndWww_match() {
        AiResult r = RuleBasedAnalyzer.analyze("https://m.wikipedia.org/wiki/Java", null);
        assertEquals("Reference", r.category);
        assertNotNull(r.summary);
        assertTrue(r.summary.contains("Wikipedia"));
    }

    @Test
    public void unknownHost_weakBaselineNoCategory() {
        AiResult r = RuleBasedAnalyzer.analyze("https://some-random-site.example/page", "My Page");
        assertNull(r.category);
        assertEquals(0.3, r.confidence, 0.001);
        assertNotNull(r.summary);
        assertTrue(r.summary.contains("My Page"));
    }

    @Test
    public void summary_neverExceedsCap() {
        StringBuilder longTitle = new StringBuilder();
        for (int i = 0; i < 60; i++) longTitle.append("verylongword ");
        AiResult r = RuleBasedAnalyzer.analyze(
                "https://example.com/a", longTitle.toString().trim());
        assertNotNull(r.summary);
        assertTrue(r.summary.length() <= AiContract.MAX_SUMMARY_CHARS);
    }

    @Test
    public void malformedUrl_doesNotCrash() {
        AiResult r = RuleBasedAnalyzer.analyze("not a url", null);
        assertNotNull(r);
        assertFalse(r.hasAnyValue() && r.category == null && r.summary == null);
        assertNotNull(r.summary);
    }
}
