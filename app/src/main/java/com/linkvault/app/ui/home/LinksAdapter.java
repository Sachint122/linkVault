package com.linkvault.app.ui.home;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.linkvault.app.R;
import com.linkvault.app.domain.model.VaultItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Universal adapter for VaultItems (Links and Files) with Material 3 styling (FR-04, FR-06).
 * Uses DiffUtil so adds/removes/changes animate via the ItemAnimator.
 */
public class LinksAdapter extends RecyclerView.Adapter<LinksAdapter.Holder> {

    public interface Listener {
        void onItemClicked(VaultItem item);
        void onMoreClicked(VaultItem item, View anchor);
    }

    private final List<VaultItem> items = new ArrayList<>();
    private Map<String, String> categoryNames = Map.of();
    private Listener listener;

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<VaultItem> newItems, Map<String, String> categoryNamesById) {
        final List<VaultItem> old = new ArrayList<>(items);
        final Map<String, String> oldNames = categoryNames;
        final List<VaultItem> fresh = newItems == null ? List.of() : newItems;
        this.categoryNames = categoryNamesById == null ? Map.of() : categoryNamesById;

        DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return old.size(); }
            @Override public int getNewListSize() { return fresh.size(); }

            @Override public boolean areItemsTheSame(int o, int n) {
                return old.get(o).getId().equals(fresh.get(n).getId());
            }

            @Override public boolean areContentsTheSame(int o, int n) {
                VaultItem a = old.get(o);
                VaultItem b = fresh.get(n);
                return Objects.equals(a.getTitle(), b.getTitle())
                        && Objects.equals(a.getSubtitle(), b.getSubtitle())
                        && Objects.equals(a.getCategoryId(), b.getCategoryId())
                        && a.isPinned() == b.isPinned()
                        && Objects.equals(a.getTags(), b.getTags())
                        && Objects.equals(a.getUpdatedAt(), b.getUpdatedAt())
                        && Objects.equals(
                                oldNames.get(a.getCategoryId()),
                                categoryNamesById == null ? null
                                        : categoryNamesById.get(b.getCategoryId()));
            }
        });

        items.clear();
        items.addAll(fresh);
        result.dispatchUpdatesTo(this);
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_link, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        VaultItem item = items.get(position);
        h.title.setText(item.getTitle());
        h.url.setText(item.getSubtitle());

        // Type icon
        if (item.isLink()) {
            h.typeIcon.setImageResource(R.drawable.ic_link);
        } else {
            h.typeIcon.setImageResource(R.drawable.ic_file);
        }

        // Pin icon (links only)
        h.pinIcon.setVisibility(item.isPinned() ? View.VISIBLE : View.GONE);

        // Metadata: Category & Tags
        StringBuilder meta = new StringBuilder();
        String catName = categoryNames.get(item.getCategoryId());
        if (catName != null) meta.append(catName);

        List<String> tags = item.getTags();
        if (tags != null && !tags.isEmpty()) {
            if (meta.length() > 0) meta.append("  ·  ");
            meta.append("#").append(tags.get(0));
            if (tags.size() > 1) meta.append(" +").append(tags.size() - 1);
        }

        h.meta.setText(meta.toString());
        h.meta.setVisibility(meta.length() > 0 ? View.VISIBLE : View.GONE);

        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClicked(item);
        });
        h.moreButton.setOnClickListener(v -> {
            if (listener != null) listener.onMoreClicked(item, v);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ImageView typeIcon;
        final TextView title;
        final TextView url;
        final TextView meta;
        final ImageView pinIcon;
        final View moreButton;

        Holder(@NonNull View itemView) {
            super(itemView);
            typeIcon = itemView.findViewById(R.id.typeIcon);
            title = itemView.findViewById(R.id.itemTitle);
            url = itemView.findViewById(R.id.itemUrl);
            meta = itemView.findViewById(R.id.itemMeta);
            pinIcon = itemView.findViewById(R.id.pinIcon);
            moreButton = itemView.findViewById(R.id.moreButton);
        }
    }
}
