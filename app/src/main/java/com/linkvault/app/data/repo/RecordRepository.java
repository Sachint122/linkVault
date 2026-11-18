package com.linkvault.app.data.repo;

import androidx.annotation.Nullable;

import com.linkvault.app.data.json.LinkCodec;
import com.linkvault.app.data.json.MalformedRecordException;
import com.linkvault.app.data.saf.DocumentTreeClient;
import com.linkvault.app.data.saf.SafeWriteEngine;
import com.linkvault.app.data.saf.StorageException;
import com.linkvault.app.domain.model.Category;
import com.linkvault.app.domain.model.LinkRecord;
import com.linkvault.app.domain.model.RecordIssue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * CRUD for link_*.json records — one file per record, inside the folder of
 * their category (folder name = stable category ID). Malformed records are
 * collected as {@link RecordIssue}s, never deleted or overwritten.
 */
public class RecordRepository {

    private final DocumentTreeClient tree;
    private final SafeWriteEngine engine;
    private final LinkCodec codec;
    private final CategoryRepository categories;

    public RecordRepository(DocumentTreeClient tree, SafeWriteEngine engine,
                            LinkCodec codec, CategoryRepository categories) {
        this.tree = tree;
        this.engine = engine;
        this.codec = codec;
        this.categories = categories;
    }

    public static String fileNameFor(String recordId) {
        return "link_" + recordId + ".json";
    }

    // ----------------------------------------------------------------- CRUD

    public synchronized void create(LinkRecord record) throws StorageException {
        String folder = categories.folderFor(record.getCategoryId());
        String fileName = fileNameFor(record.getId());
        String json = codec.toJson(record);
        engine.writeJson(folder, fileName, json,
                s -> codec.fromJson(s, folder + "/" + fileName));
    }

    /** Reads a record when its category is known. */
    public synchronized LinkRecord read(String recordId, String categoryId)
            throws StorageException {
        String folder = categories.folderFor(categoryId);
        String rel = folder + "/" + fileNameFor(recordId);
        android.net.Uri uri = tree.resolveUri(rel);
        if (uri == null) {
            throw new StorageException(StorageException.Reason.NOT_FOUND,
                    "Record not found: " + rel);
        }
        return readUri(uri, rel);
    }

    /** Scans category folders for the record (category unknown). */
    public synchronized LinkRecord findById(String recordId)
            throws StorageException {
        String wanted = fileNameFor(recordId);
        String foundRel = findRecordPath(wanted);
        if (foundRel == null) {
            throw new StorageException(StorageException.Reason.NOT_FOUND,
                    "Record not found: " + recordId);
        }
        android.net.Uri uri = tree.resolveUri(foundRel);
        if (uri == null) {
            throw new StorageException(StorageException.Reason.NOT_FOUND,
                    "Record not found: " + recordId);
        }
        return readUri(uri, foundRel);
    }

    /**
     * Updates metadata. Bumps updatedAt. Refuses NEWER_SCHEMA records so a
     * future-version file is never clobbered.
     */
    public synchronized LinkRecord update(LinkRecord record) throws StorageException {
        LinkRecord existing = findById(record.getId());
        String now = nowIso();
        LinkRecord updated = new LinkRecord(
                existing.getSchemaVersion(),
                existing.getId(),
                record.getTitle(),
                record.getUrl(),
                record.getDescription(),
                record.getCategoryId(),
                record.getSourceAppName(),
                record.getSourcePackageName(),
                record.getImageUrl(),
                record.getTags(),
                record.isPinned(),
                existing.getCreatedAt(),
                now,
                existing.getCheckedAt(),
                existing.getHttpStatus());
        // Same category → overwrite in place; different category → write new + delete old.
        String oldFolder = categories.folderFor(existing.getCategoryId());
        String newFolder = categories.folderFor(updated.getCategoryId());
        if (oldFolder.equals(newFolder)) {
            writeInto(newFolder, updated);
        } else {
            writeInto(newFolder, updated);
            deleteFile(oldFolder, fileNameFor(updated.getId()));
        }
        return updated;
    }

