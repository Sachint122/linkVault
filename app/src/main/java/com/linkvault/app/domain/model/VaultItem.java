package com.linkvault.app.domain.model;

import androidx.annotation.Nullable;
import java.util.List;

/**
 * Unified representation of either a LinkRecord or a FileRecord for search,
 * filtering, and browse lists.
 */
public final class VaultItem {

    public enum ItemType { LINK, FILE }

    private final ItemType type;
    @Nullable private final LinkRecord link;
    @Nullable private final FileRecord file;

    public VaultItem(LinkRecord link) {
        this.type = ItemType.LINK;
        this.link = link;
        this.file = null;
    }

    public VaultItem(FileRecord file) {
        this.type = ItemType.FILE;
        this.link = null;
        this.file = file;
    }

    public ItemType getType() { return type; }
    public boolean isLink() { return type == ItemType.LINK; }
    public boolean isFile() { return type == ItemType.FILE; }

    @Nullable public LinkRecord getLink() { return link; }
    @Nullable public FileRecord getFile() { return file; }

    public String getId() {
        return isLink() && link != null ? link.getId() : (file != null ? file.getId() : "");
    }

    public String getTitle() {
        return isLink() && link != null ? link.getTitle() : (file != null ? file.getDisplayName() : "");
    }

    public String getSubtitle() {
        if (isLink() && link != null) return link.getUrl();
        if (file != null) return FileRecord.formatSize(file.getSizeBytes()) + "  ·  " + file.getMimeType();
        return "";
    }

    public String getCategoryId() {
        return isLink() && link != null ? link.getCategoryId() : (file != null ? file.getCategoryId() : "");
    }

    public List<String> getTags() {
        return isLink() && link != null ? link.getTags() : (file != null ? file.getTags() : List.of());
    }

    public boolean isPinned() {
        return isLink() && link != null && link.isPinned();
    }

    public String getCreatedAt() {
        return isLink() && link != null ? link.getCreatedAt() : (file != null ? file.getCreatedAt() : "");
    }

    public String getUpdatedAt() {
        return isLink() && link != null ? link.getUpdatedAt() : (file != null ? file.getUpdatedAt() : "");
    }
}
