package com.linkvault.app.data.json;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import com.linkvault.app.domain.model.FileRecord;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

public class FileCodecTest {

    private FileCodec codec;

    @Before
    public void setUp() {
        codec = new FileCodec();
    }

    @Test
    public void roundtrip_preservesAllFields() throws Exception {
        FileRecord orig = new FileRecord(
                1,
                "5a9d2f44-1c67-4f0a-8f2e-9c3b6d1e7a55",
                "annual_report.pdf",
                "user/files/annual_report_5a9d2f44.pdf",
                "application/pdf",
                1024 * 50,
                "cat_work",
                List.of("finance", "2026"),
                "2026-11-16T10:00:00Z",
                "2026-11-16T10:30:00Z");

        String json = codec.toJson(orig);
        FileRecord read = codec.fromJson(json, "test_file.json");

        assertEquals(orig.getId(), read.getId());
        assertEquals(orig.getDisplayName(), read.getDisplayName());
        assertEquals(orig.getRelativePath(), read.getRelativePath());
        assertEquals(orig.getMimeType(), read.getMimeType());
        assertEquals(orig.getSizeBytes(), read.getSizeBytes());
        assertEquals(orig.getCategoryId(), read.getCategoryId());
        assertEquals(orig.getTags(), read.getTags());
        assertEquals(orig.getCreatedAt(), read.getCreatedAt());
        assertEquals(orig.getUpdatedAt(), read.getUpdatedAt());
    }

    @Test
    public void fromJson_rejectsNegativeSize() {
        String json = "{\n" +
                "  \"schemaVersion\": 1,\n" +
                "  \"id\": \"test-id\",\n" +
                "  \"displayName\": \"doc.pdf\",\n" +
                "  \"relativePath\": \"user/files/doc.pdf\",\n" +
                "  \"sizeBytes\": -100,\n" +
                "  \"categoryId\": \"cat_ungrouped\"\n" +
                "}";

        try {
            codec.fromJson(json, "invalid.json");
            fail("Expected MalformedRecordException for negative size");
        } catch (MalformedRecordException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    @Test
    public void fromJson_rejectsUnsupportedVersion() {
        String json = "{\n" +
                "  \"schemaVersion\": 99,\n" +
                "  \"id\": \"test-id\",\n" +
                "  \"displayName\": \"doc.pdf\",\n" +
                "  \"relativePath\": \"user/files/doc.pdf\",\n" +
                "  \"sizeBytes\": 1024,\n" +
                "  \"categoryId\": \"cat_ungrouped\"\n" +
                "}";

        try {
            codec.fromJson(json, "invalid.json");
            fail("Expected MalformedRecordException for unsupported schemaVersion");
        } catch (MalformedRecordException expected) {
            assertNotNull(expected.getMessage());
        }
    }
}
