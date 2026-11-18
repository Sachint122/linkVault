package com.linkvault.app.ai;

/**
 * Writing assistant actions (spec §4E). Output language is ALWAYS English.
 * Each action carries its dedicated instruction — composed into the system
 * prompt by {@link WritingPrompt}.
 */
public enum WritingAction {

    REWRITE("Rewrite",
            "Rewrite the text so it reads clearly and smoothly, keeping the "
                    + "original meaning and every factual detail exactly as given."),

    IMPROVE("Improve",
            "Improve the grammar, wording and flow of the text while preserving "
                    + "its meaning and all factual details."),

    SHORTEN("Shorten",
            "Shorten the text while keeping the key message and every important "
                    + "fact. Remove only what is redundant — never the meaning."),

    EXPAND("Expand",
            "Expand the text slightly for clarity and completeness using ONLY "
                    + "information already present in it. Never add new facts, "
                    + "names, dates or claims."),

    TRANSLATE("Translate",
            "Translate the text into English. Keep all facts, names, numbers and "
                    + "the original meaning exactly — do not add or drop anything.");

    public final String label;
    public final String instruction;

    WritingAction(String label, String instruction) {
        this.label = label;
        this.instruction = instruction;
    }
}
