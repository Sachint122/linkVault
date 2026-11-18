package com.linkvault.app.ai;

/**
 * Writing assistant tones (spec §4C) — 10 tones, default PROFESSIONAL
 * ("Natural Professional English": professional yet natural, never robotic).
 * Favourite Tones feature explicitly NOT added (spec §10).
 */
public enum WritingTone {

    PROFESSIONAL("Professional",
            "Use natural professional English — confident and polished, but "
                    + "warm and human, never stiff or robotic."),

    FORMAL("Formal",
            "Use formal business English with proper structure and respectful, "
                    + "measured phrasing."),

    FRIENDLY("Friendly",
            "Use warm, approachable English with a friendly human tone."),

    CASUAL("Casual",
            "Use relaxed, everyday conversational English."),

    POLITE("Polite",
            "Use courteous, considerate English."),

    CONFIDENT("Confident",
            "Use assured, direct English that states things clearly."),

    CONCISE("Concise",
            "Use tight, economical English — every word must earn its place."),

    PERSUASIVE("Persuasive",
            "Use compelling English that makes a strong case without exaggeration."),

    EMPATHETIC("Empathetic",
            "Use understanding, considerate English that acknowledges the "
                    + "reader's perspective."),

    NATURAL_ENGLISH("Natural English",
            "Sound like a fluent native English speaker — idiomatic, smooth "
                    + "and effortless.");

    public static final WritingTone DEFAULT = PROFESSIONAL;

    public final String label;
    public final String instruction;

    WritingTone(String label, String instruction) {
        this.label = label;
        this.instruction = instruction;
    }

    /** Label lookup for restoring persisted state; falls back to the default. */
    public static WritingTone fromLabel(String label) {
        if (label != null) {
            for (WritingTone t : values()) {
                if (t.label.equalsIgnoreCase(label)) return t;
            }
        }
        return DEFAULT;
    }
}
