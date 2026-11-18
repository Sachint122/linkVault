package com.linkvault.app.domain;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Manual dead-link check (D-14): a single HEAD request with an 8s timeout.
 * User-initiated only (per-item menu or Settings batch) — never runs in the
 * background. Requires the INTERNET permission.
 */
public final class DeadLinkChecker {

    /** Network failure / timeout — reachability unknown, not "dead". */
    public static final int NETWORK_FAIL = -1;

    private static final int TIMEOUT_MS = 8000;

    private DeadLinkChecker() {}

    /**
     * @return HTTP status code (>= 100), or {@link #NETWORK_FAIL}.
     * Servers that reject HEAD (405/501) are reported as 200 — a response
     * at all proves the link is reachable.
     */
    public static int check(String url) {
        if (url == null || url.isEmpty()) return NETWORK_FAIL;
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestMethod("HEAD");
            int code = conn.getResponseCode();
            if (code == 405 || code == 501) return 200;
            return code;
        } catch (IOException e) {
            return NETWORK_FAIL;
        } catch (Exception e) {
            return NETWORK_FAIL;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
