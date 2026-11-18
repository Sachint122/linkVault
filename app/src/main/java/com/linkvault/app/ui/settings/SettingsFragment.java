package com.linkvault.app.ui.settings;

import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
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
import com.linkvault.app.domain.model.Category;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;

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
    private MaterialButton exportCsvButton;
    private MaterialButton exportTextButton;
    private MaterialButton checkAllButton;
    private TextView checkProgressText;
    private TextView autoCatRulesText;
    private MaterialButton addAutoCatButton;
    private MaterialButton deleteAutoCatButton;
    private TextView autoBackupStatusText;
    private MaterialButton toggleAutoBackupButton;
    private MaterialButton runBackupNowButton;
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

    private final ActivityResultLauncher<String> exportCsvLauncher =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("text/csv"), uri -> {
                if (uri != null) viewModel.exportCsv(uri);
            });

    private final ActivityResultLauncher<String> exportTextLauncher =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("text/plain"), uri -> {
                if (uri != null) viewModel.exportText(uri);
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
        exportCsvButton = view.findViewById(R.id.exportCsvButton);
        exportTextButton = view.findViewById(R.id.exportTextButton);
        checkAllButton = view.findViewById(R.id.checkAllButton);
        checkProgressText = view.findViewById(R.id.checkProgressText);
        autoCatRulesText = view.findViewById(R.id.autoCatRulesText);
        addAutoCatButton = view.findViewById(R.id.addAutoCatButton);
        deleteAutoCatButton = view.findViewById(R.id.deleteAutoCatButton);
        autoBackupStatusText = view.findViewById(R.id.autoBackupStatusText);
        toggleAutoBackupButton = view.findViewById(R.id.toggleAutoBackupButton);
        runBackupNowButton = view.findViewById(R.id.runBackupNowButton);

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
        exportCsvButton.setOnClickListener(v ->
                exportCsvLauncher.launch("linkvault_" + System.currentTimeMillis() + ".csv"));
        exportTextButton.setOnClickListener(v ->
                exportTextLauncher.launch("linkvault_" + System.currentTimeMillis() + ".txt"));
        checkAllButton.setOnClickListener(v -> viewModel.checkAllLinks());
        addAutoCatButton.setOnClickListener(v -> showAddRuleDomainDialog());
        deleteAutoCatButton.setOnClickListener(v -> showDeleteRuleDialog());
        toggleAutoBackupButton.setOnClickListener(v -> {
            boolean enable = !Boolean.TRUE.equals(viewModel.getAutoBackupEnabled().getValue());
            viewModel.toggleAutoBackup(enable);
        });
        runBackupNowButton.setOnClickListener(v -> viewModel.runAutoBackupNow());

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

        viewModel.getCheckProgress().observe(getViewLifecycleOwner(), progress -> {
            if (progress != null) {
                checkProgressText.setVisibility(View.VISIBLE);
                checkProgressText.setText(progress);
            } else {
                checkProgressText.setVisibility(View.GONE);
            }
        });

        viewModel.getAutoCatRules().observe(getViewLifecycleOwner(), rules -> {
            if (rules == null) return;
            if (rules.isEmpty()) {
                autoCatRulesText.setText("No rules yet — links Uncategorized me jayenge.");
                deleteAutoCatButton.setVisibility(View.GONE);
            } else {
                StringBuilder sb = new StringBuilder();
                for (SettingsViewModel.AutoCatEntry e : rules) {
                    sb.append(e.domain).append("  →  ").append(e.categoryName).append('\n');
                }
                autoCatRulesText.setText(sb.toString().trim());
                deleteAutoCatButton.setVisibility(View.VISIBLE);
            }
        });

        viewModel.getAutoBackupEnabled().observe(getViewLifecycleOwner(), enabled -> {
            boolean on = Boolean.TRUE.equals(enabled);
            toggleAutoBackupButton.setText(on ? "Auto Backup OFF karein" : "Auto Backup ON karein");
            runBackupNowButton.setVisibility(on ? View.VISIBLE : View.GONE);
        });
        viewModel.getAutoBackupStatus().observe(getViewLifecycleOwner(), status -> {
            if (status != null) autoBackupStatusText.setText(status);
        });

        viewModel.getBusy().observe(getViewLifecycleOwner(), busy -> {
            boolean running = busy != null;
            selfTestButton.setEnabled(!running);
            issuesButton.setEnabled(!running);
            backupExportButton.setEnabled(!running);
            backupRestoreButton.setEnabled(!running);
            rebuildIndexButton.setEnabled(!running);
            exportCsvButton.setEnabled(!running);
            exportTextButton.setEnabled(!running);
            checkAllButton.setEnabled(!running);
            addAutoCatButton.setEnabled(!running);
            deleteAutoCatButton.setEnabled(!running);
            if ("self-test".equals(busy)) {
                selfTestResult.setText(R.string.self_test_running);
            }
        });
    }

    // -------------------------------------------------- Auto-categorize rules

    private void showAddRuleDomainDialog() {
        android.widget.EditText input = new android.widget.EditText(requireContext());
        input.setHint("youtube.com");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        FrameLayout wrap = new FrameLayout(requireContext());
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        wrap.setPadding(pad, 0, pad, 0);
        wrap.addView(input);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Domain add karein")
                .setMessage("Is domain ka link save hote hi selected category me chala jayega.")
                .setView(wrap)
                .setPositiveButton("Aage", (d, w) ->
                        showCategoryPickDialog(input.getText().toString()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showCategoryPickDialog(String domain) {
        List<Category> cats = viewModel.categoriesSnapshot();
        if (cats.isEmpty()) {
            Toast.makeText(requireContext(), "Pehle ek category banayein", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] names = new String[cats.size()];
        for (int i = 0; i < cats.size(); i++) names[i] = cats.get(i).getName();
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Category chunein")
                .setItems(names, (d, which) ->
                        viewModel.addAutoCatRule(domain, cats.get(which).getId()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showDeleteRuleDialog() {
        List<SettingsViewModel.AutoCatEntry> rules = viewModel.getAutoCatRules().getValue();
        if (rules == null || rules.isEmpty()) return;
        String[] labels = new String[rules.size()];
        for (int i = 0; i < rules.size(); i++) {
            labels[i] = rules.get(i).domain + "  →  " + rules.get(i).categoryName;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Rule delete karein")
                .setItems(labels, (d, which) ->
                        viewModel.removeAutoCatRule(rules.get(which).domain))
                .setNegativeButton("Cancel", null)
                .show();
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
