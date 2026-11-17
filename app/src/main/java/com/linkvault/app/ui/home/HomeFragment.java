package com.linkvault.app.ui.home;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.view.MenuHost;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.linkvault.app.R;
import com.linkvault.app.data.share.SharedFilePayload;
import com.linkvault.app.domain.model.Category;
import com.linkvault.app.domain.model.FileRecord;
import com.linkvault.app.domain.model.LinkRecord;
import com.linkvault.app.domain.model.VaultItem;
import com.linkvault.app.domain.policy.UrlPolicy;
import com.linkvault.app.ui.save.SaveLinkFragment;

import java.util.List;
import java.util.Map;

/**
 * Modern Material 3 Home & Search Screen (FR-04, FR-05, FR-06).
 * Handles instant search, type filters (All/Links/Files), category chips,
 * and unified actions for Links and Files.
 */
public class HomeFragment extends Fragment implements LinksAdapter.Listener {

    private HomeViewModel viewModel;
    private LinksAdapter adapter;
    private ChipGroup typeChipGroup;
    private ChipGroup categoryChipGroup;
    private View emptyContainer;
    private android.widget.TextView emptyText;
    private MaterialCardView storageWarningCard;
    private android.widget.TextView storageWarningText;
    private EditText searchInput;
    private ImageButton searchClearButton;

    private final ActivityResultLauncher<String[]> filePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    try {
                        requireContext().getContentResolver().takePersistableUriPermission(
                                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } catch (Exception ignored) {}

                    SharedFilePayload fp = SharedFilePayload.fromUri(
                            requireContext().getContentResolver(), uri, null);
                    Bundle args = new Bundle();
                    args.putBoolean(SaveLinkFragment.ARG_IS_FILE, true);
                    args.putString(SaveLinkFragment.ARG_FILE_URI, uri.toString());
                    args.putString(SaveLinkFragment.ARG_TITLE, fp.displayName);
                    args.putString(SaveLinkFragment.ARG_FILE_NAME, fp.displayName);
                    args.putString(SaveLinkFragment.ARG_FILE_MIME, fp.mimeType);
                    args.putLong(SaveLinkFragment.ARG_FILE_SIZE, fp.sizeBytes);
                    Navigation.findNavController(requireView()).navigate(R.id.saveLinkFragment, args);
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(this).get(HomeViewModel.class);

        typeChipGroup = view.findViewById(R.id.typeChipGroup);
        categoryChipGroup = view.findViewById(R.id.categoryChipGroup);
        emptyContainer = view.findViewById(R.id.emptyContainer);
        emptyText = view.findViewById(R.id.emptyText);
        storageWarningCard = view.findViewById(R.id.storageWarningCard);
        storageWarningText = view.findViewById(R.id.storageWarningText);
        searchInput = view.findViewById(R.id.searchInput);
        searchClearButton = view.findViewById(R.id.searchClearButton);

        RecyclerView list = view.findViewById(R.id.linksList);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        androidx.recyclerview.widget.DefaultItemAnimator animator =
                new androidx.recyclerview.widget.DefaultItemAnimator();
        animator.setAddDuration(220);
        animator.setRemoveDuration(200);
        animator.setChangeDuration(180);
        animator.setMoveDuration(220);
        list.setItemAnimator(animator);
        adapter = new LinksAdapter();
        adapter.setListener(this);
        list.setAdapter(adapter);

        // Search events
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString();
                searchClearButton.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);
                viewModel.search(query);
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        searchClearButton.setOnClickListener(v -> {
            searchInput.setText("");
            viewModel.search("");
        });

