package com.linkvault.app.data.repo;

import androidx.annotation.Nullable;

import com.linkvault.app.data.json.AutoCatCodec;
import com.linkvault.app.data.json.MalformedRecordException;
import com.linkvault.app.data.saf.DocumentTreeClient;
import com.linkvault.app.data.saf.SafeWriteEngine;
import com.linkvault.app.data.saf.StorageException;
import com.linkvault.app.domain.model.AutoRule;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * user/autorules.json CRUD. File is created lazily on the first rule;
 * a missing file simply means "no rules". A malformed file is surfaced
 * (never overwritten) — auto-categorize then degrades to "no match".
 */
public class AutoCatRepository {

    static final String REL_DIR = "user";
    static final String FILE_NAME = "autorules.json";
    static final String REL_FILE = REL_DIR + "/" + FILE_NAME;

    private final DocumentTreeClient tree;
    private final SafeWriteEngine engine;
    private final AutoCatCodec codec;

    private List<AutoRule> cache;

    public AutoCatRepository(DocumentTreeClient tree, SafeWriteEngine engine,
                             AutoCatCodec codec) {
        this.tree = tree;
        this.engine = engine;
        this.codec = codec;
    }

    public synchronized List<AutoRule> getAll() throws StorageException {
        if (cache != null) return cache;
        android.net.Uri uri = tree.resolveUri(REL_FILE);
        if (uri == null) {
            cache = List.of();
            return cache;
        }
        String json = tree.readUtf8(uri);
        try {
            cache = Collections.unmodifiableList(codec.fromJson(json, REL_FILE));
        } catch (MalformedRecordException e) {
            throw new StorageException(StorageException.Reason.VALIDATION_FAILED,
                    "autorules.json invalid: " + e.getMessage(), e);
        }
        return cache;
    }

    /** Adds (or replaces) the rule for a domain. */
    public synchronized void addRule(String rawDomain, String categoryId)
            throws StorageException {
        String domain = normalizeDomain(rawDomain);
        if (domain.isEmpty()) {
            throw new StorageException(StorageException.Reason.INVALID_NAME,
                    "Domain must be 1-100 characters");
        }
        if (categoryId == null || categoryId.trim().isEmpty()) {
            throw new StorageException(StorageException.Reason.INVALID_NAME,
                    "categoryId required");
        }
        List<AutoRule> all = new ArrayList<>(getAll());
        all.removeIf(r -> r.domain.equals(domain));
        all.add(new AutoRule(domain, categoryId.trim()));
        persist(all);
    }

    public synchronized void removeRule(String rawDomain) throws StorageException {
        String domain = normalizeDomain(rawDomain);
        List<AutoRule> all = new ArrayList<>(getAll());
        all.removeIf(r -> r.domain.equals(domain));
        persist(all);
    }

    /**
     * Longest-suffix host match for a URL (host equals domain or ends with
     * ".domain"). Returns the category id or null. Storage problems (e.g. a
     * malformed autorules.json) degrade to "no match" — never fails a save.
     */
    @Nullable
    public synchronized String matchFor(String url) {
        if (url == null || url.isEmpty()) return null;
        String host;
        try {
            host = new URI(url).getHost();
        } catch (Exception e) {
            return null;
        }
        if (host == null || host.isEmpty()) return null;
        host = host.toLowerCase(Locale.ROOT);
        if (host.startsWith("www.")) host = host.substring(4);

        AutoRule best = null;
        try {
            for (AutoRule r : getAll()) {
                if (host.equals(r.domain) || host.endsWith("." + r.domain)) {
                    if (best == null || r.domain.length() > best.domain.length()) {
                        best = r;
                    }
                }
            }
        } catch (StorageException e) {
            return null;
        }
        return best == null ? null : best.categoryId;
    }

    /** User text → canonical rule domain: lowercase, no scheme/www/path/port. */
    public static String normalizeDomain(String raw) {
        if (raw == null) return "";
        String d = raw.trim().toLowerCase(Locale.ROOT);
        d = d.replaceFirst("^https?://", "");
        d = d.replaceFirst("^www\\.", "");
        int slash = d.indexOf('/');
        if (slash >= 0) d = d.substring(0, slash);
        int colon = d.indexOf(':');
        if (colon >= 0) d = d.substring(0, colon);
        if (d.length() > 100) d = d.substring(0, 100);
        return d.trim();
    }

    private void persist(List<AutoRule> rules) throws StorageException {
        String json = codec.toJson(rules);
        engine.writeJson(REL_DIR, FILE_NAME, json,
                s -> codec.fromJson(s, REL_FILE));
        cache = Collections.unmodifiableList(new ArrayList<>(rules));
    }
}
