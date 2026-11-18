package com.linkvault.app.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Output validation (spec §5: malformed/empty response detection). */
public class WritingValidatorTest {

    @Test
    public void null_and_empty_rejected() {
        assertNull(WritingValidator.sanitize(null));
        assertNull(WritingValidator.sanitize(""));
        assertNull(WritingValidator.sanitize("   \n  "));
    }

    @Test
    public void clean_output_passes_unchanged() {
        assertEquals("Hello world. This is fine.",
                WritingValidator.sanitize("Hello world. This is fine."));
    }

    @Test
    public void code_fences_stripped() {
        String raw = "```text\nRewritten sentence here.\n```";
        assertEquals("Rewritten sentence here.", WritingValidator.sanitize(raw));
        assertEquals("Bare line.", WritingValidator.sanitize("```\nBare line.\n```"));
    }

    @Test
    public void preamble_lines_stripped() {
        String raw = "Here is the rewritten version:\nThe actual rewritten text.";
        assertEquals("The actual rewritten text.", WritingValidator.sanitize(raw));
        String raw2 = "Sure! Here's the improved text:\nActual content.";
        assertEquals("Actual content.", WritingValidator.sanitize(raw2));
    }

    @Test
    public void preamble_only_reply_rejected() {
        assertNull(WritingValidator.sanitize("Here is the rewritten version:"));
        assertNull(WritingValidator.sanitize("Certainly!"));
    }

    @Test
    public void real_content_starting_with_similar_words_kept() {
        // Starts with "here is" as genuine content, long sentence, no colon.
        String content = "Here is the plan we discussed for tomorrow morning "
                + "and the follow-up items everyone agreed on last week.";
        assertEquals(content, WritingValidator.sanitize(content));
    }

    @Test
    public void excess_blank_lines_collapsed() {
        String raw = "Line one.\n\n\n\n\nLine two.";
        assertEquals("Line one.\n\nLine two.", WritingValidator.sanitize(raw));
    }

    @Test
    public void oversized_output_capped() {
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < 20_000; i++) big.append('x');
        String out = WritingValidator.sanitize(big.toString());
        assertNotNull(out);
        assertEquals(WritingValidator.MAX_OUTPUT_CHARS, out.length());
    }

    @Test
    public void whitespace_around_content_trimmed() {
        assertEquals("Tight.", WritingValidator.sanitize("  \n  Tight.  \n "));
    }
}