        // Type filter chips
        typeChipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.contains(R.id.chipTypeLinks)) {
                viewModel.setTypeFilter(VaultItem.ItemType.LINK);
            } else if (checkedIds.contains(R.id.chipTypeFiles)) {
                viewModel.setTypeFilter(VaultItem.ItemType.FILE);
            } else {
                viewModel.setTypeFilter(null);
            }
        });

        FloatingActionButton fab = view.findViewById(R.id.fabAddLink);
        fab.setOnClickListener(v -> showAddOptionsDialog());

        storageWarningCard.setOnClickListener(v ->
                Navigation.findNavController(view).navigate(R.id.settingsFragment));

        setupMenu();
        observe();
    }

    private void showAddOptionsDialog() {
        String[] options = new String[]{"🔗 Add Link / URL", "📁 Import File (PDF, image, doc...)"};
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Add to Vault")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        Bundle args = new Bundle();
                        args.putBoolean(SaveLinkFragment.ARG_FROM_SHARE, false);
                        Navigation.findNavController(requireView()).navigate(R.id.saveLinkFragment, args);
                    } else if (which == 1) {
                        filePickerLauncher.launch(new String[]{"*/*"});
                    }
                })
                .show();
    }

    @Override
    public void onResume() {
        super.onResume();
        viewModel.refresh();
    }

    private void setupMenu() {
        MenuHost menuHost = requireActivity();
        menuHost.addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
                menuInflater.inflate(R.menu.menu_main, menu);
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
                int id = menuItem.getItemId();
                if (id == R.id.menu_categories) {
                    Navigation.findNavController(requireView()).navigate(R.id.categoriesFragment);
                    return true;
                }
                if (id == R.id.menu_settings) {
                    Navigation.findNavController(requireView()).navigate(R.id.settingsFragment);
                    return true;
                }
                return false;
            }
        }, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
    }

    private void observe() {
        viewModel.getCategories().observe(getViewLifecycleOwner(), cats ->
                renderChips(cats, viewModel.getSelectedCategoryId().getValue()));
        viewModel.getSelectedCategoryId().observe(getViewLifecycleOwner(), selected ->
                renderChips(viewModel.getCategories().getValue(), selected));
        viewModel.getCategoryNames().observe(getViewLifecycleOwner(), names -> {
            List<VaultItem> items = viewModel.getVisibleItems().getValue();
            adapter.submit(items == null ? List.of() : items, names);
            updateEmpty(items);
        });
        viewModel.getVisibleItems().observe(getViewLifecycleOwner(), items -> {
            Map<String, String> names = viewModel.getCategoryNames().getValue();
            adapter.submit(items, names == null ? Map.of() : names);
            updateEmpty(items);
        });
        viewModel.getBanner().observe(getViewLifecycleOwner(), b -> {
            if (b == null) return;
            storageWarningCard.setVisibility(b.visible ? View.VISIBLE : View.GONE);
            storageWarningText.setText(b.text);
        });
        viewModel.getToast().observe(getViewLifecycleOwner(), ev -> {
            String msg = ev.getContent();
            if (msg != null) Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
        });
    }

    private void updateEmpty(@Nullable List<VaultItem> items) {
        boolean empty = items == null || items.isEmpty();
        emptyContainer.setVisibility(empty ? View.VISIBLE : View.GONE);
        String selected = viewModel.getSelectedCategoryId().getValue();
        emptyText.setText(selected == null ? R.string.list_empty : R.string.list_empty_filtered);
    }

    private void renderChips(@Nullable List<Category> cats, @Nullable String selectedId) {
        if (cats == null) return;
        categoryChipGroup.removeAllViews();

        android.content.res.ColorStateList bgList =
                androidx.core.content.ContextCompat.getColorStateList(requireContext(), R.color.chip_bg_cyber);
        android.content.res.ColorStateList strokeList =
                androidx.core.content.ContextCompat.getColorStateList(requireContext(), R.color.chip_stroke_cyber);
        android.content.res.ColorStateList textList =
                androidx.core.content.ContextCompat.getColorStateList(requireContext(), R.color.chip_text_cyber);

        Chip all = new Chip(requireContext());
        all.setText("📁 All Folders");
        all.setCheckable(true);
        all.setChecked(selectedId == null);
        all.setChipBackgroundColor(bgList);
        all.setChipStrokeColor(strokeList);
        all.setChipStrokeWidth(3f);
        all.setTextColor(textList);
        all.setOnClickListener(v -> viewModel.selectCategory(null));
        categoryChipGroup.addView(all);

        for (Category c : cats) {
            Chip chip = new Chip(requireContext());
            chip.setText(c.getName());
            chip.setCheckable(true);
            chip.setChecked(c.getId().equals(selectedId));
            chip.setChipBackgroundColor(bgList);
            chip.setChipStrokeColor(strokeList);
            chip.setChipStrokeWidth(3f);
            chip.setTextColor(textList);
            final String id = c.getId();
            chip.setOnClickListener(v -> viewModel.selectCategory(id));
            categoryChipGroup.addView(chip);
        }
    }

    // --------------------------------------------------------- Item Actions

    @Override
    public void onItemClicked(VaultItem item) {
        if (item.isLink() && item.getLink() != null) {
            openUrl(item.getLink().getUrl());
        } else if (item.isFile() && item.getFile() != null) {
            openFile(item.getFile());
        }
    }

    @Override
    public void onMoreClicked(VaultItem item, View anchor) {
        PopupMenu popup = new PopupMenu(requireContext(), anchor);
        Menu menu = popup.getMenu();

        if (item.isLink() && item.getLink() != null) {
            LinkRecord link = item.getLink();
            menu.add(0, 1, 0, R.string.action_open);
            menu.add(0, 2, 1, R.string.action_share);
            menu.add(0, 3, 2, R.string.action_copy);
            menu.add(0, 4, 3, R.string.action_edit);
            menu.add(0, 5, 4, R.string.action_move);
            menu.add(0, 6, 5, link.isPinned() ? R.string.action_unpin : R.string.action_pin);
            menu.add(0, 7, 6, R.string.action_delete);
            popup.setOnMenuItemClickListener(mi -> {
                switch (mi.getItemId()) {
                    case 1: openUrl(link.getUrl()); return true;
                    case 2: shareLink(link); return true;
                    case 3: copyUrl(link.getUrl()); return true;
                    case 4: editRecord(link); return true;
                    case 5: showMoveDialog(item); return true;
                    case 6: viewModel.togglePin(link); return true;
                    case 7: confirmDelete(item); return true;
                    default: return false;
                }
            });
        } else if (item.isFile() && item.getFile() != null) {
            FileRecord file = item.getFile();
            menu.add(0, 1, 0, "Open File");
            menu.add(0, 2, 1, "Share File");
            menu.add(0, 3, 2, R.string.action_move);
            menu.add(0, 4, 3, R.string.action_delete);
            popup.setOnMenuItemClickListener(mi -> {
                switch (mi.getItemId()) {
                    case 1: openFile(file); return true;
                    case 2: shareFile(file); return true;
                    case 3: showMoveDialog(item); return true;
                    case 4: confirmDelete(item); return true;
                    default: return false;
                }
            });
        }
        popup.show();
    }

    private void openUrl(String url) {
        if (!UrlPolicy.isValid(url)) {
            Toast.makeText(requireContext(), R.string.open_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(requireContext(), R.string.open_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void copyUrl(String url) {
        ClipboardManager clipboard =
                (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("LinkVault URL", url));
            Toast.makeText(requireContext(), R.string.copied_toast, Toast.LENGTH_SHORT).show();
        }
    }

    private void shareLink(LinkRecord link) {
        if (link == null || link.getUrl() == null) return;
        try {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, link.getUrl());
            if (link.getTitle() != null && !link.getTitle().isEmpty()) {
                intent.putExtra(Intent.EXTRA_SUBJECT, link.getTitle());
            }
            startActivity(Intent.createChooser(intent, getString(R.string.share_title_chooser)));
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Share failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void openFile(FileRecord file) {
        Uri uri = viewModel.resolveFileUri(file);
        if (uri == null) {
            Toast.makeText(requireContext(), "File not found in storage", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, file.getMimeType());
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Open " + file.getDisplayName()));
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Cannot open file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void shareFile(FileRecord file) {
        Uri uri = viewModel.resolveFileUri(file);
        if (uri == null) return;
        try {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType(file.getMimeType());
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Share " + file.getDisplayName()));
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Share failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void editRecord(LinkRecord record) {
        Bundle args = new Bundle();
        args.putString(SaveLinkFragment.ARG_RECORD_ID, record.getId());
        Navigation.findNavController(requireView()).navigate(R.id.saveLinkFragment, args);
    }

    private void showMoveDialog(VaultItem item) {
        List<Category> cats = viewModel.categoriesSnapshot();
        if (cats.isEmpty()) return;
        String[] names = new String[cats.size()];
        int checked = -1;
        for (int i = 0; i < cats.size(); i++) {
            names[i] = cats.get(i).getName();
            if (cats.get(i).getId().equals(item.getCategoryId())) checked = i;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.action_move)
                .setSingleChoiceItems(names, checked, (dialog, which) -> {
                    viewModel.moveTo(item, cats.get(which).getId());
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private void confirmDelete(VaultItem item) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.delete_title)
                .setMessage(getString(R.string.delete_message, item.getTitle()))
                .setPositiveButton(R.string.btn_delete, (dialog, which) -> {
                    viewModel.delete(item);
                    if (item.isLink() && item.getLink() != null) {
                        Snackbar.make(requireView(), R.string.deleted_toast, Snackbar.LENGTH_LONG)
                                .setAction(R.string.undo, v -> viewModel.restore(item.getLink()))
                                .show();
                    } else {
                        Snackbar.make(requireView(), "File deleted", Snackbar.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }
}
