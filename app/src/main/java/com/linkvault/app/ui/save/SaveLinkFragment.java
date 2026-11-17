package com.linkvault.app.ui.save;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.MenuHost;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.linkvault.app.R;
import com.linkvault.app.domain.model.Category;
import com.linkvault.app.domain.model.LinkRecord;
import com.linkvault.app.domain.policy.UrlPolicy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Review screen shown before any link is persisted (FR-01): edit title,
 * note, category, tags, pin; duplicate warning on Save (D-07 - warn, never
 * block); used for share-entry, manual add, and edit modes.
 */
public class SaveLinkFragment extends Fragment {

    public static final String ARG_URL = "arg_url";
    public static final String ARG_TITLE = "arg_title";
    public static final String ARG_DESCRIPTION = "arg_description";
    public static final String ARG_CATEGORY_ID = "arg_category_id";
    public static final String ARG_SOURCE_PKG = "arg_source_pkg";
    public static final String ARG_SHARED_TEXT = "arg_shared_text";
    public static final String ARG_OTHER_URLS = "arg_other_urls";
    public static final String ARG_FROM_SHARE = "arg_from_share";
    public static final String ARG_RECORD_ID = "arg_record_id";
    public static final String ARG_IS_FILE = "arg_is_file";
    public static final String ARG_FILE_URI = "arg_file_uri";
    public static final String ARG_FILE_NAME = "arg_file_name";
    public static final String ARG_FILE_MIME = "arg_file_mime";
    public static final String ARG_FILE_SIZE = "arg_file_size";
    public static final String ARG_BATCH_URIS = "arg_batch_uris";

    private View fileInfoCard;
    private android.widget.TextView fileNameText;
    private android.widget.TextView fileDetailsText;
    private TextInputEditText urlInput;
    private TextInputEditText titleInput;
    private TextInputEditText descriptionInput;
    private TextInputEditText tagInput;
    private MaterialAutoCompleteTextView categoryDropdown;
    private ChipGroup tagsChipGroup;
    private com.google.android.material.materialswitch.MaterialSwitch pinnedSwitch;
    private com.google.android.material.textfield.TextInputLayout urlLayout;
    private android.widget.TextView duplicateWarning;
    private android.widget.TextView extraUrlsHint;
    private android.widget.TextView sourceLabel;
    private MaterialButton saveButton;

    private SaveLinkViewModel viewModel;
    private final Map<String, String> categoryNameToId = new HashMap<>();
    private final List<Category> categoryList = new ArrayList<>();

    @Nullable private String recordId;      // non-null = edit mode
    @Nullable private String sourcePackage;
    @Nullable private String sourceAppName;
    @Nullable private String preselectedCategoryId;
    private boolean isFileMode = false;
    private boolean isFromShare = false;
    @Nullable private String fileUriStr;
    @Nullable private String fileMime;
    @Nullable private String originalFileName;
    @Nullable private ArrayList<String> batchUris;

    /** Set once duplicate warning has been acknowledged for current URL. */
    @Nullable private String dupAckUrl;
    private boolean saveInProgress = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_save_link, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        fileInfoCard = view.findViewById(R.id.fileInfoCard);
        fileNameText = view.findViewById(R.id.fileNameText);
        fileDetailsText = view.findViewById(R.id.fileDetailsText);
        urlInput = view.findViewById(R.id.urlInput);
        titleInput = view.findViewById(R.id.titleInput);
        descriptionInput = view.findViewById(R.id.descriptionInput);
        tagInput = view.findViewById(R.id.tagInput);
        categoryDropdown = view.findViewById(R.id.categoryDropdown);
        tagsChipGroup = view.findViewById(R.id.tagsChipGroup);
        pinnedSwitch = view.findViewById(R.id.pinnedSwitch);
        urlLayout = view.findViewById(R.id.urlLayout);
        duplicateWarning = view.findViewById(R.id.duplicateWarning);
        extraUrlsHint = view.findViewById(R.id.extraUrlsHint);
        sourceLabel = view.findViewById(R.id.sourceLabel);
        saveButton = view.findViewById(R.id.saveButton);

        viewModel = new ViewModelProvider(this).get(SaveLinkViewModel.class);

        Bundle args = requireArguments();
        recordId = args.getString(ARG_RECORD_ID);
        sourcePackage = args.getString(ARG_SOURCE_PKG);
        preselectedCategoryId = args.getString(ARG_CATEGORY_ID);
        isFileMode = args.getBoolean(ARG_IS_FILE, false);
        isFromShare = args.getBoolean(ARG_FROM_SHARE, false);