    /** Persists a dead-link check result. Does not bump updatedAt. */
    public synchronized LinkRecord markChecked(String recordId, int httpStatus)
            throws StorageException {
        LinkRecord existing = findById(recordId);
        LinkRecord checked = existing.withCheck(nowIso(), httpStatus);
        writeInto(categories.folderFor(existing.getCategoryId()), checked);
        return checked;
    }

    public synchronized LinkRecord moveTo(String recordId, String newCategoryId)
            throws StorageException {
        LinkRecord existing = findById(recordId);
        if (existing.getCategoryId().equals(newCategoryId)) return existing;
        LinkRecord moved = existing.withCategoryId(newCategoryId).withUpdatedAt(nowIso());
        String oldFolder = categories.folderFor(existing.getCategoryId());
        String newFolder = categories.folderFor(newCategoryId);
        writeInto(newFolder, moved);
        deleteFile(oldFolder, fileNameFor(recordId));
        return moved;
    }

    public synchronized void delete(String recordId) throws StorageException {
        String rel = findRecordPath(fileNameFor(recordId));
        if (rel == null) {
            throw new StorageException(StorageException.Reason.NOT_FOUND,
                    "Record not found: " + recordId);
        }
        int slash = rel.lastIndexOf('/');
        String folder = slash < 0 ? "" : rel.substring(0, slash);
        deleteFile(folder, fileNameFor(recordId));
    }

    public synchronized int countIn(String categoryId) {
        String folder = categories.folderFor(categoryId);
        String parentDocId = tree.resolveDocumentId(folder);
        if (parentDocId == null) return 0;
        List<DocumentTreeClient.Child> children = tree.listChildren(parentDocId);
        if (children == null) return 0;
        int n = 0;
        for (DocumentTreeClient.Child ch : children) {
            if (ch.displayName != null && ch.displayName.startsWith("link_")
                    && ch.displayName.endsWith(".json")) n++;
        }
        return n;
    }

    // ---------------------------------------------------------------- scan

    /**
     * Reads every parseable link record across all category folders.
     * Malformed records are skipped here (reported via {@link #scanIssues()}).
     * Phase 2/3 scale: incremental loading builds on this.
     */
    public synchronized List<LinkRecord> getAllLinks() {
        return getAll();
    }

    public synchronized List<LinkRecord> getAll() {
        List<LinkRecord> out = new ArrayList<>();
        for (String folder : listAllRecordFolders()) {
            String parentDocId = tree.resolveDocumentId(folder);
            if (parentDocId == null) continue;
            List<DocumentTreeClient.Child> children = tree.listChildren(parentDocId);
            if (children == null) continue;
            for (DocumentTreeClient.Child ch : children) {
                if (ch.displayName == null) continue;
                if (!ch.displayName.startsWith("link_") || !ch.displayName.endsWith(".json")) {
                    continue;
                }
                String rel = folder + "/" + ch.displayName;
                android.net.Uri uri = tree.resolveUri(rel);
                if (uri == null) continue;
                try {
                    String json = tree.readUtf8(uri);
                    out.add(codec.fromJson(json, rel));
                } catch (Exception ignored) {
                    // Malformed/unreadable — surfaced by scanIssues(), not here.
                }
            }
        }
        return out;
    }

    /**
     * Duplicate detection (D-07): returns records whose URL normalizes to the
     * same comparison form as {@code candidateUrl}. Never blocks saving.
     */
    public synchronized List<LinkRecord> findDuplicates(String candidateUrl) {
        String normalized = com.linkvault.app.domain.policy.UrlPolicy.normalizeForCompare(candidateUrl);
        if (normalized.isEmpty()) return new ArrayList<>();
        List<LinkRecord> matches = new ArrayList<>();
        for (LinkRecord r : getAllLinks()) {
            if (normalized.equals(
                    com.linkvault.app.domain.policy.UrlPolicy.normalizeForCompare(r.getUrl()))) {
                matches.add(r);
            }
        }
        return matches;
    }


