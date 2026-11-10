package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Wallhaven search: the query URL, which results are safe 4K landscapes, and what a search should do
 * with them. No network here.
 */
final class WallpaperSearch {
    static final String USER_AGENT = "CleanYourTV wallpaper finder (com.gathin.tvhome)";
    static final int MIN_WIDTH = 3840;
    static final int MIN_HEIGHT = 2160;
    static final double TARGET_RATIO = 1.78;
    static final int MAX_RESULTS = 8;
    static final long MAX_DOWNLOAD_BYTES = 20L * 1024 * 1024;
    static final int MAX_PER_MINUTE = 45;
    static final long WINDOW_MS = 60_000L;

    enum Outcome { OFFLINE, NONE, KEPT, APPLIED, FAILED }

    static final class Hit {
        final String path;
        final int width;
        final int height;
        final long fileSize;
        final String fileType;
        final String purity;

        Hit(String path, int width, int height, long fileSize, String fileType, String purity) {
            this.path = path;
            this.width = width;
            this.height = height;
            this.fileSize = fileSize;
            this.fileType = fileType;
            this.purity = purity;
        }
    }

    private WallpaperSearch() {}

    /** The search URL. {@code q} is encoded; there is no API key. */
    static String url(String query) {
        String q = query == null ? "" : query.trim();
        try {
            return "https://wallhaven.cc/api/v1/search?q=" + URLEncoder.encode(q, "UTF-8")
                    + "&atleast=3840x2160&ratios=landscape&categories=111&purity=100&sorting=relevance";
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Full-image URL from the API: https on wallhaven.cc only. */
    static boolean allowedPath(String path) {
        if (path == null || !path.startsWith("https://")) return false;
        int start = "https://".length();
        int slash = path.indexOf('/', start);
        String host = (slash < 0 ? path.substring(start) : path.substring(start, slash)).toLowerCase(Locale.ROOT);
        int colon = host.indexOf(':');
        if (colon >= 0) host = host.substring(0, colon);
        return "wallhaven.cc".equals(host) || host.endsWith(".wallhaven.cc");
    }

    /**
     * {@code data[]} as hits, or null when the body is not JSON. An empty {@code data} array is an empty list.
     */
    static List<Hit> parse(String body) {
        if (body == null || body.trim().isEmpty()) return null;
        try {
            JSONArray data = new JSONObject(body).optJSONArray("data");
            if (data == null) return Collections.emptyList();
            List<Hit> hits = new ArrayList<>();
            for (int i = 0; i < data.length(); i++) {
                JSONObject item = data.optJSONObject(i);
                if (item == null) continue;
                hits.add(new Hit(item.optString("path", ""), item.optInt("dimension_x", 0),
                        item.optInt("dimension_y", 0), item.optLong("file_size", 0),
                        item.optString("file_type", ""), item.optString("purity", "")));
            }
            return hits;
        } catch (JSONException e) {
            return null;
        }
    }

    /** 4K landscape, safe for work, closest to 16:9 first, at most {@link #MAX_RESULTS}. */
    static List<Hit> select(List<Hit> hits) {
        List<Hit> ok = new ArrayList<>();
        if (hits != null) for (Hit hit : hits) if (accepted(hit)) ok.add(hit);
        ok.sort((a, b) -> Double.compare(distance(a), distance(b)));
        if (ok.size() > MAX_RESULTS) return new ArrayList<>(ok.subList(0, MAX_RESULTS));
        return ok;
    }

    static boolean accepted(Hit hit) {
        if (hit == null || hit.width < MIN_WIDTH || hit.height < MIN_HEIGHT || hit.width <= hit.height) return false;
        if (!"sfw".equalsIgnoreCase(hit.purity)) return false;
        if (!allowedPath(hit.path)) return false;
        if (!acceptedType(hit.fileType)) return false;
        return hit.fileSize <= 0 || hit.fileSize <= MAX_DOWNLOAD_BYTES;
    }

    static boolean acceptedType(String fileType) {
        if (fileType == null || fileType.isEmpty()) return true;
        String type = fileType.toLowerCase(Locale.ROOT);
        return "image/jpeg".equals(type) || "image/jpg".equals(type) || "image/png".equals(type);
    }

    /**
     * What to tell the screen. {@code reached} is a connection that answered; {@code parsed} is JSON;
     * {@code hits} is how many passed {@link #select}; {@code saved} is how many files were written.
     */
    static Outcome classify(boolean reached, boolean parsed, int hits, int saved) {
        if (!reached) return Outcome.OFFLINE;
        if (!parsed) return Outcome.FAILED;
        if (hits <= 0) return Outcome.NONE;
        if (saved <= 0) return Outcome.KEPT;
        return Outcome.APPLIED;
    }

    /** Wait so a 46th call inside {@link #WINDOW_MS} does not exceed {@link #MAX_PER_MINUTE}. */
    static long delayMs(List<Long> stampsOldestFirst, long now) {
        if (stampsOldestFirst == null || stampsOldestFirst.size() < MAX_PER_MINUTE) return 0;
        long oldest = stampsOldestFirst.get(stampsOldestFirst.size() - MAX_PER_MINUTE);
        long until = oldest + WINDOW_MS;
        return now >= until ? 0 : until - now;
    }

    private static double distance(Hit hit) {
        return Math.abs((double) hit.width / hit.height - TARGET_RATIO);
    }
}
