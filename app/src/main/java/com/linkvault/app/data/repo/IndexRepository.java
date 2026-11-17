package com.linkvault.app.data.repo;

import androidx.annotation.Nullable;

import com.linkvault.app.data.saf.DocumentTreeClient;
import com.linkvault.app.data.saf.SafeWriteEngine;
import com.linkvault.app.domain.model.FileRecord;
import com.linkvault.app.domain.model.LinkRecord;
import com.linkvault.app.domain.model.VaultItem;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Rebuildable index and client-side multi-token search engine (FR-04, D-05).
 * Combines LinkRecords and FileRecords into unified VaultItems with sorting,
 * filtering, and caching in .index/index.json.
 */
public class IndexRepository {

    public enum SortOrder {
        NEWEST,
        OLDEST,
        TITLE_AZ
    }

    private static final String INDEX_DIR = ".index";
    private static final String INDEX_FILE = "index.json";

    private final DocumentTreeClient tree;
    private final SafeWriteEngine engine;
    private final RecordRepository recordRepo;
    private final FileRepository fileRepo;
    private final com.linkvault.app.data.json.LinkCodec linkCodec =
            new com.linkvault.app.data.json.LinkCodec();
    private final com.linkvault.app.data.json.FileCodec fileCodec =
            new com.linkvault.app.data.json.FileCodec();

    private final List<VaultItem> memoryCache = new ArrayList<>();
    private boolean isLoaded = false;
    /** True after a mutation invalidated the cache — next load must rescan disk. */
    private boolean dirtySinceScan = false;
    /** Set when the last load came from index.json; consumer runs one verify rescan. */
    private boolean coldVerifyPending = false;

    public IndexRepository(
            DocumentTreeClient tree,
            SafeWriteEngine engine,
            RecordRepository recordRepo,
            FileRepository fileRepo) {
        this.tree = tree;
        this.engine = engine;
        this.recordRepo = recordRepo;
        this.fileRepo = fileRepo;
    }

    /**
     * Loads all items. Cold start (no mutation this process) reads the
     * persisted .index/index.json — a single file — instead of scanning every
     * record on disk. Post-mutation loads always rescan so the index cannot
     * go stale within a process.
     */
    public synchronized List<VaultItem> loadAll(boolean forceRescan) {
        if (isLoaded && !forceRescan) {
            return new ArrayList<>(memoryCache);
        }

        if (!forceRescan && !dirtySinceScan) {
            List<VaultItem> cached = readIndexFile();
            if (cached != null) {
                memoryCache.clear();
                memoryCache.addAll(cached);
                isLoaded = true;
                coldVerifyPending = true;
                return new ArrayList<>(memoryCache);
            }
        }

        List<VaultItem> items = new ArrayList<>();
        // Scan links
        try {
            List<LinkRecord> links = recordRepo.getAll();
            for (LinkRecord l : links) {
                items.add(new VaultItem(l));
            }
        } catch (Exception ignored) {}

        // Scan files
        try {
            List<FileRecord> files = fileRepo.getAllFiles();
            for (FileRecord f : files) {
                items.add(new VaultItem(f));
            }
        } catch (Exception ignored) {}

        memoryCache.clear();
        memoryCache.addAll(items);
        isLoaded = true;
        dirtySinceScan = false;

        // Persist cache asynchronously / safely in background
        saveIndexCache(items);

        return new ArrayList<>(memoryCache);
    }

    /**
     * Rebuilds the index completely from scratch and updates .index/index.json.
     */
    public synchronized List<VaultItem> rebuild() {
        return loadAll(true);
    }

    /**
     * Invalidate in-memory cache so subsequent reads reload from disk.
     */
    public synchronized void invalidate() {
        isLoaded = false;
        dirtySinceScan = true;
    }

    /** One-shot: true if the last load was the fast index.json path. */
    public synchronized boolean consumeColdVerify() {
        boolean v = coldVerifyPending;
        coldVerifyPending = false;
        return v;
    }

