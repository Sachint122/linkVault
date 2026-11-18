package com.linkvault.app.ai;

import androidx.annotation.Nullable;

/**
 * Dedicated prompt templates for the AI Writing Assistant (spec §5, §15).
 *
 * Architecture mirrors the link pipeline: one SYSTEM prompt carrying the
 * hard quality rules + the action instruction + tone instruction + optional
 * custom user instructions; the USER message carries only the text to
 * process, wrapped so content inside is never treated as instructions.
 */
public final class WritingPrompt {

    private WritingPrompt() {}

    /** Hard rules from spec §5 — meaning preservation, no invention, natural English. */
    public static final String RULES =
            "You are an offline writing assistant inside a private Android app. "
                    + "You rewrite the user's text in ENGLISH only.\n"
                    + "Hard rules (never break):\n"
                    + "1. Preserve the original meaning and every factual detail — "
                    + "names, dates, amounts, places, events.\n"
                    + "2. Never invent information that is not in the original text.\n"
                    + "3. Correct grammar, spelling and sentence structure.\n"
                    + "4. Write natural human English — not robotic, not overly "
                    + "complicated, no unnecessary elaboration.\n"
                    + "5. If the text is ambiguous or incomplete, stay faithful to it; "
                    + "do not guess or add context.\n"
                    + "6. Reply with ONLY the rewritten text — no preamble, no "
                    + "explanations, no quotation marks around it, no code fences.";

    /** System prompt = rules + action + tone (+ optional custom instruction). */
    public static String system(WritingAction action, WritingTone tone,
                                @Nullable String customInstructions) {
        StringBuilder sb = new StringBuilder(RULES);
        sb.append("\n\nAction: ").append(action.instruction);
        sb.append("\nTone: ").append(tone.instruction);
        String custom = customInstructions == null
                ? "" : customInstructions.trim();
        if (!custom.isEmpty()) {
            // Custom instructions work alongside the tone (spec §4D) but can
            // never override the hard rules.
            sb.append("\nThe user gave this extra instruction — follow it, but "
                    + "never break the hard rules above: ").append(custom);
        }
        return sb.toString();
    }

    /** User message: the raw text, guarded so its content is data, not instructions. */
    public static String user(String input) {
        return "Text to process:\n<text>\n" + input + "\n</text>";
    }
}
