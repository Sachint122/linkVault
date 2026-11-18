package com.linkvault.app.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** History retention (spec §8): cap 10, newest-first, delete, clear. */
public class WritingHistoryTest {

    private static final class MapStore implements WritingHistory.StringStore {
        final Map<String, String> map = new HashMap<>();

        @Override
        public String get(String key) {
            return map.get(key);
        }

        @Override
        public void put(String key, String value) {
            map.put(key, value);
        }
    }

    private MapStore store;
    private WritingHistory history;

    @Before
    public void setUp() {
        store = new MapStore();
        history = new WritingHistory(store);
    }

    private static WritingHistory.Entry entry(String input, String output, long ts) {
        return new WritingHistory.Entry(input, output,
                WritingTone.PROFESSIONAL.label, ts);
    }

    @Test
    public void empty_history() {
        assertEquals(0, history.size());
        assertTrue(history.list().isEmpty());
    }

    @Test
    public void add_stores_fields_and_newest_first() {
        history.add(entry("in1", "out1", 1000));
        history.add(entry("in2", "out2", 2000));
        List<WritingHistory.Entry> all = history.list();
        assertEquals(2, all.size());
        assertEquals("out2", all.get(0).output);   // newest first
        assertEquals("in2", all.get(0).input);
        assertEquals(WritingTone.PROFESSIONAL.label, all.get(0).tone);
        assertEquals(2000L, all.get(0).timestamp);
        assertEquals("out1", all.get(1).output);
    }

    @Test
    public void cap_evicts_oldest_beyond_10() {
        for (int i = 0; i < 14; i++) {
            history.add(entry("in" + i, "out" + i, i));
        }
        assertEquals(WritingHistory.MAX_ENTRIES, history.size());
        List<WritingHistory.Entry> all = history.list();
        assertEquals("out13", all.get(0).output);           // newest kept
        assertEquals("out4", all.get(9).output);            // oldest survivor
        // out0..out3 evicted
        for (WritingHistory.Entry e : all) {
            assertFalse(e.output.equals("out0"));
        }
    }

    @Test
    public void individual_delete_by_index() {
        history.add(entry("a", "outA", 1));
        history.add(entry("b", "outB", 2));
        history.add(entry("c", "outC", 3));
        assertTrue(history.remove(1));      // removes outB
        assertEquals(2, history.size());
        assertEquals("outC", history.list().get(0).output);
        assertEquals("outA", history.list().get(1).output);
        assertFalse(history.remove(99));    // out of range → no-op
        assertFalse(history.remove(-1));
        assertEquals(2, history.size());
    }

    @Test
    public void clear_empties_everything() {
        history.add(entry("a", "outA", 1));
        history.clear();
        assertEquals(0, history.size());
        assertTrue(store.map.isEmpty() || store.map.get("entries").equals("[]"));
    }

    @Test
    public void roundtrip_survives_new_instance() {
        history.add(entry("persist me", "persisted output", 555));
        WritingHistory reopened = new WritingHistory(store);
        List<WritingHistory.Entry> all = reopened.list();
        assertEquals(1, all.size());
        assertEquals("persist me", all.get(0).input);
        assertEquals("persisted output", all.get(0).output);
        assertEquals(555L, all.get(0).timestamp);
    }

    @Test
    public void corrupt_payload_recovers_as_empty() {
        store.put("entries", "{not json!!");
        assertNotNull(history.list());
        assertEquals(0, history.size());
        history.add(entry("x", "y", 1)); // still usable after corruption
        assertEquals(1, history.size());
    }
}
