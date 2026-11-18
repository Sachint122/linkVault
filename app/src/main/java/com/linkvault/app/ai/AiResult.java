package com.linkvault.app.ai;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Validated output of an analysis step (rule-based or local LLM). */
public final class AiResult {

    /** Lowercase-ish single-word category; null = no opinion. */
    @Nullable public final String category;
    /** 1..5 normalized tags. */
    public final List<String> tags;
    /** One-line summary, <= {@link AiContract#MAX_SUMMARY_CHARS}; null = none. */
    @Nullable public final String summary;
    /** 0..1 confidence. */
    public final double confidence;

    public AiResult(@Nullable String category, List<String> tags,
                    @Nullable String summary, double confidence) {
        this.category = category;
        this.tags = tags == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(tags));
        this.summary = summary;
        double c = confidence;
        if (Double.isNaN(c) || c < 0) c = 0;
        if (c > 1) c = 1;
        this.confidence = c;
    }

    public boolean hasAnyValue() {
        return category != null || summary != null || !tags.isEmpty();
    }

    /**
     * LLM result layered over the rule baseline: the LLM wins per field when
     * it produced a value, rules fill the gaps; tags union (LLM first) capped
     * at {@link AiContract#MAX_AI_TAGS}.
     */
    public static AiResult merge(AiResult rules, AiResult llm) {
        String category = llm.category != null ? llm.category : rules.category;
        String summary = llm.summary != null ? llm.summary : rules.summary;
        List<String> tags = new ArrayList<>();
        for (String t : llm.tags) addTag(tags, t);
        for (String t : rules.tags) addTag(tags, t);
        double confidence = Math.max(rules.confidence, llm.confidence);
        return new AiResult(category, tags, summary, confidence);
    }

    private static void addTag(List<String> out, String tag) {
        if (out.size() >= AiContract.MAX_AI_TAGS) return;
        for (String existing : out) {
            if (existing.equalsIgnoreCase(tag)) return;
        }
        out.add(tag);
    }
}
