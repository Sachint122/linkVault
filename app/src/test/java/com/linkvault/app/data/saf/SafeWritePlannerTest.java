package com.linkvault.app.data.saf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SafeWritePlannerTest {

    @Test
    public void choose_firstWrite_createsNew() {
        assertEquals(SafeWritePlanner.Strategy.CREATE_NEW,
                SafeWritePlanner.choose(false, false));
        assertEquals(SafeWritePlanner.Strategy.CREATE_NEW,
                SafeWritePlanner.choose(false, true));
    }

    @Test
    public void choose_existingWithRename_renamesSwap() {
        assertEquals(SafeWritePlanner.Strategy.RENAME_SWAP,
                SafeWritePlanner.choose(true, true));
    }

    @Test
    public void choose_existingWithoutRename_copiesReplace() {
        assertEquals(SafeWritePlanner.Strategy.COPY_REPLACE,
                SafeWritePlanner.choose(true, false));
    }

    @Test
    public void tempAndBackupNames_distinctAndPrefixed() {
        String tmp = SafeWritePlanner.tempName("link_a.json", "ab12cd34");
        String bak = SafeWritePlanner.backupName("link_a.json");
        assertTrue(tmp.startsWith(".tmp_"));
        assertTrue(bak.startsWith(".bak_"));
        assertFalse(tmp.equals(bak));
        assertEquals(".bak_link_a.json", bak);
    }

    @Test
    public void isManagedTemp_detectsOnlyOurFiles() {
        assertTrue(SafeWritePlanner.isManagedTemp(".tmp_link_a.json_x"));
        assertTrue(SafeWritePlanner.isManagedTemp(".bak_link_a.json"));
        assertFalse(SafeWritePlanner.isManagedTemp("link_a.json"));
        assertFalse(SafeWritePlanner.isManagedTemp(null));
        assertFalse(SafeWritePlanner.isManagedTemp("important.bak"));
    }

    @Test
    public void recovery_orphanTmp_alwaysDeleted() {
        assertEquals(SafeWritePlanner.RecoveryAction.DELETE_TMP,
                SafeWritePlanner.decideRecovery(true, false, false));
        assertEquals(SafeWritePlanner.RecoveryAction.DELETE_TMP,
                SafeWritePlanner.decideRecovery(true, false, true));
    }

    @Test
    public void recovery_bakWithTargetPresent_deleted() {
        assertEquals(SafeWritePlanner.RecoveryAction.DELETE_BAK,
                SafeWritePlanner.decideRecovery(true, true, true));
    }

    @Test
    public void recovery_bakWithoutTarget_restored() {
        // Crash happened between rename-old→bak and rename-tmp→target.
        assertEquals(SafeWritePlanner.RecoveryAction.RESTORE_BAK,
                SafeWritePlanner.decideRecovery(true, true, false));
    }

    @Test
    public void recovery_normalFile_nothing() {
        assertEquals(SafeWritePlanner.RecoveryAction.NOTHING,
                SafeWritePlanner.decideRecovery(false, false, true));
    }
}
