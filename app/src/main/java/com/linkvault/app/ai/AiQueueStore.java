package com.linkvault.app.ai;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Persistent FIFO of record ids awaiting analysis (internal storage — never
 * the SAF vault folder, spec §9). Survives process death; WorkManager keeps
 * the job itself, this keeps the items. All access is synchronized; every
 * mutation is persisted immediately (tiny file).
 *
 * JSON shape:
 * {"pending":["id",...],"attempts":{"id":n},"failed":[{"id","attempts","reason","at"}]}
 */
final class AiQueueStore {

    private static final int MAX_FAILED = 50;

    private final File file;
    private final ArrayDeque<String> pending = new ArrayDeque<>();
    private final Map<String, Integer> attempts = new LinkedHashMap<>();
    private final JSONArray failed = new JSONArray();
    private boolean loaded;

    AiQueueStore(File file) {
        this.file = file;
    }

    synchronized boolean enqueue(String id) {
        ensureLoaded();
        if (id == null || id.isEmpty() || pending.contains(id)) return false;
        pending.add(id);
        persist();
        return true;
    }

    @Nullable
    synchronized String peek() {
        ensureLoaded();
        return pending.peek();
    }

    synchronized void markAttempt(String id) {
        ensureLoaded();
        attempts.put(id, attempts.getOrDefault(id, 0) + 1);
        persist();
    }

    synchronized int attemptsOf(String id) {
        ensureLoaded();
        return attempts.getOrDefault(id, 0);
    }

    synchronized void complete(String id) {
        ensureLoaded();
        pending.remove(id);
        attempts.remove(id);
        persist();
    }

    /** Attempt failed but retries remain: move the head to the tail. */
    synchronized void rotate(String id) {
        ensureLoaded();
        if (id != null && id.equals(pending.peek())) {
            pending.poll();
            pending.add(id);
            persist();
        }
    }

    /** Attempts exhausted: drop from pending, keep a bounded failure record. */
    synchronized void fail(String id, @Nullable String reason) {
        ensureLoaded();
        pending.remove(id);
        attempts.remove(id);
        try {
            JSONObject entry = new JSONObject();
            entry.put("id", id);
            entry.put("at", Instant.now().toString());
            entry.put("reason", reason == null ? "error" : reason);
            failed.put(entry);
            while (failed.length() > MAX_FAILED) failed.remove(0);
        } catch (Exception ignored) {
            // failure bookkeeping is best-effort
        }
        persist();
    }

    synchronized boolean hasPending() {
        ensureLoaded();
        return !pending.isEmpty();
    }

    synchronized int pendingCount() {
        ensureLoaded();
        return pending.size();
    }

    synchronized int failedCount() {
        ensureLoaded();
        return failed.length();
    }

    // -------------------------------------------------------------- internal

    private void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        if (!file.isFile()) return;
        String raw = null;
        try (FileInputStream in = new FileInputStream(file);
             InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[4096];
            int n;
            while ((n = reader.read(buf)) > 0) sb.append(buf, 0, n);
            raw = sb.toString();
            JSONObject o = new JSONObject(raw);
            JSONArray arr = o.optJSONArray("pending");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    String id = arr.optString(i, null);
                    if (id != null && !id.isEmpty()) pending.add(id);
                }
            }
            JSONObject att = o.optJSONObject("attempts");
            if (att != null) {
                Iterator<String> keys = att.keys();
                while (keys.hasNext()) {
                    String k = keys.next();
                    attempts.put(k, att.optInt(k, 0));
                }
            }
            JSONArray f = o.optJSONArray("failed");
            if (f != null) {
                for (int i = 0; i < f.length() && failed.length() < MAX_FAILED; i++) {
                    JSONObject e = f.optJSONObject(i);
                    if (e != null) failed.put(e);
                }
            }
        } catch (Exception e) {
            // Corrupt queue is not user data — quarantine and start clean.
            pending.clear();
            attempts.clear();
            while (failed.length() > 0) failed.remove(0);
            if (raw != null) {
                //noinspection ResultOfMethodCallIgnored
                file.renameTo(new File(file.getPath() + ".bad"));
            }
        }
    }

    private void persist() {
        try {
            JSONObject o = new JSONObject();
            o.put("pending", new JSONArray(pending));
            JSONObject att = new JSONObject();
            for (Map.Entry<String, Integer> e : attempts.entrySet()) {
                att.put(e.getKey(), e.getValue());
            }
            o.put("attempts", att);
            o.put("failed", failed);
            File tmp = new File(file.getPath() + ".tmp");
            try (OutputStreamWriter w = new OutputStreamWriter(
                    new java.io.FileOutputStream(tmp), StandardCharsets.UTF_8)) {
                w.write(o.toString());
            }
            if (!tmp.renameTo(file)) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
                //noinspection ResultOfMethodCallIgnored
                tmp.renameTo(file);
            }
        } catch (Exception ignored) {
            // Durability best-effort: in-memory state still drives this run.
        }
    }
}
