package com.linkvault.app.data.saf;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import androidx.annotation.Nullable;

import com.linkvault.app.domain.policy.PathSanitizer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * All SAF access goes through this class (ContentResolver + DocumentsContract).
 * A document-tree URI is NOT a filesystem path — no direct File API is used
 * anywhere. Child document IDs are cached per relative path and invalidated
 * on mutations.
 */
public class DocumentTreeClient {

    public static final String MIME_DIR = DocumentsContract.Document.MIME_TYPE_DIR;
    public static final String MIME_JSON = "application/json";

    public static final class Child {
        public final String documentId;
        public final String displayName;
        public final String mimeType;
        public final long size;

        Child(String documentId, String displayName, String mimeType, long size) {
            this.documentId = documentId;
            this.displayName = displayName;
            this.mimeType = mimeType;
            this.size = size;
        }
    }

    private final Context context;
    private final Uri treeUri;
    private final String rootDocumentId;
    private final ConcurrentHashMap<String, String> pathToDocId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> renameSupport = new ConcurrentHashMap<>();

    public DocumentTreeClient(Context context, Uri treeUri) {
        if (treeUri == null) throw new IllegalArgumentException("treeUri == null");
        this.context = context.getApplicationContext();
        this.treeUri = treeUri;
        this.rootDocumentId = DocumentsContract.getTreeDocumentId(treeUri);
        pathToDocId.put("", rootDocumentId);
    }

    public Uri getTreeUri() {
        return treeUri;
    }

