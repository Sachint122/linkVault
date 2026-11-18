package com.linkvault.app.domain.model;

/** One auto-categorize rule: links whose host matches {@code domain} go to {@code categoryId}. */
public final class AutoRule {

    public final String domain;
    public final String categoryId;

    public AutoRule(String domain, String categoryId) {
        this.domain = domain;
        this.categoryId = categoryId;
    }

    @Override
    public String toString() {
        return domain + " -> " + categoryId;
    }
}
