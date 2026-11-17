package com.linkvault.app.data.json;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.linkvault.app.domain.model.LinkRecord;

import org.junit.Test;

import java.util.List;

public class LinkCodecTest {

    private final LinkCodec codec = new LinkCodec();

    private LinkRecord sample() {
        return LinkRecord.create(
                "b7c1e0a2-6f3d-4c8e-9a10-2d5f8b3c7e41",
                "Useful resource",
                "https://example.com/resource",
                "Optional note",
                "cat_ungrouped",
                "Instagram",
                "com.instagram.android",
                null,
                List.of("android", "reference"),
                true,
                "2026-11-16T10:00:00Z");
    }

    @Test
    public void roundTrip_preservesAllFields() throws Exception {
        LinkRecord original = sample();
        String json = codec.toJson(original);
        LinkRecord back = codec.fromJson(json, "user/categories/cat_ungrouped/link_x.json");

        assertEquals(original.getId(), back.getId());
        assertEquals(original.getTitle(), back.getTitle());
        assertEquals(original.getUrl(), back.getUrl());
        assertEquals(original.getDescription(), back.getDescription());
        assertEquals(original.getCategoryId(), back.getCategoryId());
        assertEquals(original.getSourceAppName(), back.getSourceAppName());
        assertEquals(original.getSourcePackageName(), back.getSourcePackageName());
        assertEquals(original.getTags(), back.getTags());
        assertTrue(back.isPinned());
        assertEquals(original.getCreatedAt(), back.getCreatedAt());
        assertEquals(original.getUpdatedAt(), back.getUpdatedAt());
        assertEquals(LinkRecord.SCHEMA_VERSION, back.getSchemaVersion());
    }

    @Test
    public void malformedJson_throwsMalformedKind() {
        MalformedRecordException e = assertThrows(MalformedRecordException.class,
                () -> codec.fromJson("{not json", "path/x.json"));
        assertEquals(MalformedRecordException.Kind.MALFORMED_JSON, e.getKind());
        assertEquals("path/x.json", e.getRelativePath());
    }

    @Test
    public void missingRequiredField_throwsSchemaInvalid() {
        MalformedRecordException e = assertThrows(MalformedRecordException.class,
                () -> codec.fromJson("{\"schemaVersion\":1,\"id\":\"a\"}", "p"));
        assertEquals(MalformedRecordException.Kind.SCHEMA_INVALID, e.getKind());
    }

    @Test
    public void newerSchemaVersion_rejectedNotOverwritten() {
        String json = "{"
                + "\"schemaVersion\":99,"
                + "\"id\":\"a\",\"type\":\"link\",\"title\":\"t\","
                + "\"url\":\"https://e.com\",\"categoryId\":\"cat_ungrouped\","
                + "\"createdAt\":\"2026-11-16T10:00:00Z\","
                + "\"updatedAt\":\"2026-11-16T10:00:00Z\"}";
        MalformedRecordException e = assertThrows(MalformedRecordException.class,
                () -> codec.fromJson(json, "p"));
        assertEquals(MalformedRecordException.Kind.NEWER_SCHEMA, e.getKind());
    }

    @Test
    public void unknownKeys_areIgnored() throws Exception {
        String json = codec.toJson(sample());
        json = json.replaceFirst("\\{", "{\"futureField\":\"ignored\",");
        LinkRecord back = codec.fromJson(json, "p");
        assertEquals(sample().getId(), back.getId());
    }

    @Test
    public void nonHttpScheme_rejected() {
        String json = codec.toJson(sample()).replace("https://example.com/resource",
                "javascript:alert(1)");
        MalformedRecordException e = assertThrows(MalformedRecordException.class,
                () -> codec.fromJson(json, "p"));
        assertEquals(MalformedRecordException.Kind.SCHEMA_INVALID, e.getKind());
    }

    @Test
    public void fileScheme_rejected() {
        String json = codec.toJson(sample()).replace("https://example.com/resource",
                "file:///etc/passwd");
        assertThrows(MalformedRecordException.class, () -> codec.fromJson(json, "p"));
    }

    @Test
    public void invalidTimestamp_rejected() {
        String json = codec.toJson(sample()).replace("2026-11-16T10:00:00Z", "yesterday");
        MalformedRecordException e = assertThrows(MalformedRecordException.class,
                () -> codec.fromJson(json, "p"));
        assertEquals(MalformedRecordException.Kind.SCHEMA_INVALID, e.getKind());
    }

    @Test
    public void tooManyTags_rejected() throws Exception {
        LinkRecord r = sample();
        java.util.ArrayList<String> tags = new java.util.ArrayList<>();
        for (int i = 0; i < 25; i++) tags.add("tag" + i);
        String json = codec.toJson(r.withTags(tags));
        MalformedRecordException e = assertThrows(MalformedRecordException.class,
                () -> codec.fromJson(json, "p"));
        assertEquals(MalformedRecordException.Kind.SCHEMA_INVALID, e.getKind());
    }

    @Test
    public void duplicateTags_dedupedCaseInsensitive() throws Exception {
        LinkRecord r = sample().withTags(List.of("Android", "android", "JAVA"));
        String json = codec.toJson(r);
        LinkRecord back = codec.fromJson(json, "p");
        assertEquals(2, back.getTags().size());
    }

    @Test
    public void missingTagsAndPinned_defaultSafely() throws Exception {
        String json = codec.toJson(sample());
        // Strip tags + pinned to emulate an older/partial writer.
        json = json.replaceAll("\"tags\": \\[[^\\]]*\\],", "");
        json = json.replaceAll("\"pinned\": true,", "");
        LinkRecord back = codec.fromJson(json, "p");
        assertTrue(back.getTags().isEmpty());
        assertFalse(back.isPinned());
    }

    @Test
    public void fallbackTitle_usesHost() {
        assertEquals("example.com", LinkRecord.fallbackTitle("https://example.com/a/b"));
        assertEquals("example.com", LinkRecord.fallbackTitle("https://www.example.com/a"));
        assertNotNull(LinkRecord.fallbackTitle("https://8.8.8.8/path"));
    }
}
