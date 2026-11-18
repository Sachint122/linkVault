package com.linkvault.app.ui.home;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.linkvault.app.R;
import com.linkvault.app.domain.model.VaultItem;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Universal adapter for VaultItems (Links and Files) with Material 3 styling (FR-04, FR-06).
 * Uses DiffUtil so adds/removes/changes animate via the ItemAnimator.
 * Long-press enters multi-select mode (share / delete).
 */
public class LinksAdapter extends RecyclerView.Adapter<LinksAdapter.Holder> {

    public interface Listener {
        void onItemClicked(VaultItem item);
        void onMoreClicked(VaultItem item, View anchor);
        void onSelectionChanged(int count);
    }

    private final List<VaultItem> items = new ArrayList<>();
    private final Set<String> selectedIds = new HashSet<>();
    private Map<String, String> categoryNames = Map.of();
    private Listener listener;

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public boolean isSelectionMode() {
        return !selectedIds.isEmpty();
    }

    public void toggleSelection(VaultItem item) {
        if (!selectedIds.remove(item.getId())) {
            selectedIds.add(item.getId());
        }
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).getId().equals(item.getId())) {
                notifyItemChanged(i);
                break;
            }
        }
        if (listener != null) listener.onSelectionChanged(selectedIds.size());
    }

    public void exitSelection() {
        if (selectedIds.isEmpty()) return;
        Set<String> old = new HashSet<>(selectedIds);
        selectedIds.clear();
        for (int i = 0; i < items.size(); i++) {
            if (old.contains(items.get(i).getId())) notifyItemChanged(i);
        }
        if (listener != null) listener.onSelectionChanged(0);
    }

    /** Selected items in current list order (stale ids ignored). */
    public List<VaultItem> getSelectedItems() {
        List<VaultItem> out = new ArrayList<>();
        for (VaultItem it : items) {
            if (selectedIds.contains(it.getId())) out.add(it);
        }
        return out;
    }

    public void submit(List<VaultItem> newItems, Map<String, String> categoryNamesById) {
        final List<VaultItem> old = new ArrayList<>(items);
        final Map<String, String> oldNames = categoryNames;
        final List<VaultItem> fresh = newItems == null ? List.of() : newItems;
        this.categoryNames = categoryNamesById == null ? Map.of() : categoryNamesById;

        // Drop selections that are no longer in the list.
        Set<String> freshIds = new HashSet<>();
        for (VaultItem v : fresh) freshIds.add(v.getId());
        boolean pruned = selectedIds.retainAll(freshIds);

        DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return old.size(); }
            @Override public int getNewListSize() { return fresh.size(); }

            @Override public boolean areItemsTheSame(int o, int n) {
                return old.get(o).getId().equals(fresh.get(n).getId());
            }

            @Override public boolean areContentsTheSame(int o, int n) {
                VaultItem a = old.get(o);
                VaultItem b = fresh.get(n);
                boolean aChecked = a.isLink() && a.getLink() != null
                        && a.getLink().getCheckedAt() != null;
                boolean bChecked = b.isLink() && b.getLink() != null
                        && b.getLink().getCheckedAt() != null;
                String aSummary = a.isLink() && a.getLink() != null
                        ? a.getLink().getSummary() : null;
                String bSummary = b.isLink() && b.getLink() != null
                        ? b.getLink().getSummary() : null;
                return Objects.equals(a.getTitle(), b.getTitle())
                        && Objects.equals(a.getSubtitle(), b.getSubtitle())
                        && Objects.equals(a.getCategoryId(), b.getCategoryId())
                        && a.isPinned() == b.isPinned()
                        && Objects.equals(a.getTags(), b.getTags())
                        && Objects.equals(a.getUpdatedAt(), b.getUpdatedAt())
                        && Objects.equals(aSummary, bSummary)
                        && aChecked == bChecked
                        && (!aChecked || a.getLink().getHttpStatus() == b.getLink().getHttpStatus())
                        && Objects.equals(
                                oldNames.get(a.getCategoryId()),
                                categoryNamesById == null ? null
                                        : categoryNamesById.get(b.getCategoryId()));
            }
        });

        items.clear();
        items.addAll(fresh);
        result.dispatchUpdatesTo(this);

        if (pruned && listener != null) listener.onSelectionChanged(selectedIds.size());
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
        boolean selected = selectedIds.contains(item.getId());
        h.title.setText(item.getTitle());
        h.url.setText(item.getSubtitle());

        // AI summary (2 lines) — only when the pipeline has produced one.
        String summary = item.isLink() && item.getLink() != null
                ? item.getLink().getSummary() : null;
        if (summary != null && !summary.isEmpty()) {
            h.summary.setText(summary);
            h.summary.setVisibility(View.VISIBLE);
        } else {
            h.summary.setVisibility(View.GONE);
        }

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

        // Dead-link check badge (only after a manual check).
        if (item.isLink() && item.getLink() != null
                && item.getLink().getCheckedAt() != null) {
            int status = item.getLink().getHttpStatus();
            String badge;
            if (status == com.linkvault.app.domain.DeadLinkChecker.NETWORK_FAIL) {
                badge = h.itemView.getContext().getString(R.string.meta_offline);
            } else if (status >= 400) {
                badge = h.itemView.getContext().getString(R.string.meta_dead, status);
            } else {
                badge = h.itemView.getContext().getString(R.string.meta_live);
            }
            if (meta.length() > 0) meta.append("  ·  ");
            meta.append(badge);
        }

        h.meta.setText(meta.toString());
        h.meta.setVisibility(meta.length() > 0 ? View.VISIBLE : View.GONE);

        // Selection highlight
        h.itemView.setBackgroundColor(selected
                ? ContextCompat.getColor(h.itemView.getContext(), R.color.cyber_surface)
                : Color.TRANSPARENT);

        h.itemView.setOnClickListener(v -> {
            if (isSelectionMode()) {
                toggleSelection(item);
            } else if (listener != null) {
                listener.onItemClicked(item);
            }
        });
        h.itemView.setOnLongClickListener(v -> {
            toggleSelection(item);
            return true;
        });
        h.moreButton.setOnClickListener(v -> {
            if (listener != null && !isSelectionMode()) listener.onMoreClicked(item, v);
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
        final TextView summary;
        final TextView meta;
        final ImageView pinIcon;
        final View moreButton;

        Holder(@NonNull View itemView) {
            super(itemView);
            typeIcon = itemView.findViewById(R.id.typeIcon);
            title = itemView.findViewById(R.id.itemTitle);
            url = itemView.findViewById(R.id.itemUrl);
            summary = itemView.findViewById(R.id.itemSummary);
            meta = itemView.findViewById(R.id.itemMeta);
            pinIcon = itemView.findViewById(R.id.pinIcon);
            moreButton = itemView.findViewById(R.id.moreButton);
        }
    }
}