        if (isFileMode) {
            fileUriStr = args.getString(ARG_FILE_URI);
            fileMime = args.getString(ARG_FILE_MIME);
            originalFileName = args.getString(ARG_FILE_NAME);
            batchUris = args.getStringArrayList(ARG_BATCH_URIS);

            urlLayout.setVisibility(View.GONE);
            pinnedSwitch.setVisibility(View.GONE);
            fileInfoCard.setVisibility(View.VISIBLE);
            fileNameText.setText(originalFileName != null ? originalFileName : "File");
            long size = args.getLong(ARG_FILE_SIZE, 0);
            fileDetailsText.setText(com.linkvault.app.domain.model.FileRecord.formatSize(size)
                    + "  ·  " + (fileMime != null ? fileMime : "application/octet-stream"));
            if (originalFileName != null) titleInput.setText(originalFileName);
            saveButton.setText("Save File to Vault");
            requireActivity().setTitle("Save File");
        } else if (recordId != null) {
            requireActivity().setTitle(R.string.edit_title);
            viewModel.loadForEdit(recordId);
        } else {
            requireActivity().setTitle(R.string.save_title);
            applyPrefill(args);
        }
        if (sourcePackage != null) {
            viewModel.resolveSourceLabel(sourcePackage);
        }

        view.findViewById(R.id.tagAddButton).setOnClickListener(v -> addTagFromInput());
        saveButton.setOnClickListener(v -> onSubmit());

