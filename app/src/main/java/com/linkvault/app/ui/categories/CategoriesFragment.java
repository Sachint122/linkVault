package com.linkvault.app.ui.categories;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import androidx.appcompat.widget.PopupMenu;
import com.linkvault.app.R;
import com.linkvault.app.domain.model.Category;

import java.util.ArrayList;
import java.util.List;

/** Category list + create/rename/delete with non-empty move prompt (FR-03). */
public class CategoriesFragment extends Fragment {

    private CategoriesViewModel viewModel;
    private CategoryAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_categories, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(this).get(CategoriesViewModel.class);

        RecyclerView list = view.findViewById(R.id.categoriesList);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new CategoryAdapter();
        list.setAdapter(adapter);

        FloatingActionButton fab = view.findViewById(R.id.fabAddCategory);
        fab.setOnClickListener(v -> showNameDialog(null));

        view.findViewById(R.id.emptyText).setVisibility(View.GONE);

        viewModel.getRows().observe(getViewLifecycleOwner(), rows -> {
            adapter.submit(rows);
            View empty = view.findViewById(R.id.emptyText);
            empty.setVisibility(rows == null || rows.isEmpty() ? View.VISIBLE : View.GONE);
        });
        viewModel.getToast().observe(getViewLifecycleOwner(), ev -> {
            String msg = ev.getContent();
            if (msg != null) Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
        });
        viewModel.getDeleteRequest().observe(getViewLifecycleOwner(), ev -> {
            CategoriesViewModel.DeleteRequest req = ev.getContent();
            if (req != null) showNonEmptyDeleteDialog(req);
        });
    }

    private void showNameDialog(@Nullable Category existing) {
        EditText input = new EditText(requireContext());
        input.setHint(R.string.category_name_hint);
        input.setMaxLines(1);
        if (existing != null) input.setText(existing.getName());
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        android.widget.FrameLayout wrapper = new android.widget.FrameLayout(requireContext());
        wrapper.setPadding(pad, 0, pad, 0);
        wrapper.addView(input);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(existing == null
                        ? R.string.category_new_title : R.string.category_rename_title)
                .setView(wrapper)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String name = input.getText() == null
                            ? "" : input.getText().toString();
                    if (existing == null) viewModel.create(name);
                    else viewModel.rename(existing, name);
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private void showNonEmptyDeleteDialog(CategoriesViewModel.DeleteRequest req) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.category_delete_title, req.category.getName()))
                .setMessage(getString(R.string.category_delete_nonempty,
                        req.category.getName(), req.linkCount))
                .setPositiveButton(R.string.btn_move_and_delete,
                        (dialog, which) -> viewModel.delete(req.category, true))
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    /** Simple row adapter with overflow (rename/delete). */
    private class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.Holder> {

        private final List<CategoriesViewModel.Row> items = new ArrayList<>();

        void submit(@Nullable List<CategoriesViewModel.Row> rows) {
            items.clear();
            if (rows != null) items.addAll(rows);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_category, parent, false);
            return new Holder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            CategoriesViewModel.Row row = items.get(position);
            h.name.setText(row.category.getName());
            h.count.setText(getString(R.string.category_count, row.linkCount));

            h.itemView.setOnClickListener(v -> showRowMenu(row, v));
        }

        private void showRowMenu(CategoriesViewModel.Row row, View anchor) {
            PopupMenu popup = new PopupMenu(requireContext(), anchor);
            popup.getMenu().add(0, 1, 0, R.string.action_rename);
            popup.getMenu().add(0, 2, 1, R.string.action_delete);
            popup.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == 1) {
                    showNameDialog(row.category);
                    return true;
                }
                if (item.getItemId() == 2) {
                    viewModel.requestDelete(row.category, row.linkCount);
                    return true;
                }
                return false;
            });
            popup.show();
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            final android.widget.TextView name;
            final android.widget.TextView count;

            Holder(@NonNull View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.categoryName);
                count = itemView.findViewById(R.id.categoryCount);
            }
        }
    }
}
