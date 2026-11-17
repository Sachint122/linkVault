package com.linkvault.app.data.json;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.linkvault.app.domain.model.Category;

import org.junit.Test;

import java.util.List;

public class CategoryCodecTest {

    private final CategoryCodec codec = new CategoryCodec();

    @Test
    public void roundTrip_preservesCategories() throws Exception {
        List<Category> cats = List.of(
                Category.ungrouped("2026-11-16T09:00:00Z"),
                new Category("cat_ab12", "Development",
                        "2026-11-16T10:00:00Z", "2026-11-16T10:00:00Z", 1, false));
        String json = codec.toJson(cats, "2026-11-16T11:00:00Z");
        List<Category> back = codec.fromJson(json, "user/categories/categories.json");

        assertEquals(2, back.size());
        assertEquals(Category.UNGROUPED_ID, back.get(0).getId());
        assertTrue(back.get(0).isSystem());
        assertEquals("Development", back.get(1).getName());
        assertEquals(1, back.get(1).getSortOrder());
    }

    @Test
    public void malformedJson_throws() {
        MalformedRecordException e = assertThrows(MalformedRecordException.class,
                () -> codec.fromJson("oops", "p"));
        assertEquals(MalformedRecordException.Kind.MALFORMED_JSON, e.getKind());
    }

    @Test
    public void missingCategoriesArray_throwsSchemaInvalid() {
        MalformedRecordException e = assertThrows(MalformedRecordException.class,
                () -> codec.fromJson("{\"schemaVersion\":1}", "p"));
        assertEquals(MalformedRecordException.Kind.SCHEMA_INVALID, e.getKind());
    }

    @Test
    public void newerSchema_rejected() {
        MalformedRecordException e = assertThrows(MalformedRecordException.class,
                () -> codec.fromJson("{\"schemaVersion\":9,\"categories\":[]}", "p"));
        assertEquals(MalformedRecordException.Kind.NEWER_SCHEMA, e.getKind());
    }

    @Test
    public void categoryWithoutIdRejected() {
        String json = "{\"schemaVersion\":1,\"categories\":[{\"name\":\"x\","
                + "\"createdAt\":\"2026-11-16T10:00:00Z\","
                + "\"updatedAt\":\"2026-11-16T10:00:00Z\"}]}";
        MalformedRecordException e = assertThrows(MalformedRecordException.class,
                () -> codec.fromJson(json, "p"));
        assertEquals(MalformedRecordException.Kind.SCHEMA_INVALID, e.getKind());
    }

    @Test
    public void folderName_isStableId() {
        Category c = new Category("cat_ab12", "My Folder",
                "2026-11-16T10:00:00Z", "2026-11-16T10:00:00Z", 1, false);
        // Rename never changes the folder (D-06).
        Category renamed = c.withName("New Name", "2026-11-16T12:00:00Z");
        assertEquals(c.folderName(), renamed.folderName());
        assertEquals("cat_ab12", renamed.folderName());
    }
}
