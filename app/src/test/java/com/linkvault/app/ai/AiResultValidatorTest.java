package com.linkvault.app.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class AiResultValidatorTest {

    @Test
    public void parse_validJson_returnsResult() {
        AiResult r = AiResultValidator.parse(
                "{\"category\":\"development\",\"tags\":[\"java\",\"backend\"],"
                        + "\"summary\":\"A Spring Boot tutorial\",\"confidence\":0.87}");
        assertNotNull(r);
        assertEquals("development", r.category);
        assertEquals(List.of("java", "backend"), r.tags);
        assertEquals("A Spring Boot tutorial", r.summary);
        assertEquals(0.87, r.confidence, 0.001);
    }

    @Test
    public void parse_markdownFencesAndChatter_stillExtractsJson() {
        AiResult r = AiResultValidator.parse(
                "Sure!\n```json\n{\"category\":\"videos\",\"tags\":[\"youtube\"],"
                        + "\"summary\":\"A clip\",\"confidence\":0.9}\n```\nHope that helps.");
        assertNotNull(r);
        assertEquals("videos", r.category);
    }

    @Test
    public void parse_garbage_returnsNull() {
        assertNull(AiResultValidator.parse(null));
        assertNull(AiResultValidator.parse(""));
        assertNull(AiResultValidator.parse("I could not classify this link."));
        assertNull(AiResultValidator.parse("{\"category\":\"\",\"tags\":[],\"summary\":\"\"}"));
    }

    @Test
    public void sanitizeCategory_stripsAndCaps() {
        assertEquals("Development", AiResultValidator.sanitizeCategory("  Development  "));
        assertEquals("my weird category", AiResultValidator.sanitizeCategory("my <weird> category!"));
        assertNull(AiResultValidator.sanitizeCategory("!!!"));
        assertNull(AiResultValidator.sanitizeCategory(null));
        String longName = "abcdefghij".repeat(5); // 50 chars
        String capped = AiResultValidator.sanitizeCategory(longName);
        assertNotNull(capped);
        assertTrue(capped.length() <= AiContract.MAX_CATEGORY_CHARS);
    }

    @Test
    public void sanitizeTags_normalizesDedupesCapsAtFive() throws Exception {
        List<String> tags = AiResultValidator.sanitizeTags(
                new org.json.JSONArray("[\"Java\",\"java\",\"  Backend \",\"x\",\"y\",\"z\",\"w\"]"));
        assertEquals(5, tags.size());
        assertEquals("java", tags.get(0));
        assertEquals("backend", tags.get(1));
        for (String t : tags) assertEquals(t, t.toLowerCase(java.util.Locale.ROOT));
    }

    @Test
    public void sanitizeTags_commaStringFallback() {
        List<String> tags = AiResultValidator.sanitizeTags("news, politics, news");
        assertEquals(List.of("news", "politics"), tags);
    }

    @Test
    public void sanitizeSummary_collapsesAndTruncatesAtWordBoundary() {
        String longText = "word ".repeat(80).trim(); // 399 chars
        String out = AiResultValidator.sanitizeSummary(longText);
        assertNotNull(out);
        assertTrue(out.length() <= AiContract.MAX_SUMMARY_CHARS);
        assertNull(AiResultValidator.sanitizeSummary("   \n  "));
        assertEquals("one two three",
                AiResultValidator.sanitizeSummary("one \n\n two   three"));
    }

    @Test
    public void confidence_isClamped() {
        AiResult r = AiResultValidator.parse(
                "{\"category\":\"news\",\"tags\":[],\"summary\":\"s\",\"confidence\":7.5}");
        assertNotNull(r);
        assertEquals(1.0, r.confidence, 0.001);
        AiResult r2 = AiResultValidator.parse(
                "{\"category\":\"news\",\"tags\":[],\"summary\":\"s\",\"confidence\":-3}");
        assertNotNull(r2);
        assertEquals(0.0, r2.confidence, 0.001);
    }
}
