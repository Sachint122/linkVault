package com.linkvault.app.data.backup;

import android.content.ContentResolver;
import android.net.Uri;

import com.linkvault.app.data.json.CategoryCodec;
import com.linkvault.app.data.json.FileCodec;
import com.linkvault.app.data.json.LinkCodec;
import com.linkvault.app.data.repo.CategoryRepository;
import com.linkvault.app.data.repo.FileRepository;
import com.linkvault.app.data.repo.RecordRepository;
import com.linkvault.app.data.saf.DocumentTreeClient;
import com.linkvault.app.data.saf.StorageException;
import com.linkvault.app.domain.model.Category;
import com.linkvault.app.domain.model.FileRecord;
import com.linkvault.app.domain.model.LinkRecord;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Phase 4: Backup engine. Exports categories, links, file metadata, and raw file
 * bytes into a verified ZIP archive with manifest.json (FR-07, D-11).
 */
public class BackupEngine {

    public static final String FORMAT = "linkvault-backup";
    public static final int FORMAT_VERSION = 1;
    public static final String APP_VERSION = "1.0.0";
    public static final String MANIFEST_ENTRY = "manifest.json";

    public static final class BackupSummary {
        public final int categoryCount;
        public final int linkCount;
        public final int fileCount;
        public final long totalBytesWritten;
        public final List<String> omissions;

        public BackupSummary(int categoryCount, int linkCount, int fileCount,
                             long totalBytesWritten, List<String> omissions) {
            this.categoryCount = categoryCount;
            this.linkCount = linkCount;
            this.fileCount = fileCount;
            this.totalBytesWritten = totalBytesWritten;
            this.omissions = omissions;
        }
    }

    private final DocumentTreeClient tree;
    private final CategoryRepository categoryRepo;
    private final RecordRepository recordRepo;
    private final FileRepository fileRepo;
    private final CategoryCodec categoryCodec;
    private final LinkCodec linkCodec;
    private final FileCodec fileCodec;
    private final ContentResolver contentResolver;

    public BackupEngine(
            DocumentTreeClient tree,
            CategoryRepository categoryRepo,
            RecordRepository recordRepo,
            FileRepository fileRepo,
            CategoryCodec categoryCodec,
            LinkCodec linkCodec,
            FileCodec fileCodec,
            ContentResolver contentResolver) {
        this.tree = tree;
        this.categoryRepo = categoryRepo;
        this.recordRepo = recordRepo;
        this.fileRepo = fileRepo;
        this.categoryCodec = categoryCodec;
        this.linkCodec = linkCodec;
        this.fileCodec = fileCodec;
        this.contentResolver = contentResolver;
    }

    /**
     * Exports entire vault to the given output stream (e.g. from SAF CreateDocument).
     */
    public BackupSummary exportBackup(OutputStream out) throws Exception {
        List<Category> categories = categoryRepo.getAll();
        List<LinkRecord> links = recordRepo.getAll();
        List<FileRecord> files = fileRepo.getAllFiles();
        List<String> omissions = new ArrayList<>();

        long totalFileBytes = 0;
        for (FileRecord f : files) {
            totalFileBytes += f.getSizeBytes();
        }

        // Build manifest
        JSONObject manifest = new JSONObject();
        manifest.put("format", FORMAT);
        manifest.put("formatVersion", FORMAT_VERSION);
        manifest.put("appVersion", APP_VERSION);
        manifest.put("createdAt", Instant.now().toString());

        JSONObject counts = new JSONObject();
        counts.put("categories", categories.size());
        counts.put("links", links.size());
        counts.put("files", files.size());
        counts.put("fileBytes", totalFileBytes);
        manifest.put("counts", counts);

        JSONArray omissionsArr = new JSONArray();
        manifest.put("omissions", omissionsArr);

        ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(out));
        long bytesWritten = 0;

        // 1. Write manifest.json first
        byte[] manifestBytes = manifest.toString(2).getBytes(StandardCharsets.UTF_8);
        ZipEntry mEntry = new ZipEntry(MANIFEST_ENTRY);
        zip.putNextEntry(mEntry);
        zip.write(manifestBytes);
        zip.closeEntry();
        bytesWritten += manifestBytes.length;

        // 2. Write categories.json
        String categoriesJson = categoryCodec.toJson(categories, Instant.now().toString());
        byte[] catBytes = categoriesJson.getBytes(StandardCharsets.UTF_8);
        ZipEntry cEntry = new ZipEntry("user/categories/categories.json");
        zip.putNextEntry(cEntry);
        zip.write(catBytes);
        zip.closeEntry();
        bytesWritten += catBytes.length;

        // 3. Write link records
        for (LinkRecord l : links) {
            String folder = categoryRepo.folderFor(l.getCategoryId());
            String entryPath = folder + "/" + RecordRepository.fileNameFor(l.getId());
            byte[] lBytes = linkCodec.toJson(l).getBytes(StandardCharsets.UTF_8);
            ZipEntry le = new ZipEntry(entryPath);
            zip.putNextEntry(le);
            zip.write(lBytes);
            zip.closeEntry();
            bytesWritten += lBytes.length;
        }

        // 4. Write file records and binary contents
        for (FileRecord f : files) {
            String folder = categoryRepo.folderFor(f.getCategoryId());
            String metaPath = folder + "/" + FileRepository.fileNameFor(f.getId());
            byte[] fBytes = fileCodec.toJson(f).getBytes(StandardCharsets.UTF_8);
            ZipEntry fe = new ZipEntry(metaPath);
            zip.putNextEntry(fe);
            zip.write(fBytes);
            zip.closeEntry();
            bytesWritten += fBytes.length;

            // Raw bytes
            Uri bytesUri = tree.resolveUri(f.getRelativePath());
            if (bytesUri != null) {
                ZipEntry be = new ZipEntry(f.getRelativePath());
                zip.putNextEntry(be);
                try (InputStream in = contentResolver.openInputStream(bytesUri)) {
                    if (in != null) {
                        byte[] buffer = new byte[32 * 1024];
                        int read;
                        while ((read = in.read(buffer)) != -1) {
                            zip.write(buffer, 0, read);
                            bytesWritten += read;
                        }
                    }
                } catch (Exception e) {
                    omissions.add("File payload unreadable: " + f.getDisplayName());
                }
                zip.closeEntry();
            } else {
                omissions.add("Missing file payload for: " + f.getDisplayName());
            }
        }

        zip.finish();
        zip.flush();

        return new BackupSummary(categories.size(), links.size(), files.size(), bytesWritten, omissions);
    }
}
