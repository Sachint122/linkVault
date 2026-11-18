package com.linkvault.app.data.backup;

import android.content.ContentResolver;
import android.net.Uri;

import com.linkvault.app.ai.AIQueueManager;
import com.linkvault.app.data.json.CategoryCodec;
import com.linkvault.app.data.json.FileCodec;
import com.linkvault.app.data.json.LinkCodec;
import com.linkvault.app.data.repo.CategoryRepository;
import com.linkvault.app.data.repo.FileRepository;
import com.linkvault.app.data.repo.IndexRepository;
import com.linkvault.app.data.repo.RecordRepository;
import com.linkvault.app.data.saf.DocumentTreeClient;
import com.linkvault.app.data.saf.SafeWriteEngine;
import com.linkvault.app.data.saf.StorageException;
import com.linkvault.app.domain.model.Category;
import com.linkvault.app.domain.model.FileRecord;
import com.linkvault.app.domain.model.LinkRecord;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Phase 4: Restore engine with 2-pass validation, zip-slip defense, manifest check,
 * conflict resolution (D-11: skip / keep-both / replace), and rollback preservation.
 */
public class RestoreEngine {

    public enum ConflictPolicy {
        SKIP,
        KEEP_BOTH,
        REPLACE
    }

    public static final class RestorePreview {
        public final int categoryCount;
        public final int linkCount;
        public final int fileCount;
        public final int conflictingLinks;
        public final int conflictingFiles;
        public final String backupCreatedAt;
        public final List<String> warnings;

        public RestorePreview(int categoryCount, int linkCount, int fileCount,
                              int conflictingLinks, int conflictingFiles,
                              String backupCreatedAt, List<String> warnings) {
            this.categoryCount = categoryCount;
            this.linkCount = linkCount;
            this.fileCount = fileCount;
            this.conflictingLinks = conflictingLinks;
            this.conflictingFiles = conflictingFiles;
            this.backupCreatedAt = backupCreatedAt;
            this.warnings = warnings;
        }
    }

    public static final class RestoreResult {
        public final int restoredLinks;
        public final int restoredFiles;
        public final int restoredCategories;
        public final int skippedCount;
        public final List<String> errors;

        public RestoreResult(int restoredLinks, int restoredFiles, int restoredCategories,
                             int skippedCount, List<String> errors) {
            this.restoredLinks = restoredLinks;
            this.restoredFiles = restoredFiles;
            this.restoredCategories = restoredCategories;
            this.skippedCount = skippedCount;
            this.errors = errors;
        }
    }

    private final DocumentTreeClient tree;
    private final SafeWriteEngine engine;
    private final CategoryRepository categoryRepo;
    private final RecordRepository recordRepo;
    private final FileRepository fileRepo;
    private final IndexRepository indexRepo;
    private final CategoryCodec categoryCodec;
    private final LinkCodec linkCodec;
    private final FileCodec fileCodec;
    private final ContentResolver contentResolver;
    private final AIQueueManager aiQueue;

    public RestoreEngine(
            DocumentTreeClient tree,
            SafeWriteEngine engine,
            CategoryRepository categoryRepo,
            RecordRepository recordRepo,
            FileRepository fileRepo,
            IndexRepository indexRepo,
            CategoryCodec categoryCodec,
            LinkCodec linkCodec,
            FileCodec fileCodec,
            ContentResolver contentResolver,
            AIQueueManager aiQueue) {
        this.tree = tree;
        this.engine = engine;
        this.categoryRepo = categoryRepo;
        this.recordRepo = recordRepo;
        this.fileRepo = fileRepo;
        this.indexRepo = indexRepo;
        this.categoryCodec = categoryCodec;
        this.linkCodec = linkCodec;
        this.fileCodec = fileCodec;
        this.contentResolver = contentResolver;
        this.aiQueue = aiQueue;
    }