    /**
     * Multi-token search with filters and sorting.
     */
    public synchronized List<VaultItem> query(
            @Nullable String textQuery,
            @Nullable String categoryId,
            @Nullable VaultItem.ItemType typeFilter,
            SortOrder sortOrder,
            @Nullable Map<String, String> categoryNames) {

        List<VaultItem> source = isLoaded ? memoryCache : loadAll(false);
        List<VaultItem> filtered = new ArrayList<>();

        String[] tokens = (textQuery == null || textQuery.trim().isEmpty())
                ? new String[0]
                : textQuery.trim().toLowerCase(Locale.US).split("\\s+");

        for (VaultItem item : source) {
            // Type filter
            if (typeFilter != null && item.getType() != typeFilter) {
                continue;
            }

            // Category filter
            if (categoryId != null && !categoryId.isEmpty() && !categoryId.equalsIgnoreCase("all")) {
                if (!categoryId.equals(item.getCategoryId())) {
                    continue;
                }
            }

            // Query match
            if (tokens.length > 0) {
                String catName = (categoryNames != null && categoryNames.containsKey(item.getCategoryId()))
                        ? categoryNames.get(item.getCategoryId()) : "";
                if (!matchesAllTokens(item, catName, tokens)) {
                    continue;
                }
            }

            filtered.add(item);
        }

        // Sorting
        Collections.sort(filtered, comparatorFor(sortOrder));
        return filtered;
    }

    private static boolean matchesAllTokens(VaultItem item, String categoryName, String[] tokens) {
        String title = item.getTitle().toLowerCase(Locale.US);
        String subtitle = item.getSubtitle().toLowerCase(Locale.US);
        String cat = categoryName.toLowerCase(Locale.US);
        List<String> tags = item.getTags();

        String desc = "";
        if (item.isLink() && item.getLink() != null && item.getLink().getDescription() != null) {
            desc = item.getLink().getDescription().toLowerCase(Locale.US);
        }

        for (String token : tokens) {
            boolean matched = title.contains(token)
                    || subtitle.contains(token)
                    || cat.contains(token)
                    || desc.contains(token);

            if (!matched && tags != null) {
                for (String t : tags) {
                    if (t.toLowerCase(Locale.US).contains(token)) {
                        matched = true;
                        break;
                    }
                }
            }

            if (!matched) return false;
        }
        return true;
    }

    private static Comparator<VaultItem> comparatorFor(SortOrder sortOrder) {
        return (a, b) -> {
            // Pinned links always appear first in default NEWEST sort
            if (sortOrder == SortOrder.NEWEST) {
                if (a.isPinned() != b.isPinned()) {
                    return a.isPinned() ? -1 : 1;
                }
            }

            switch (sortOrder) {
                case OLDEST:
                    return a.getCreatedAt().compareTo(b.getCreatedAt());
                case TITLE_AZ:
                    return a.getTitle().compareToIgnoreCase(b.getTitle());
                case NEWEST:
                default:
                    return b.getCreatedAt().compareTo(a.getCreatedAt());
            }
        };
    }

    /** Reads the persisted index. Returns null when missing/corrupt/legacy. */
    private List<VaultItem> readIndexFile() {
        try {
            android.net.Uri uri = tree.resolveUri(INDEX_DIR + "/" + INDEX_FILE);
            if (uri == null) return null;
            String json = tree.readUtf8(uri);
            JSONObject root = new JSONObject(json);
            JSONArray arr = root.optJSONArray("records");
            if (arr == null) return null;
            List<VaultItem> out = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String type = o.optString("type", "");
                JSONObject rec = o.optJSONObject("record");
                if (rec == null) return null; // legacy lossy format → full rescan
                String rel = INDEX_DIR + "/" + INDEX_FILE;
                if ("link".equals(type)) {
                    out.add(new VaultItem(linkCodec.fromJson(rec.toString(), rel)));
                } else if ("file".equals(type)) {
                    out.add(new VaultItem(fileCodec.fromJson(rec.toString(), rel)));
                }
            }
            return out;
        } catch (Exception e) {
            return null; // unreadable → caller falls back to full disk scan
        }
    }

    private void saveIndexCache(List<VaultItem> items) {
        try {
            JSONObject root = new JSONObject();
            root.put("schemaVersion", 2);
            root.put("builtAt", Instant.now().toString());

            JSONArray arr = new JSONArray();
            for (VaultItem it : items) {
                JSONObject obj = new JSONObject();
                if (it.isLink() && it.getLink() != null) {
                    obj.put("type", "link");
                    obj.put("record", new JSONObject(linkCodec.toJson(it.getLink())));
                } else if (it.getFile() != null) {
                    obj.put("type", "file");
                    obj.put("record", new JSONObject(fileCodec.toJson(it.getFile())));
                } else {
                    continue;
                }
                arr.put(obj);
            }
            root.put("records", arr);

            engine.writeJson(INDEX_DIR, INDEX_FILE, root.toString(2), s -> {
                new JSONObject(s);
            });
        } catch (Exception ignored) {
            // Index write failure is non-fatal: JSON records remain source of truth (D-05)
        }
    }
}
