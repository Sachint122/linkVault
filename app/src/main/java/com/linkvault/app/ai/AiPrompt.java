package com.linkvault.app.ai;

import androidx.annotation.Nullable;

import java.util.List;

/** Prompt construction for the local LLM (strict JSON-out contract). */
public final class AiPrompt {

    private AiPrompt() {}

    public static final String SYSTEM =
            "You classify saved web links for a personal bookmark app. "
                    + "Reply with ONLY one compact JSON object and nothing else — no markdown, "
                    + "no code fences, no commentary: "
                    + "{\"category\":\"...\",\"tags\":[\"...\"],\"summary\":\"...\",\"confidence\":0.0} "
                    + "Rules: category is a single short lowercase word (examples: tech, development, "
                    + "videos, shopping, news, social, reading, tools, reference, entertainment, "
                    + "design, professional, learning). "
                    + "tags: 1 to 5 short lowercase keywords, no duplicates. "
                    + "summary: one plain sentence of at most 120 characters saying what the link "
                    + "contains. confidence: a number between 0.0 and 1.0.";

    public static String userPrompt(String url, @Nullable String title,
                                    List<String> existingTags, @Nullable String categoryName) {
        StringBuilder sb = new StringBuilder();
        sb.append("URL: ").append(url).append('\n');
        sb.append("Title: ").append(title == null || title.isEmpty() ? "(none)" : title).append('\n');
        if (existingTags == null || existingTags.isEmpty()) {
            sb.append("Existing tags: (none)\n");
        } else {
            sb.append("Existing tags: ");
            for (int i = 0; i < existingTags.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(existingTags.get(i));
            }
            sb.append('\n');
        }
        sb.append("Current category: ")
                .append(categoryName == null || categoryName.isEmpty() ? "uncategorized" : categoryName);
        return sb.toString();
    }
}