        // Setup Toolbar Menu with Tick (✓) action button
        MenuHost menuHost = requireActivity();
        menuHost.addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
                menuInflater.inflate(R.menu.menu_save, menu);
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
                if (menuItem.getItemId() == R.id.action_save) {
                    onSubmit();
                    return true;
                }
                return false;
            }
        }, getViewLifecycleOwner(), Lifecycle.State.RESUMED);

        // Clean Back button behavior: if entered from Share sheet, exit back to Chrome/source app
        requireActivity().getOnBackPressedDispatcher().addCallback(
                getViewLifecycleOwner(), new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (isFromShare) {
                    requireActivity().finish();
                } else {
                    setEnabled(false);
                    requireActivity().getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        observe();
    }

    private void applyPrefill(Bundle args) {
        String url = args.getString(ARG_URL);
        String title = args.getString(ARG_TITLE);
        String sharedText = args.getString(ARG_SHARED_TEXT);
        String otherUrls = args.getString(ARG_OTHER_URLS);

        if (!TextUtils.isEmpty(url)) {
            urlInput.setText(url.trim());
        } else if (!TextUtils.isEmpty(sharedText)) {
            // No URL found - show hint, let user paste (never auto-save text).
            urlLayout.setHint(getString(R.string.no_url_hint));
        }

        if (!TextUtils.isEmpty(title)) {
            titleInput.setText(title.trim());
        } else if (!TextUtils.isEmpty(url)) {
            // Auto fill fallback title (e.g. domain name) so title is never blank
            titleInput.setText(LinkRecord.fallbackTitle(url.trim()));
        }

        if (!TextUtils.isEmpty(otherUrls)) {
            extraUrlsHint.setVisibility(View.VISIBLE);
            extraUrlsHint.setText(getString(R.string.extra_urls_hint, otherUrls));
        }
    }

    private void observe() {
        viewModel.getCategories().observe(getViewLifecycleOwner(), list -> {
            categoryList.clear();
            if (list != null) categoryList.addAll(list);
            populateCategoryDropdown();
        });
        viewModel.getEditRecord().observe(getViewLifecycleOwner(), rec -> {
            if (rec == null) return;
            urlInput.setText(rec.getUrl());
            titleInput.setText(rec.getTitle());
            if (rec.getDescription() != null) descriptionInput.setText(rec.getDescription());
            pinnedSwitch.setChecked(rec.isPinned());
            sourcePackage = rec.getSourcePackageName();
            sourceAppName = rec.getSourceAppName();
            preselectedCategoryId = rec.getCategoryId();
            for (String t : rec.getTags()) addChip(t);
            if (sourcePackage != null) viewModel.resolveSourceLabel(sourcePackage);
            populateCategoryDropdown(); // re-apply selection once categories arrive
        });
        viewModel.getSourceLabel().observe(getViewLifecycleOwner(), label -> {
            sourceAppName = label;
            if (label == null) {
                sourceLabel.setVisibility(View.GONE);
            } else {
                sourceLabel.setVisibility(View.VISIBLE);
                sourceLabel.setText(getString(R.string.source_label, label));
            }
        });
        viewModel.getDuplicateCheck().observe(getViewLifecycleOwner(), ev -> {
            List<LinkRecord> matches = ev.getContent();
            if (matches == null) return;
            String currentUrl = textOf(urlInput).trim();
            if (!matches.isEmpty()) {
                if (!currentUrl.equals(dupAckUrl)) {
                    // First click: warn only (D-07), user may still save.
                    dupAckUrl = currentUrl;
                    duplicateWarning.setVisibility(View.VISIBLE);
                    duplicateWarning.setText(getString(R.string.duplicate_warning,
                            matches.get(0).getTitle()));
                    saveButton.setText(R.string.btn_save_anyway);
                    setBusy(false);
                    return;
                }
            }
            doSave();
        });
        viewModel.getSaved().observe(getViewLifecycleOwner(), ev -> {
            if (ev.getContent() == null) return;
            boolean isEdit = recordId != null;
            Toast.makeText(requireContext(),
                    isEdit ? R.string.save_updated : R.string.save_success,
                    Toast.LENGTH_SHORT).show();
            if (isFromShare) {
                requireActivity().finish();
            } else {
                Navigation.findNavController(requireView()).popBackStack();
            }
        });
        viewModel.getError().observe(getViewLifecycleOwner(), ev -> {
            String msg = ev.getContent();
            if (msg == null) return;
            setBusy(false);
            if ("URL_INVALID".equals(msg)) {
                urlLayout.setError(getString(R.string.save_invalid_url));
            } else {
                Toast.makeText(requireContext(),
                        getString(R.string.save_error, msg), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void onSubmit() {
        if (isFileMode) {
            setBusy(true);
            if (batchUris != null && !batchUris.isEmpty()) {
                List<android.net.Uri> uriList = new ArrayList<>();
                for (String u : batchUris) uriList.add(android.net.Uri.parse(u));
                viewModel.saveBatchFiles(uriList, selectedCategoryId(), collectTags());
            } else if (fileUriStr != null) {
                String dName = textOf(titleInput).trim();
                if (dName.isEmpty()) dName = originalFileName != null ? originalFileName : "file";
                viewModel.saveFile(
                        android.net.Uri.parse(fileUriStr),
                        dName,
                        fileMime,
                        selectedCategoryId(),
                        collectTags());
            }
            return;
        }

        String url = textOf(urlInput).trim();
        urlLayout.setError(null);
        if (!UrlPolicy.isValid(url)) {
            urlLayout.setError(getString(R.string.save_invalid_url));
            return;
        }
        // Reset ack when URL changed after a warning.
        if (dupAckUrl != null && !dupAckUrl.equals(url)) {
            dupAckUrl = null;
            duplicateWarning.setVisibility(View.GONE);
            saveButton.setText(R.string.btn_save);
        }
        setBusy(true);
        viewModel.checkDuplicate(url, recordId);
    }

    private void doSave() {
        viewModel.save(
                textOf(urlInput).trim(),
                textOf(titleInput).trim(),
                textOf(descriptionInput).trim(),
                selectedCategoryId(),
                collectTags(),
                pinnedSwitch.isChecked(),
                sourcePackage,
                sourceAppName);
    }

    private void setBusy(boolean busy) {
        saveInProgress = busy;
        saveButton.setEnabled(!busy);
    }

    private void populateCategoryDropdown() {
        String[] names = new String[categoryList.size()];
        categoryNameToId.clear();
        for (int i = 0; i < categoryList.size(); i++) {
            names[i] = categoryList.get(i).getName();
            categoryNameToId.put(names[i], categoryList.get(i).getId());
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_list_item_1, names);
        categoryDropdown.setAdapter(adapter);

        String selectId = preselectedCategoryId;
        if (selectId == null && recordId == null) selectId = Category.UNGROUPED_ID;
        if (selectId != null) {
            for (Category c : categoryList) {
                if (c.getId().equals(selectId)) {
                    categoryDropdown.setText(c.getName(), false);
                    break;
                }
            }
        }
    }

    @Nullable
    private String selectedCategoryId() {
        String name = textOf(categoryDropdown);
        String id = categoryNameToId.get(name);
        return id != null ? id : preselectedCategoryId;
    }

    private void addTagFromInput() {
        String tag = LinkRecord.normalizeTag(textOf(tagInput));
        if (tag.isEmpty()) return;
        addChip(tag);
        tagInput.setText(null);
    }

    private void addChip(String tag) {
        // Skip duplicates (case-insensitive).
        for (int i = 0; i < tagsChipGroup.getChildCount(); i++) {
            View v = tagsChipGroup.getChildAt(i);
            if (v instanceof Chip) {
                String existing = ((Chip) v).getText().toString();
                if (existing.equalsIgnoreCase(tag)) return;
            }
        }
        if (tagsChipGroup.getChildCount() >= 20) return;
        Chip chip = new Chip(requireContext());
        chip.setText(tag);
        chip.setCloseIconVisible(true);
        chip.setOnCloseIconClickListener(v -> tagsChipGroup.removeView(chip));
        tagsChipGroup.addView(chip);
    }

    private List<String> collectTags() {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < tagsChipGroup.getChildCount(); i++) {
            View v = tagsChipGroup.getChildAt(i);
            if (v instanceof Chip) {
                String t = LinkRecord.normalizeTag(((Chip) v).getText().toString());
                if (!t.isEmpty()) out.add(t);
            }
        }
        return out;
    }

    private static String textOf(TextInputEditText et) {
        return et.getText() == null ? "" : et.getText().toString();
    }

    private static String textOf(android.widget.TextView et) {
        return et.getText() == null ? "" : et.getText().toString();
    }

    @SuppressWarnings("unused")
    private boolean isSaveInProgress() {
        return saveInProgress;
    }
}
