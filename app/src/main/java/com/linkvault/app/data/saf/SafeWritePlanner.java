package com.linkvault.app.data.saf;

/**
 * Pure decision logic for the safe-write protocol (DATA_SCHEMA.md §9):
 * tmp → validate → replace → verify. Kept separate from I/O so the fallback
 * ordering is unit-testable without a device.
 */
public final class SafeWritePlanner {

    public enum Strategy {
        /** First write: create target directly, verify, drop tmp. */
        CREATE_NEW,
        /** Provider supports rename: bak-swap (safest, near-atomic). */
        RENAME_SWAP,
        /** No rename: in-memory backup + byte-copy onto target + verify. */
        COPY_REPLACE
    }

    private SafeWritePlanner() {
    }

    public static Strategy choose(boolean targetExists, boolean renameSupported) {
        if (!targetExists) return Strategy.CREATE_NEW;
        return renameSupported ? Strategy.RENAME_SWAP : Strategy.COPY_REPLACE;
    }

    /** Display name of the backup file for a given target name. */
    public static String backupName(String fileName) {
        return ".bak_" + fileName;
    }

    /** Display name of the temp file for a given target name. */
    public static String tempName(String fileName, String nonce) {
        return ".tmp_" + fileName + "_" + nonce;
    }

    /** True for files this engine owns for crash-recovery purposes. */
    public static boolean isManagedTemp(String displayName) {
        return displayName != null
                && (displayName.startsWith(".tmp_") || displayName.startsWith(".bak_"));
    }

    /**
     * Startup recovery decision for a directory listing:
     * - orphan tmp (target present or absent) → delete tmp (uncommitted work)
     * - bak present + target present+readable → delete bak (commit finished)
     * - bak present + target missing → restore bak (crash mid-swap)
     */
    public enum RecoveryAction { DELETE_TMP, DELETE_BAK, RESTORE_BAK, NOTHING }

    public static RecoveryAction decideRecovery(
            boolean isTempOrBak, boolean isBackup, boolean targetExists) {
        if (!isTempOrBak) return RecoveryAction.NOTHING;
        if (!isBackup) {
            // .tmp_ file: never committed → safe to delete regardless.
            return RecoveryAction.DELETE_TMP;
        }
        // .bak_ file
        if (targetExists) return RecoveryAction.DELETE_BAK;
        return RecoveryAction.RESTORE_BAK;
    }
}
