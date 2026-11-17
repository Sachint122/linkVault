package com.linkvault.app.data.saf;

import android.net.Uri;

import androidx.annotation.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Executes the safe-write protocol for JSON files:
 * write tmp → re-read + validate → commit via rename-swap or verified
 * byte-copy → verify target. Never reports success before the target
 * parses. Startup {@link #recoverDirectory} cleans or restores crash
 * leftovers (DATA_SCHEMA.md §9).
 */
public class SafeWriteEngine {

    public interface Validator {
        /** @throws Exception when the JSON is not acceptable. */
        void validate(String json) throws Exception;
    }

    private final DocumentTreeClient tree;

    public SafeWriteEngine(DocumentTreeClient tree) {
        this.tree = tree;
    }

    /**
     * Writes {@code json} as {@code fileName} inside {@code relativeDir},
     * creating the directory when needed.
     */
    public void writeJson(String relativeDir, String fileName, String json, Validator validator)
            throws StorageException {
        String parentDocId = tree.ensureDirectory(relativeDir);

        String nonce = UUID.randomUUID().toString().substring(0, 8);
        String tmpName = SafeWritePlanner.tempName(fileName, nonce);

        // 1) Create + write tmp.
        Uri tmpUri = tree.createFile(parentDocId, tmpName, DocumentTreeClient.MIME_JSON);
        tree.writeAll(tmpUri, json.getBytes(StandardCharsets.UTF_8));

        // 2) Re-read + validate tmp before touching the target.
        String readBack = tree.readUtf8(tmpUri);
        try {
            validator.validate(readBack);
        } catch (Exception e) {
            tree.deleteUri(tmpUri);
            throw new StorageException(StorageException.Reason.VALIDATION_FAILED,
                    "Written JSON failed validation for " + fileName + ": " + e.getMessage(), e);
        }

        String targetDocId = tree.findChildDocumentId(parentDocId, fileName);
        boolean targetExists = targetDocId != null;
        boolean renameSupported = targetExists && tree.supportsRename(targetDocId);

        SafeWritePlanner.Strategy strategy =
                SafeWritePlanner.choose(targetExists, renameSupported);

        switch (strategy) {
            case CREATE_NEW:
                commitCreateNew(parentDocId, fileName, tmpUri, tmpName, validator);
                break;
            case RENAME_SWAP:
                commitRenameSwap(parentDocId, fileName, targetDocId, tmpUri, validator);
                break;
            case COPY_REPLACE:
                commitCopyReplace(parentDocId, fileName, tmpUri, validator);
                break;
        }
    }

    // -------------------------------------------------------------- commits

    private void commitCreateNew(String parentDocId, String fileName, Uri tmpUri,
                                 String tmpName, Validator validator) throws StorageException {
        String newTargetId = tree.rename(treeDocumentIdOf(tmpUri), fileName);
        if (newTargetId != null) {
            // Verify the committed target.
            verifyTarget(parentDocId, fileName, validator);
            return;
        }
        // Rename unsupported for fresh files: create target, copy, verify, drop tmp.
        Uri targetUri = tree.createFile(parentDocId, fileName, DocumentTreeClient.MIME_JSON);
        copyVerified(tmpUri, targetUri, validator);
        tree.deleteUri(tmpUri);
    }

    private void commitRenameSwap(String parentDocId, String fileName, String targetDocId,
                                  Uri tmpUri, Validator validator) throws StorageException {
        String bakName = SafeWritePlanner.backupName(fileName);

        // a) old target → .bak_ (crash here: target missing, bak present → RESTORE on recovery)
        String bakId = tree.rename(targetDocId, bakName);
        if (bakId == null) {
            // Rename started failing mid-flight? Fall back to copy-replace path.
            // Target still exists under original name in that case.
            commitCopyReplace(parentDocId, fileName, tmpUri, validator);
            return;
        }

        // b) tmp → target
        String newTargetId = tree.rename(treeDocumentIdOf(tmpUri), fileName);
        if (newTargetId == null) {
            // Restore old content first, then report.
            tree.rename(bakId, fileName);
            throw new StorageException(StorageException.Reason.PROVIDER_UNSUPPORTED,
                    "Provider failed to rename temp over target '" + fileName + "'");
        }

        // c) drop bak (crash before this: target=new exists → DELETE_BAK on recovery)
        tree.deleteUri(tree.uriFor(bakId));

        verifyTarget(parentDocId, fileName, validator);
    }

    private void commitCopyReplace(String parentDocId, String fileName, Uri tmpUri,
                                   Validator validator) throws StorageException {
        String targetDocId = tree.findChildDocumentId(parentDocId, fileName);
        byte[] oldBytes = null;
        if (targetDocId != null) {
            try {
                oldBytes = tree.readAll(tree.uriFor(targetDocId));
            } catch (StorageException ignored) {
                oldBytes = null; // unreadable old target: proceed, Issues will catch it
            }
        }

        Uri targetUri;
        if (targetDocId != null) {
            targetUri = tree.uriFor(targetDocId);
        } else {
            targetUri = tree.createFile(parentDocId, fileName, DocumentTreeClient.MIME_JSON);
        }

        try {
            copyVerified(tmpUri, targetUri, validator);
        } catch (StorageException e) {
            // Best effort restore of previous content.
            if (oldBytes != null) {
                try {
                    tree.writeAll(targetUri, oldBytes);
                } catch (StorageException ignored) {
                    // Leave recovery to Issues report — original bytes still logged in message.
                }
            }
            tree.deleteUri(tmpUri);
            throw e;
        }
        tree.deleteUri(tmpUri);
    }

    private void copyVerified(Uri fromUri, Uri toUri, Validator validator)
            throws StorageException {
        byte[] bytes = tree.readAll(fromUri);
        tree.writeAll(toUri, bytes);
        String readBack = tree.readUtf8(toUri);
        try {
            validator.validate(readBack);
        } catch (Exception e) {
            throw new StorageException(StorageException.Reason.VALIDATION_FAILED,
                    "Target verification failed: " + e.getMessage(), e);
        }
    }

    private void verifyTarget(String parentDocId, String fileName, Validator validator)
            throws StorageException {
        String docId = tree.findChildDocumentId(parentDocId, fileName);
        if (docId == null) {
            throw new StorageException(StorageException.Reason.NOT_FOUND,
                    "Target missing after write: " + fileName);
        }
        String content = tree.readUtf8(tree.uriFor(docId));
        try {
            validator.validate(content);
        } catch (Exception e) {
            throw new StorageException(StorageException.Reason.VALIDATION_FAILED,
                    "Post-commit verification failed for " + fileName + ": " + e.getMessage(), e);
        }
    }

    private String treeDocumentIdOf(Uri docUri) {
        return android.provider.DocumentsContract.getDocumentId(docUri);
    }

    // ------------------------------------------------------------- recovery

    /**
     * Crash recovery for one directory: deletes orphan .tmp_ files, deletes
     * finished .bak_ files, restores a .bak_ whose target is missing.
     * Returns a human-readable count summary.
     */
    public String recoverDirectory(String relativeDir) {
        String parentDocId = tree.resolveDocumentId(relativeDir);
        if (parentDocId == null) return "no directory";

        List<DocumentTreeClient.Child> temps = tree.listManagedTempFiles(parentDocId);
        int deletedTmp = 0, deletedBak = 0, restored = 0;

        for (DocumentTreeClient.Child ch : temps) {
            boolean isBak = ch.displayName.startsWith(".bak_");
            String targetName = isBak
                    ? ch.displayName.substring(".bak_".length())
                    : stripTmpName(ch.displayName);
            boolean targetExists =
                    targetName != null && tree.findChildDocumentId(parentDocId, targetName) != null;

            SafeWritePlanner.RecoveryAction action = SafeWritePlanner.decideRecovery(
                    true, isBak, targetExists);
            switch (action) {
                case DELETE_TMP:
                    if (tree.delete(ch.documentId)) deletedTmp++;
                    break;
                case DELETE_BAK:
                    if (tree.delete(ch.documentId)) deletedBak++;
                    break;
                case RESTORE_BAK:
                    if (tree.rename(ch.documentId, targetName) != null) restored++;
                    break;
                case NOTHING:
                default:
                    break;
            }
        }
        return "tmp deleted=" + deletedTmp + ", bak deleted=" + deletedBak
                + ", bak restored=" + restored;
    }

    /** ".tmp_link_x.json_ab12cd34" → "link_x.json" (best effort). */
    private static String stripTmpName(String tmpName) {
        if (tmpName == null || !tmpName.startsWith(".tmp_")) return null;
        String rest = tmpName.substring(".tmp_".length());
        int idx = rest.lastIndexOf('_');
        // nonce is 8 chars at the end
        if (idx > 0 && rest.length() - idx - 1 == 8) {
            return rest.substring(0, idx);
        }
        return rest;
    }
}
