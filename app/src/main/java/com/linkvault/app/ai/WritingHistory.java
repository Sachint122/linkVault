package com.linkvault.app.ai;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Local storage for the last {@link #MAX_ENTRIES} writing assistant results
 * (spec §8): original input + generated output + tone + timestamp, newest
 * first, older entries evicted, individual delete + Clear All. No PIN/fingerprint
 * (spec §8) — plain app-private SharedPreferences.
 *
 * Storage is abstracted behind {@link StringStore} so the JSON logic runs in
 * plain JVM unit tests (SharedPreferences would be an Android mock).
 */
public class WritingHistory {

    public static final int MAX_ENTRIES = 10;

    private static final String PREFS = "ai_writing_history";
    private static final String KEY_ENTRIES = "entries";

    /** Minimal key-value string storage (prod: SharedPreferences, tests: map). */
    public interface StringStore {
        @Nullable
        String get(String key);
        void put(String key, String value);
    }

    /** One stored message pair (spec §8 fields exactly). */
    public static final class Entry {
        public final String input;
        public final String output;
        public final String tone;   // WritingTone.label
        public final long timestamp;

        public Entry(String input, String output, String tone, long timestamp) {
            this.input = input;
            this.output = output;
            this.tone = tone;
            this.timestamp = timestamp;
        }

        JSONObject toJson() {
            try {
                JSONObject o = new JSONObject();
                o.put("input", input);
                o.put("output", output);
                o.put("tone", tone);
                o.put("ts", timestamp);
                return o;
            } catch (Exception e) {
                return new JSONObject();
            }
        }

        @Nullable
        static Entry fromJson(JSONObject o) {
            try {
                String input = o.optString("input", null);
                String output = o.optString("output", null);
                if (output == null || output.isEmpty()) return null;
                return new Entry(input == null ? "" : input, output,
                        o.optString("tone", WritingTone.DEFAULT.label),
                        o.optLong("ts", 0L));
            } catch (Exception e) {
                return null;
            }
        }
    }

    private final StringStore store;
    private final Object lock = new Object();

    public WritingHistory(Context context) {
        this(new PrefsStore(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)));
    }

    public WritingHistory(StringStore store) {
        this.store = store;
    }

    /** Newest first (list index == stored index). */
    public List<Entry> list() {
        synchronized (lock) {
            return load();
        }
    }

    /** Inserts at the head; evicts beyond the 10-record cap. */
    public void add(Entry entry) {
        if (entry == null) return;
        synchronized (lock) {
            List<Entry> all = load();
            all.add(0, entry);
            while (all.size() > MAX_ENTRIES) {
                all.remove(all.size() - 1);
            }
            save(all);
        }
    }

    /** Removes one entry by displayed/stored index. Returns true if removed. */
    public boolean remove(int index) {
        synchronized (lock) {
            List<Entry> all = load();
            if (index < 0 || index >= all.size()) return false;
            all.remove(index);
            save(all);
            return true;
        }
    }

    public void clear() {
        synchronized (lock) {
            save(new ArrayList<>());
        }
    }

    public int size() {
        synchronized (lock) {
            return load().size();
        }
    }

    // ------------------------------------------------------------ internals

    private List<Entry> load() {
        List<Entry> out = new ArrayList<>();
        try {
            String raw = store.get(KEY_ENTRIES);
            if (raw == null || raw.isEmpty()) return out;
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                Entry e = Entry.fromJson(arr.optJSONObject(i));
                if (e != null) out.add(e);
            }
        } catch (Exception ignored) {
            // corrupt payload → start fresh; history is best-effort
        }
        return out;
    }

    private void save(List<Entry> all) {
        try {
            JSONArray arr = new JSONArray();
            for (Entry e : all) arr.put(e.toJson());
            store.put(KEY_ENTRIES, arr.toString());
        } catch (Exception ignored) {
            // best-effort persistence
        }
    }

    private static final class PrefsStore implements StringStore {
        private final SharedPreferences prefs;

        PrefsStore(SharedPreferences prefs) {
            this.prefs = prefs;
        }

        @Override
        public String get(String key) {
            return prefs.getString(key, null);
        }

        @Override
        public void put(String key, String value) {
            prefs.edit().putString(key, value).apply();
        }
    }
}
