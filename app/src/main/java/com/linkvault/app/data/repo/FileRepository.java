package com.linkvault.app.data.repo;

import android.content.ContentResolver;
import android.net.Uri;
import androidx.annotation.Nullable;

import com.linkvault.app.data.json.FileCodec;
import com.linkvault.app.data.json.MalformedRecordException;
import com.linkvault.app.data.saf.DocumentTreeClient;
import com.linkvault.app.data.saf.SafeWriteEngine;
import com.linkvault.app.data.saf.StorageException;
import com.linkvault.app.domain.model.Category;
import com.linkvault.app.domain.model.FileRecord;
import com.linkvault.app.domain.policy.PathSanitizer;

import java.io.InputStream;
import java.io.OutputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * File management (FR-06): copy-only import into user/files/, metadata in
 * user/categories/<cat>/file_<id>.json, 100 MB hard cap (D-10).
 */
public class FileRepository {

    public static final String FILES_DIR = "user/files";
    public static final long MAX_FILE_BYTES = 100 * 1024 * 1024L; // 100 MB (D-10)

    private final DocumentTreeClient tree;
    private final SafeWriteEngine engine;
    private final FileCodec codec;
    private final CategoryRepository categories;
    private final ContentResolver contentResolver;

    public FileRepository(
            DocumentTreeClient tree,
            SafeWriteEngine engine,
            FileCodec codec,
            CategoryRepository categories,
            ContentResolver contentResolver) {
        this.tree = tree;
        this.engine = engine;
        this.codec = codec;
        this.categories = categories;
        this.contentResolver = contentResolver;
    }

    public static String fileNameFor(String recordId) {
        return "file_" + recordId + ".json";
    }

    /**
     * Imports a file from a source URI by copying bytes into user/files/.
     * Leaves the original untouched (FR-06 invariant).
     */
    public synchronized FileRecord importFile(
            Uri sourceUri,
            String originalDisplayName,
            @Nullable String mimeType,
            String categoryId,
            List<String> tags) throws StorageException {

        String recordId = UUID.randomUUID().toString();
        String safeOriginal = PathSanitizer.sanitizeFileName(originalDisplayName);
        if (safeOriginal.isEmpty()) safeOriginal = "file_" + recordId.substring(0, 8);

        // Collision-safe filename: <base>_<shortId>.<ext>
        String ext = "";
        int dot = safeOriginal.lastIndexOf('.');
        String baseName = safeOriginal;
        if (dot > 0) {
            baseName = safeOriginal.substring(0, dot);
            ext = safeOriginal.substring(dot);
        }
        String safeStorageName = PathSanitizer.sanitizeFileName(baseName)
                + "_" + recordId.substring(0, 8) + ext;
        String relPath = FILES_DIR + "/" + safeStorageName;

        // Ensure user/files directory exists in tree
        String filesDocId = tree.ensureDirectory(FILES_DIR);

        // Stream copy bytes from sourceUri
        String actualMime = (mimeType == null || mimeType.equals("*/*"))
                ? "application/octet-stream" : mimeType;
        Uri destDocUri = tree.createFile(filesDocId, safeStorageName, actualMime);

        long bytesCopied = 0;
        try (InputStream in = contentResolver.openInputStream(sourceUri);
             OutputStream out = contentResolver.openOutputStream(destDocUri)) {
            if (in == null || out == null) {
                tree.deleteUri(destDocUri);
                throw new StorageException(StorageException.Reason.IO_ERROR,
                        "Cannot open streams for file copy");
            }

            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                bytesCopied += read;
                if (bytesCopied > MAX_FILE_BYTES) {
                    tree.deleteUri(destDocUri);
                    throw new StorageException(StorageException.Reason.VALIDATION_FAILED,
                            "File exceeds 100 MB limit (D-10)");
                }
                out.write(buffer, 0, read);
            }
            out.flush();
        } catch (StorageException e) {
            throw e;
        } catch (Exception e) {
            tree.deleteUri(destDocUri);
            throw new StorageException(StorageException.Reason.IO_ERROR,
                    "Error copying file: " + e.getMessage(), e);
        }