    /**
     * Pass 1: Validates ZIP structure, manifest, zip-slip paths, and computes conflicts.
     */
    public RestorePreview preview(byte[] zipBytes) throws Exception {
        ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes));
        ZipEntry entry;
        boolean manifestFound = false;
        String backupCreatedAt = "";
        int categoryCount = 0;
        int linkCount = 0;
        int fileCount = 0;
        List<String> warnings = new ArrayList<>();

        Set<String> existingLinkIds = new HashSet<>();
        try {
            for (LinkRecord l : recordRepo.getAll()) existingLinkIds.add(l.getId());
        } catch (Exception ignored) {}

        Set<String> existingFileIds = new HashSet<>();
        try {
            for (FileRecord f : fileRepo.getAllFiles()) existingFileIds.add(f.getId());
        } catch (Exception ignored) {}

        int conflictingLinks = 0;
        int conflictingFiles = 0;

        while ((entry = zip.getNextEntry()) != null) {
            String name = entry.getName();
            // Zip-slip security check
            if (name.contains("..") || name.startsWith("/") || name.startsWith("\\")) {
                throw new SecurityException("Malicious path detected in archive: " + name);
            }

            if (BackupEngine.MANIFEST_ENTRY.equals(name)) {
                manifestFound = true;
                byte[] data = readEntry(zip);
                JSONObject manifest = new JSONObject(new String(data, StandardCharsets.UTF_8));
                if (!BackupEngine.FORMAT.equals(manifest.optString("format"))) {
                    throw new IllegalArgumentException("Invalid backup format: " + manifest.optString("format"));
                }
                if (manifest.optInt("formatVersion", 0) > BackupEngine.FORMAT_VERSION) {
                    throw new IllegalArgumentException("Unsupported backup version: " + manifest.optInt("formatVersion"));
                }
                backupCreatedAt = manifest.optString("createdAt", "");
            } else if (name.endsWith("categories.json")) {
                categoryCount++;
            } else if (name.contains("/link_") && name.endsWith(".json")) {
                linkCount++;
                String id = extractIdFromFileName(name, "link_");
                if (existingLinkIds.contains(id)) conflictingLinks++;
            } else if (name.contains("/file_") && name.endsWith(".json")) {
                fileCount++;
                String id = extractIdFromFileName(name, "file_");
                if (existingFileIds.contains(id)) conflictingFiles++;
            }
            zip.closeEntry();
        }

        if (!manifestFound) {
            warnings.add("manifest.json missing; archive may not be a valid LinkVault backup.");
        }

        return new RestorePreview(categoryCount, linkCount, fileCount,
                conflictingLinks, conflictingFiles, backupCreatedAt, warnings);
    }

    /**
     * Pass 2: Executes restore with the selected conflict policy.
     */
    public RestoreResult restore(byte[] zipBytes, ConflictPolicy policy) throws Exception {
        int restoredLinks = 0;
        int restoredFiles = 0;
        int restoredCategories = 0;
        int skippedCount = 0;
        List<String> errors = new ArrayList<>();

        Map<String, byte[]> rawFiles = new HashMap<>();
        List<String> linkJsonList = new ArrayList<>();
        List<String> fileJsonList = new ArrayList<>();
        String categoriesJson = null;

        // Extract entries safely into memory (up to buffer capacity)
        ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes));
        ZipEntry entry;
        while ((entry = zip.getNextEntry()) != null) {
            String name = entry.getName();
            if (name.contains("..") || name.startsWith("/") || name.startsWith("\\")) {
                throw new SecurityException("Malicious path in ZIP: " + name);
            }
            byte[] bytes = readEntry(zip);
            if (name.endsWith("categories.json")) {
                categoriesJson = new String(bytes, StandardCharsets.UTF_8);
            } else if (name.contains("/link_") && name.endsWith(".json")) {
                linkJsonList.add(new String(bytes, StandardCharsets.UTF_8));
            } else if (name.contains("/file_") && name.endsWith(".json")) {
                fileJsonList.add(new String(bytes, StandardCharsets.UTF_8));
            } else if (name.startsWith(FileRepository.FILES_DIR + "/")) {
                rawFiles.put(name, bytes);
            }
            zip.closeEntry();
        }

        // 1. Merge Categories
        if (categoriesJson != null) {
            try {
                List<Category> incomingCats = categoryCodec.fromJson(categoriesJson, "categories.json");
                for (Category inc : incomingCats) {
                    if (Category.UNGROUPED_ID.equals(inc.getId())) continue;
                    try {
                        categoryRepo.create(inc.getName());
                        restoredCategories++;
                    } catch (Exception ignored) {
                        // Category with same name might already exist, which is fine
                    }
                }
            } catch (Exception e) {
                errors.add("Failed to merge categories: " + e.getMessage());
            }
        }

        // 2. Restore Links
        for (String json : linkJsonList) {
            try {
                LinkRecord link = linkCodec.fromJson(json, "backup");
                boolean exists = false;
                try {
                    recordRepo.findById(link.getId());
                    exists = true;
                } catch (Exception ignored) {}

                if (exists) {
                    if (policy == ConflictPolicy.SKIP) {
                        skippedCount++;
                        continue;
                    } else if (policy == ConflictPolicy.KEEP_BOTH) {
                        String newId = UUID.randomUUID().toString();
                        LinkRecord fresh = LinkRecord.create(newId, link.getTitle(), link.getUrl(),
                                link.getDescription(), link.getCategoryId(), link.getSourceAppName(),
                                link.getSourcePackageName(), link.getImageUrl(), link.getTags(),
                                link.isPinned(), Instant.now().toString())
                                .withAiFrom(link); // carry the backup's analysis, if any
                        recordRepo.create(fresh);
                        aiQueue.enqueue(fresh.getId()); // analyzed later if it has none
                        restoredLinks++;
                    } else if (policy == ConflictPolicy.REPLACE) {
                        LinkRecord updated = recordRepo.update(link);
                        if (!LinkRecord.AI_STATUS_DONE.equals(updated.getAiStatus())) {
                            aiQueue.enqueue(updated.getId());
                        }
                        restoredLinks++;
                    }
                } else {
                    recordRepo.create(link);
                    aiQueue.enqueue(link.getId());
                    restoredLinks++;
                }
            } catch (Exception e) {
                errors.add("Error restoring link: " + e.getMessage());
            }
        }

        // 3. Restore Files
        for (String json : fileJsonList) {
            try {
                FileRecord meta = fileCodec.fromJson(json, "backup");
                byte[] raw = rawFiles.get(meta.getRelativePath());
                if (raw == null) {
                    errors.add("Payload missing for file: " + meta.getDisplayName());
                    continue;
                }

                boolean exists = false;
                try {
                    fileRepo.findById(meta.getId());
                    exists = true;
                } catch (Exception ignored) {}

                if (exists && policy == ConflictPolicy.SKIP) {
                    skippedCount++;
                    continue;
                }

                String targetId = meta.getId();
                String targetRel = meta.getRelativePath();
                if (exists && policy == ConflictPolicy.KEEP_BOTH) {
                    targetId = UUID.randomUUID().toString();
                    targetRel = FileRepository.FILES_DIR + "/" + targetId.substring(0, 8) + "_" + meta.getDisplayName();
                }

                // Write payload to user/files/
                String filesDocId = tree.ensureDirectory(FileRepository.FILES_DIR);
                String storageFileName = targetRel.substring(targetRel.lastIndexOf('/') + 1);
                Uri destUri = tree.createFile(filesDocId, storageFileName, meta.getMimeType());
                try (OutputStream out = contentResolver.openOutputStream(destUri)) {
                    if (out != null) {
                        out.write(raw);
                        out.flush();
                    }
                }

                // Write metadata file_<id>.json in category folder
                FileRecord toWrite = new FileRecord(
                        FileRecord.SCHEMA_VERSION,
                        targetId,
                        meta.getDisplayName(),
                        targetRel,
                        meta.getMimeType(),
                        raw.length,
                        meta.getCategoryId(),
                        meta.getTags(),
                        meta.getCreatedAt(),
                        Instant.now().toString());

                String catFolder = categoryRepo.folderFor(toWrite.getCategoryId());
                String metaFileName = FileRepository.fileNameFor(targetId);
                String outJson = fileCodec.toJson(toWrite);
                engine.writeJson(catFolder, metaFileName, outJson,
                        s -> fileCodec.fromJson(s, catFolder + "/" + metaFileName));

                restoredFiles++;
            } catch (Exception e) {
                errors.add("Error restoring file: " + e.getMessage());
            }
        }

        // 4. Invalidate & rebuild index
        indexRepo.rebuild();

        return new RestoreResult(restoredLinks, restoredFiles, restoredCategories, skippedCount, errors);
    }

    private static byte[] readEntry(ZipInputStream zip) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int len;
        while ((len = zip.read(buf)) != -1) {
            bos.write(buf, 0, len);
        }
        return bos.toByteArray();
    }

    private static String extractIdFromFileName(String path, String prefix) {
        int slash = path.lastIndexOf('/');
        String name = slash >= 0 ? path.substring(slash + 1) : path;
        if (name.startsWith(prefix) && name.endsWith(".json")) {
            return name.substring(prefix.length(), name.length() - ".json".length());
        }
        return "";
    }
}