    /**
     * Walks every category folder on disk (union of categories.json entries
     * and folders actually present), parses each link_*.json, and reports
     * problems. Malformed files stay untouched.
     */
    public synchronized List<RecordIssue> scanIssues() {
        List<RecordIssue> issues = new ArrayList<>();
        for (String folder : listAllRecordFolders()) {
            String parentDocId = tree.resolveDocumentId(folder);
            if (parentDocId == null) continue;
            List<DocumentTreeClient.Child> children = tree.listChildren(parentDocId);
            if (children == null) {
                issues.add(new RecordIssue(RecordIssue.Kind.UNREADABLE, folder,
                        "Cannot list directory"));
                continue;
            }
            for (DocumentTreeClient.Child ch : children) {
                if (ch.displayName == null) continue;
                if (!ch.displayName.startsWith("link_") || !ch.displayName.endsWith(".json")) {
                    continue;
                }
                String rel = folder + "/" + ch.displayName;
                android.net.Uri uri = tree.resolveUri(rel);
                if (uri == null) {
                    issues.add(new RecordIssue(RecordIssue.Kind.UNREADABLE, rel,
                            "File disappeared during scan"));
                    continue;
                }
                try {
                    String json = tree.readUtf8(uri);
                    codec.fromJson(json, rel);
                } catch (MalformedRecordException e) {
                    issues.add(e.toIssue());
                } catch (StorageException e) {
                    issues.add(new RecordIssue(RecordIssue.Kind.UNREADABLE, rel,
                            e.getMessage()));
                } catch (Exception e) {
                    issues.add(new RecordIssue(RecordIssue.Kind.MALFORMED_JSON, rel,
                            String.valueOf(e.getMessage())));
                }
            }
        }
        return issues;
    }

    // -------------------------------------------------------------- helpers

    private List<String> listAllRecordFolders() {
        List<String> folders = new ArrayList<>();
        try {
            for (Category c : categories.getAll()) {
                folders.add(categories.folderFor(c.getId()));
            }
        } catch (StorageException ignored) {
            // categories.json unreadable — still scan what exists on disk below.
        }
        // Also include folders present on disk but missing from categories.json.
        String rootDocId = tree.resolveDocumentId(CategoryRepository.REL_DIR);
        if (rootDocId != null) {
            List<DocumentTreeClient.Child> children = tree.listChildren(rootDocId);
            if (children != null) {
                for (DocumentTreeClient.Child ch : children) {
                    if (DocumentTreeClient.MIME_DIR.equals(ch.mimeType)) {
                        String rel = CategoryRepository.REL_DIR + "/" + ch.displayName;
                        if (!folders.contains(rel)) folders.add(rel);
                    }
                }
            }
        }
        return folders;
    }

    @Nullable
    private String findRecordPath(String fileName) {
        for (String folder : listAllRecordFolders()) {
            String parentDocId = tree.resolveDocumentId(folder);
            if (parentDocId == null) continue;
            String childId = tree.findChildDocumentId(parentDocId, fileName);
            if (childId != null) return folder + "/" + fileName;
        }
        return null;
    }

    private void writeInto(String folder, LinkRecord record) throws StorageException {
        String fileName = fileNameFor(record.getId());
        String json = codec.toJson(record);
        engine.writeJson(folder, fileName, json,
                s -> codec.fromJson(s, folder + "/" + fileName));
    }

    private void deleteFile(String folder, String fileName) throws StorageException {
        String parentDocId = tree.resolveDocumentId(folder);
        if (parentDocId == null) {
            throw new StorageException(StorageException.Reason.NOT_FOUND,
                    "Folder missing: " + folder);
        }
        String childId = tree.findChildDocumentId(parentDocId, fileName);
        if (childId == null) {
            throw new StorageException(StorageException.Reason.NOT_FOUND,
                    "File missing: " + folder + "/" + fileName);
        }
        if (!tree.delete(childId)) {
            throw new StorageException(StorageException.Reason.PROVIDER_UNSUPPORTED,
                    "Provider refused to delete " + fileName);
        }
    }

    private LinkRecord readUri(android.net.Uri uri, String rel) throws StorageException {
        String json = tree.readUtf8(uri);
        try {
            return codec.fromJson(json, rel);
        } catch (MalformedRecordException e) {
            throw new StorageException(StorageException.Reason.VALIDATION_FAILED,
                    e.getMessage(), e);
        }
    }

    private static String nowIso() {
        return Instant.now().toString();
    }

    public static String newId() {
        return UUID.randomUUID().toString();
    }
}