        // Persist metadata file_<id>.json in category folder
        String now = Instant.now().toString();
        FileRecord record = new FileRecord(
                FileRecord.SCHEMA_VERSION,
                recordId,
                originalDisplayName,
                relPath,
                actualMime,
                bytesCopied,
                categoryId,
                tags,
                now,
                now);

        String catFolder = categories.folderFor(categoryId);
        String metaFileName = fileNameFor(recordId);
        String json = codec.toJson(record);
        engine.writeJson(catFolder, metaFileName, json,
                s -> codec.fromJson(s, catFolder + "/" + metaFileName));

        return record;
    }

    public synchronized FileRecord findById(String recordId) throws StorageException {
        String wanted = fileNameFor(recordId);
        for (String folder : listAllRecordFolders()) {
            String parentDocId = tree.resolveDocumentId(folder);
            if (parentDocId == null) continue;
            String childId = tree.findChildDocumentId(parentDocId, wanted);
            if (childId != null) {
                String rel = folder + "/" + wanted;
                Uri uri = tree.resolveUri(rel);
                if (uri != null) {
                    try {
                        return codec.fromJson(tree.readUtf8(uri), rel);
                    } catch (MalformedRecordException e) {
                        throw new StorageException(StorageException.Reason.VALIDATION_FAILED, e.getMessage(), e);
                    }
                }
            }
        }
        throw new StorageException(StorageException.Reason.NOT_FOUND, "File record not found: " + recordId);
    }

    public synchronized List<FileRecord> getAllFiles() {
        List<FileRecord> out = new ArrayList<>();
        for (String folder : listAllRecordFolders()) {
            String parentDocId = tree.resolveDocumentId(folder);
            if (parentDocId == null) continue;
            List<DocumentTreeClient.Child> children = tree.listChildren(parentDocId);
            if (children == null) continue;
            for (DocumentTreeClient.Child ch : children) {
                if (ch.displayName == null) continue;
                if (!ch.displayName.startsWith("file_") || !ch.displayName.endsWith(".json")) continue;
                String rel = folder + "/" + ch.displayName;
                Uri uri = tree.resolveUri(rel);
                if (uri == null) continue;
                try {
                    out.add(codec.fromJson(tree.readUtf8(uri), rel));
                } catch (Exception ignored) {}
            }
        }
        return out;
    }

    public synchronized FileRecord moveTo(String recordId, String newCategoryId) throws StorageException {
        FileRecord existing = findById(recordId);
        if (existing.getCategoryId().equals(newCategoryId)) return existing;
        FileRecord moved = existing.withCategoryId(newCategoryId)
                .withUpdatedAt(Instant.now().toString());

        String oldFolder = categories.folderFor(existing.getCategoryId());
        String newFolder = categories.folderFor(newCategoryId);
        String metaFileName = fileNameFor(recordId);

        // Write new record in new category
        String json = codec.toJson(moved);
        engine.writeJson(newFolder, metaFileName, json,
                s -> codec.fromJson(s, newFolder + "/" + metaFileName));

        // Delete old record
        deleteFileInFolder(oldFolder, metaFileName);
        return moved;
    }

    public synchronized void delete(String recordId) throws StorageException {
        FileRecord existing = findById(recordId);

        // Delete binary file in user/files/
        Uri fileBytesUri = tree.resolveUri(existing.getRelativePath());
        if (fileBytesUri != null) {
            tree.deleteUri(fileBytesUri);
        }

        // Delete metadata in category folder
        String folder = categories.folderFor(existing.getCategoryId());
        deleteFileInFolder(folder, fileNameFor(recordId));
    }

    @Nullable
    public Uri resolveFileUri(FileRecord record) {
        return tree.resolveUri(record.getRelativePath());
    }

    private List<String> listAllRecordFolders() {
        List<String> folders = new ArrayList<>();
        try {
            for (Category c : categories.getAll()) {
                folders.add(categories.folderFor(c.getId()));
            }
        } catch (StorageException ignored) {}
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

    private void deleteFileInFolder(String folder, String fileName) throws StorageException {
        String parentDocId = tree.resolveDocumentId(folder);
        if (parentDocId == null) return;
        String childId = tree.findChildDocumentId(parentDocId, fileName);
        if (childId != null) tree.delete(childId);
    }
}
