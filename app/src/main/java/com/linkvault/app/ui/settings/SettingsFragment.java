package com.linkvault.app.ui.settings;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.linkvault.app.R;
import com.linkvault.app.data.backup.RestoreEngine;
import com.linkvault.app.data.saf.StoragePermissionManager;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * Settings: folder status/recovery, storage self-test, issues scan,
 * Phase 4 backup export/restore, and index rebuild.
 */
public class SettingsFragment extends Fragment {

    private TextView storageState;
    private TextView storageFolder;
    private TextView selfTestResult;
    private TextView issuesResult;
    private TextView backupStatusText;
    private MaterialButton selfTestButton;
    private MaterialButton issuesButton;
    private MaterialButton backupExportButton;
    private MaterialButton backupRestoreButton;
    private MaterialButton rebuildIndexButton;
    private SettingsViewModel viewModel;

    @Nullable private byte[] pendingRestoreZipBytes;

    private final ActivityResultLauncher<Uri> folderPicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocumentTree(), uri -> {
                if (uri == null) return;
                viewModel.reselectFolder(uri);
            });

    private final ActivityResultLauncher<String> exportDocLauncher =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("application/zip"), uri -> {
                if (uri != null) {
                    viewModel.exportBackup(uri);
                }
            });

    private final ActivityResultLauncher<String[]> openBackupLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    try (InputStream in = requireContext().getContentResolver().openInputStream(uri)) {
                        if (in != null) {
                            ByteArrayOutputStream bos = new ByteArrayOutputStream();
                            byte[] buf = new byte[16 * 1024];
                            int read;
                            while ((read = in.read(buf)) != -1) {
                                bos.write(buf, 0, read);
                            }
                            pendingRestoreZipBytes = bos.toByteArray();
                            viewModel.previewRestore(pendingRestoreZipBytes);
                        }
                    } catch (Exception e) {
                        Toast.makeText(requireContext(), "Failed to read file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        storageState = view.findViewById(R.id.storageState);
        storageFolder = view.findViewById(R.id.storageFolder);
        selfTestResult = view.findViewById(R.id.selfTestResult);
        issuesResult = view.findViewById(R.id.issuesResult);
        backupStatusText = view.findViewById(R.id.backupStatusText);
        selfTestButton = view.findViewById(R.id.selfTestButton);
        issuesButton = view.findViewById(R.id.issuesButton);
        backupExportButton = view.findViewById(R.id.backupExportButton);
        backupRestoreButton = view.findViewById(R.id.backupRestoreButton);
        rebuildIndexButton = view.findViewById(R.id.rebuildIndexButton);

        viewModel = new ViewModelProvider(this).get(SettingsViewModel.class);

        view.findViewById(R.id.reselectButton).setOnClickListener(v -> folderPicker.launch(null));
        view.findViewById(R.id.recheckButton).setOnClickListener(v -> viewModel.refresh());
        selfTestButton.setOnClickListener(v -> viewModel.runSelfTest());
        issuesButton.setOnClickListener(v -> viewModel.scanIssues());

        backupExportButton.setOnClickListener(v ->
                exportDocLauncher.launch("linkvault_backup_" + System.currentTimeMillis() + ".zip"));
        backupRestoreButton.setOnClickListener(v ->
                openBackupLauncher.launch(new String[]{"application/zip", "application/octet-stream", "*/*"}));
        rebuildIndexButton.setOnClickListener(v -> viewModel.rebuildIndex());

        viewModel.getState().observe(getViewLifecycleOwner(), this::renderState);
        viewModel.getSelfTest().observe(getViewLifecycleOwner(), this::renderSelfTest);
        viewModel.getIssuesReport().observe(getViewLifecycleOwner(), this::renderIssues);

        viewModel.getBackupStatus().observe(getViewLifecycleOwner(), status -> {
            if (status != null) {
                backupStatusText.setVisibility(View.VISIBLE);
                backupStatusText.setText(status);
            }
        });

        viewModel.getRestorePreview().observe(getViewLifecycleOwner(), preview -> {
            if (preview != null && pendingRestoreZipBytes != null) {
                showRestoreConfirmDialog(preview);
            }
        });

        viewModel.getBusy().observe(getViewLifecycleOwner(), busy -> {
            boolean running = busy != null;
            selfTestButton.setEnabled(!running);
            issuesButton.setEnabled(!running);
            backupExportButton.setEnabled(!running);
            backupRestoreButton.setEnabled(!running);
            rebuildIndexButton.setEnabled(!running);
            if ("self-test".equals(busy)) {
                selfTestResult.setText(R.string.self_test_running);
            }
        });
    }

    private void showRestoreConfirmDialog(RestoreEngine.RestorePreview preview) {
        String msg = "Backup Created: " + preview.backupCreatedAt + "\n\n"
                + "• Categories: " + preview.categoryCount + "\n"
                + "• Links: " + preview.linkCount + " (" + preview.conflictingLinks + " conflicting)\n"
                + "• Files: " + preview.fileCount + " (" + preview.conflictingFiles + " conflicting)\n";

        if (!preview.warnings.isEmpty()) {
            msg += "\nWarnings:\n" + String.join("\n", preview.warnings);
        }

        String[] policies = new String[]{
                "Skip Conflicting Items",
                "Keep Both (Generate New IDs)",
                "Replace Existing Items"
        };
        final int[] selectedPolicy = {0};

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Restore Vault Data")
                .setMessage(msg)
                .setSingleChoiceItems(policies, 0, (dialog, which) -> selectedPolicy[0] = which)
                .setPositiveButton("Restore Now", (dialog, which) -> {
                    RestoreEngine.ConflictPolicy policy;
                    if (selectedPolicy[0] == 1) policy = RestoreEngine.ConflictPolicy.KEEP_BOTH;
                    else if (selectedPolicy[0] == 2) policy = RestoreEngine.ConflictPolicy.REPLACE;
                    else policy = RestoreEngine.ConflictPolicy.SKIP;

                    if (pendingRestoreZipBytes != null) {
                        viewModel.executeRestore(pendingRestoreZipBytes, policy);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void renderState(SettingsViewModel.UiState s) {
        if (s == null) return;
        boolean healthy = s.accessState == StoragePermissionManager.AccessState.GRANTED
                && s.folderAlive;
        if (s.accessState == StoragePermissionManager.AccessState.NO_FOLDER) {
            storageState.setText(R.string.storage_state_no_folder);
        } else if (healthy) {
            storageState.setText(R.string.storage_state_granted);
        } else {
            storageState.setText(R.string.storage_state_revoked);
        }
        storageFolder.setText(s.treeUri == null
                ? ""
                : getString(R.string.storage_folder_label, s.treeUri.toString()));
    }

    private void renderSelfTest(SettingsViewModel.TestResult r) {
        if (r == null) return;
        if (r.passed) {
            selfTestResult.setTextColor(getResources().getColor(R.color.lv_success, null));
            selfTestResult.setText(getString(R.string.self_test_pass, r.message));
        } else {
            selfTestResult.setTextColor(getResources().getColor(R.color.lv_error, null));
            selfTestResult.setText(getString(R.string.self_test_fail, r.step,
                    r.message == null ? "" : r.message));
        }
    }

    private void renderIssues(String report) {
        if (report == null) return;
        if ("NONE".equals(report)) {
            issuesResult.setTextColor(getResources().getColor(R.color.lv_success, null));
            issuesResult.setText(R.string.issues_none);
        } else {
            issuesResult.setTextColor(getResources().getColor(R.color.lv_error, null));
            issuesResult.setText(report);
        }
    }
}