    public Uri uriFor(String documentId) {
        return DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId);
    }

    private ContentResolver resolver() {
        return context.getContentResolver();
    }

    // ---------------------------------------------------------------- reads

    @Nullable
    public String findChildDocumentId(String parentDocId, String displayName) {
        try (Cursor c = resolver().query(
                DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId),
                new String[]{
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE,
                        DocumentsContract.Document.COLUMN_SIZE},
                null, null, null)) {
            if (c == null) return null;
            while (c.moveToNext()) {
                if (displayName.equals(c.getString(1))) {
                    return c.getString(0);
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    public List<Child> listChildren(String parentDocId) {
        try (Cursor c = resolver().query(
                DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId),
                new String[]{
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE,
                        DocumentsContract.Document.COLUMN_SIZE},
                null, null, null)) {
            if (c == null) return null;
            List<Child> out = new ArrayList<>(c.getCount());
            while (c.moveToNext()) {
                out.add(new Child(
                        c.getString(0), c.getString(1), c.getString(2), c.getLong(3)));
            }
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    public boolean documentExists(String documentId) {
        try (Cursor c = resolver().query(
                uriFor(documentId),
                new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID},
                null, null, null)) {
            return c != null && c.moveToFirst();
        } catch (Exception e) {
            return false;
        }
    }

    public byte[] readAll(Uri docUri) throws StorageException {
        try (InputStream in = resolver().openInputStream(docUri)) {
            if (in == null) {
                throw new StorageException(StorageException.Reason.IO_ERROR,
                        "Cannot open stream for " + docUri);
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                bos.write(buf, 0, n);
            }
            return bos.toByteArray();
        } catch (IOException e) {
            throw new StorageException(StorageException.Reason.IO_ERROR,
                    "Read failed: " + e.getMessage(), e);
        }
    }

    public String readUtf8(Uri docUri) throws StorageException {
        return new String(readAll(docUri), StandardCharsets.UTF_8);
    }

    // --------------------------------------------------------------- writes

    /**
     * Truncating write. Tries "rwt" first (guaranteed truncate), falls back
     * to "w" for providers that reject "rwt".
     */
    public void writeAll(Uri docUri, byte[] bytes) throws StorageException {
        IOException last = null;
        for (String mode : new String[]{"rwt", "w"}) {
            try (OutputStream out = resolver().openOutputStream(docUri, mode)) {
                if (out == null) throw new IOException("openOutputStream returned null");
                out.write(bytes);
                out.flush();
                return;
            } catch (IOException e) {
                last = e;
            }
        }
        throw new StorageException(StorageException.Reason.IO_ERROR,
                "Write failed: " + (last == null ? "unknown" : last.getMessage()), last);
    }

    // ------------------------------------------------------------ directory

    /**
     * Walks/creates {@code relativePath} from the tree root and returns the
     * document ID. Safe paths only ({@link PathSanitizer}).
     */
    public String ensureDirectory(String relativePath) throws StorageException {
        if (relativePath == null || relativePath.isEmpty()) return rootDocumentId;
        if (!PathSanitizer.isSafeRelativePath(relativePath)) {
            throw new StorageException(StorageException.Reason.INVALID_NAME,
                    "Unsafe relative path: " + relativePath);
        }
        String cached = pathToDocId.get(relativePath);
        if (cached != null && documentExists(cached)) return cached;

        String currentDocId = rootDocumentId;
        String walked = "";
        for (String segment : relativePath.split("/")) {
            walked = walked.isEmpty() ? segment : walked + "/" + segment;
            String nextCached = pathToDocId.get(walked);
            if (nextCached != null && documentExists(nextCached)) {
                currentDocId = nextCached;
                continue;
            }
            String existing = findChildDocumentId(currentDocId, segment);
            if (existing != null) {
                currentDocId = existing;
            } else {
                try {
                    Uri created = DocumentsContract.createDocument(
                            resolver(),
                            uriFor(currentDocId), MIME_DIR, segment);
                    if (created == null) {
                        throw new StorageException(StorageException.Reason.PROVIDER_UNSUPPORTED,
                                "Provider refused to create directory '" + segment + "'");
                    }
                    currentDocId = DocumentsContract.getDocumentId(created);
                } catch (SecurityException e) {
                    throw new StorageException(StorageException.Reason.NO_PERMISSION,
                            "No write permission on tree", e);
                } catch (java.io.FileNotFoundException e) {
                    throw new StorageException(StorageException.Reason.IO_ERROR,
                            "Cannot create directory '" + segment + "'", e);
                }
            }
            pathToDocId.put(walked, currentDocId);
        }
        pathToDocId.put(relativePath, currentDocId);
        return currentDocId;
    }

    /** Resolves a relative path to a document ID, or null if absent. */
    @Nullable
    public String resolveDocumentId(String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) return rootDocumentId;
        if (!PathSanitizer.isSafeRelativePath(relativePath)) return null;
        String cached = pathToDocId.get(relativePath);
        if (cached != null && documentExists(cached)) return cached;

        String currentDocId = rootDocumentId;
        String walked = "";
        for (String segment : relativePath.split("/")) {
            walked = walked.isEmpty() ? segment : walked + "/" + segment;
            String next = findChildDocumentId(currentDocId, segment);
            if (next == null) return null;
            currentDocId = next;
        }
        pathToDocId.put(relativePath, currentDocId);
        return currentDocId;
    }

    @Nullable
    public Uri resolveUri(String relativePath) {
        String docId = resolveDocumentId(relativePath);
        return docId == null ? null : uriFor(docId);
    }

    // -------------------------------------------------------- create/delete

    public Uri createFile(String parentDocId, String displayName, String mimeType)
            throws StorageException {
        if (!PathSanitizer.isSafeRelativePath(displayName)
                && !isSimpleName(displayName)) {
            throw new StorageException(StorageException.Reason.INVALID_NAME,
                    "Unsafe file name: " + displayName);
        }
        try {
            Uri created = DocumentsContract.createDocument(
                    resolver(), uriFor(parentDocId), mimeType, displayName);
            if (created == null) {
                throw new StorageException(StorageException.Reason.PROVIDER_UNSUPPORTED,
                        "Provider refused to create file '" + displayName + "'");
            }
            return created;
        } catch (SecurityException e) {
            throw new StorageException(StorageException.Reason.NO_PERMISSION,
                    "No write permission", e);
        } catch (java.io.FileNotFoundException e) {
            throw new StorageException(StorageException.Reason.IO_ERROR,
                    "Cannot create file '" + displayName + "'", e);
        }
    }

    private static boolean isSimpleName(String name) {
        // Internal temp names (.tmp_..., .bak_...) are single segments too.
        if (name == null || name.isEmpty() || name.contains("/") || name.contains("\\")) return false;
        return !name.equals(".") && !name.equals("..") && !name.contains("..");
    }

    public boolean delete(String documentId) {
        invalidatePathsContaining(documentId);
        try {
            return DocumentsContract.deleteDocument(resolver(), uriFor(documentId));
        } catch (Exception e) {
            return false;
        }
    }

    public boolean deleteUri(Uri docUri) {
        try {
            boolean ok = DocumentsContract.deleteDocument(resolver(), docUri);
            pathToDocId.clear();
            return ok;
        } catch (Exception e) {
            return false;
        }
    }

    @Nullable
    public String rename(String documentId, String newDisplayName) {
        try {
            Uri newUri = DocumentsContract.renameDocument(
                    resolver(), uriFor(documentId), newDisplayName);
            if (newUri != null) {
                pathToDocId.clear();
                renameSupport.clear();
                return DocumentsContract.getDocumentId(newUri);
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Capability probe: does this provider support rename for this document?
     * Cached per document ID until an invalidation.
     */
    public boolean supportsRename(String documentId) {
        Boolean cached = renameSupport.get(documentId);
        if (cached != null) return cached;
        boolean supported = false;
        try (Cursor c = resolver().query(
                uriFor(documentId),
                new String[]{DocumentsContract.Document.COLUMN_FLAGS},
                null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int flags = c.getInt(0);
                supported = (flags & DocumentsContract.Document.FLAG_SUPPORTS_RENAME) != 0;
            }
        } catch (Exception ignored) {
            supported = false;
        }
        renameSupport.put(documentId, supported);
        return supported;
    }

    public void invalidatePathsContaining(String documentId) {
        pathToDocId.entrySet().removeIf(e -> documentId.equals(e.getValue()));
        renameSupport.remove(documentId);
    }

    public void invalidateAllCaches() {
        pathToDocId.clear();
        pathToDocId.put("", rootDocumentId);
        renameSupport.clear();
    }

    // ---------------------------------------------------------------- misc

    /** App-owned temp/backup files under a directory (prefix-based). */
    public List<Child> listManagedTempFiles(String parentDocId) {
        List<Child> out = new ArrayList<>();
        List<Child> children = listChildren(parentDocId);
        if (children == null) return out;
        for (Child ch : children) {
            if (ch.displayName != null
                    && (ch.displayName.startsWith(".tmp_") || ch.displayName.startsWith(".bak_"))) {
                out.add(ch);
            }
        }
        return out;
    }
}
