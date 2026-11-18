package com.linkvault.app.ai;

import androidx.annotation.Nullable;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Phase 1 of the pipeline: offline, deterministic classification from the
 * URL (and title) alone — platform detection + rule table. Works with zero
 * model installed; its output is also the baseline the LLM layer enriches.
 */
public final class RuleBasedAnalyzer {

    private RuleBasedAnalyzer() {}

    private static final class Rule {
        final String[] hosts;
        final String category;
        final String[] tags;
        final double confidence;
        /** Summary label ("YouTube video", "GitHub", ...) or null → generic. */
        final String label;

        Rule(String[] hosts, String category, String[] tags, double confidence, String label) {
            this.hosts = hosts;
            this.category = category;
            this.tags = tags;
            this.confidence = confidence;
            this.label = label;
        }
    }

    private static final Rule[] RULES = {
            new Rule(new String[]{"youtube.com", "youtu.be"}, "Videos",
                    new String[]{"youtube", "video"}, 0.92, "YouTube video"),
            new Rule(new String[]{"github.com", "gitlab.com", "bitbucket.org"}, "Development",
                    new String[]{"development", "code"}, 0.92, "GitHub"),
            new Rule(new String[]{"stackoverflow.com", "stackexchange.com", "superuser.com"},
                    "Development", new String[]{"development", "reference"}, 0.88, "Stack Overflow"),
            new Rule(new String[]{"docs.google.com", "sheets.google.com", "slides.google.com"},
                    "Docs", new String[]{"docs", "google"}, 0.88, "Google Doc"),
            new Rule(new String[]{"notion.so", "notion.site"}, "Docs",
                    new String[]{"docs", "notion"}, 0.88, "Notion page"),
            new Rule(new String[]{"figma.com", "canva.com"}, "Design",
                    new String[]{"design", "figma"}, 0.88, "Design file"),
            new Rule(new String[]{"linkedin.com"}, "Professional",
                    new String[]{"professional", "linkedin"}, 0.9, "LinkedIn post"),
            new Rule(new String[]{"twitter.com", "x.com"}, "Social",
                    new String[]{"social", "twitter"}, 0.9, "Post on X"),
            new Rule(new String[]{"instagram.com"}, "Social",
                    new String[]{"social", "instagram"}, 0.9, "Instagram post"),
            new Rule(new String[]{"facebook.com", "fb.watch"}, "Social",
                    new String[]{"social", "facebook"}, 0.9, "Facebook post"),
            new Rule(new String[]{"reddit.com"}, "Social",
                    new String[]{"social", "reddit"}, 0.9, "Reddit thread"),
            new Rule(new String[]{"medium.com", "substack.com", "wordpress.com", "blogger.com",
                    "ghost.io", "dev.to"}, "Reading",
                    new String[]{"article", "reading"}, 0.88, "Article"),
            new Rule(new String[]{"amazon.in", "amazon.com", "flipkart.com", "ebay.com",
                    "ebay.in", "myntra.com"}, "Shopping",
                    new String[]{"shopping", "product"}, 0.9, "Shopping"),
            new Rule(new String[]{"netflix.com", "primevideo.com", "disneyplus.com",
                    "hotstar.com"}, "Entertainment",
                    new String[]{"entertainment", "streaming"}, 0.9, "Streaming"),
            new Rule(new String[]{"open.spotify.com", "soundcloud.com"}, "Entertainment",
                    new String[]{"music", "streaming"}, 0.9, "Music"),
            new Rule(new String[]{"wikipedia.org", "wikimedia.org"}, "Reference",
                    new String[]{"reference", "wikipedia"}, 0.92, "Wikipedia"),
            new Rule(new String[]{"bbc.co.uk", "bbc.com", "cnn.com", "ndtv.com",
                    "timesofindia.indiatimes.com", "hindustantimes.com", "theverge.com",
                    "techcrunch.com", "arstechnica.com", "thehindu.com"}, "News",
                    new String[]{"news"}, 0.88, "News article"),
            new Rule(new String[]{"chatgpt.com", "openai.com", "anthropic.com", "gemini.google.com",
                    "perplexity.ai", "claude.ai"}, "Tools",
                    new String[]{"ai", "tools"}, 0.88, "AI tool"),
            new Rule(new String[]{"coursera.org", "udemy.com", "edx.org", "khanacademy.org",
                    "freecodecamp.org"}, "Learning",
                    new String[]{"learning", "course"}, 0.88, "Course"),
    };

    /** Never null — returns at least a low-confidence generic result. */
    public static AiResult analyze(String url, @Nullable String title) {
        String host = hostOf(url);
        String cleanTitle = cleanTitle(title);

        for (Rule r : RULES) {
            if (matches(host, r.hosts)) {
                String summary = buildSummary(r.label, cleanTitle, host);
                List<String> tags = new ArrayList<>(List.of(r.tags));
                return new AiResult(r.category, tags, summary, r.confidence);
            }
        }

        // No platform match: weak baseline, LLM expected to refine.
        List<String> tags = new ArrayList<>();
        String summary;
        if (cleanTitle != null) {
            summary = truncate("Link: " + cleanTitle, AiContract.MAX_SUMMARY_CHARS);
        } else {
            summary = host.isEmpty() ? "Saved link" : host + " link";
        }
        return new AiResult(null, tags, summary, 0.3);
    }

    private static boolean matches(String host, String[] hosts) {
        if (host.isEmpty()) return false;
        for (String h : hosts) {
            if (host.equals(h) || host.endsWith("." + h)) return true;
        }
        return false;
    }

    private static String hostOf(String url) {
        if (url == null) return "";
        try {
            URI uri = URI.create(url.trim());
            String host = uri.getHost();
            if (host == null) return "";
            host = host.toLowerCase(Locale.ROOT);
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (Exception e) {
            return "";
        }
    }

    /** Drops common site suffixes (" - YouTube") and caps length. */
    @Nullable
    private static String cleanTitle(@Nullable String title) {
        if (title == null) return null;
        String t = title.trim();
        if (t.isEmpty()) return null;
        int cut = -1;
        for (String sep : new String[]{" - ", " | ", " — ", " » "}) {
            int i = t.lastIndexOf(sep);
            if (i > 0 && i > cut) cut = i;
        }
        if (cut > 0) t = t.substring(0, cut).trim();
        if (t.length() > 160) t = t.substring(0, 160).trim();
        return t.isEmpty() ? null : t;
    }

    private static String buildSummary(String label, @Nullable String title, String host) {
        String base;
        if (title != null) {
            base = (label == null ? "Link" : label) + ": " + title;
        } else {
            base = label == null
                    ? (host.isEmpty() ? "Saved link" : host + " link")
                    : label + " from " + (host.isEmpty() ? "web" : host);
        }
        return truncate(base, AiContract.MAX_SUMMARY_CHARS);
    }

    private static String truncate(String s, int max) {
        if (s.length() <= max) return s;
        int cut = s.lastIndexOf(' ', max - 1);
        if (cut < max / 2) cut = max - 1;
        return s.substring(0, cut).trim();
    }
}
