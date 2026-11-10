package com.gathin.tvhome;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;

/** Wallhaven over HTTPS. One instance per search, so the 45-per-minute gap is shared by the search and the files. */
final class WallpaperHttp {
    static final class Text {
        final boolean reached;
        final String body;

        Text(boolean reached, String body) {
            this.reached = reached;
            this.body = body;
        }
    }

    private final ArrayDeque<Long> stamps = new ArrayDeque<>();

    Text text(String url) {
        HttpURLConnection conn = null;
        try {
            acquire();
            conn = open(url);
            int code = conn.getResponseCode();
            if (!WallpaperSearch.allowedPath(conn.getURL().toString()) || code != 200) return new Text(true, null);
            return new Text(true, readLimited(conn.getInputStream(), 2 * 1024 * 1024));
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return new Text(false, null);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    boolean download(WallpaperSearch.Hit hit, File dest) throws IOException {
        if (hit == null || dest == null || !WallpaperSearch.allowedPath(hit.path)) return false;
        if (hit.fileSize > WallpaperSearch.MAX_DOWNLOAD_BYTES) return false;
        HttpURLConnection conn = null;
        try {
            acquire();
            conn = open(hit.path);
            int code = conn.getResponseCode();
            if (!WallpaperSearch.allowedPath(conn.getURL().toString()) || code != 200) return false;
            long length = conn.getContentLengthLong();
            if (length > WallpaperSearch.MAX_DOWNLOAD_BYTES) return false;
            try (InputStream in = conn.getInputStream(); OutputStream out = new FileOutputStream(dest)) {
                byte[] buf = new byte[8192];
                long total = 0;
                int n;
                while ((n = in.read(buf)) >= 0) {
                    total += n;
                    if (total > WallpaperSearch.MAX_DOWNLOAD_BYTES) {
                        out.close();
                        dest.delete();
                        return false;
                    }
                    out.write(buf, 0, n);
                }
            }
            return dest.isFile() && dest.length() > 0;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (dest.exists()) dest.delete();
            throw new IOException(e);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private void acquire() throws InterruptedException {
        long now = System.currentTimeMillis();
        while (!stamps.isEmpty() && now - stamps.peekFirst() >= WallpaperSearch.WINDOW_MS) stamps.removeFirst();
        long wait = WallpaperSearch.delayMs(new ArrayList<>(stamps), now);
        if (wait > 0) {
            Thread.sleep(wait);
            long after = System.currentTimeMillis();
            while (!stamps.isEmpty() && after - stamps.peekFirst() >= WallpaperSearch.WINDOW_MS) stamps.removeFirst();
        }
        stamps.addLast(System.currentTimeMillis());
    }

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(15_000);
        conn.setReadTimeout(30_000);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", WallpaperSearch.USER_AGENT);
        conn.setRequestProperty("Accept", "*/*");
        return conn;
    }

    private static String readLimited(InputStream in, int max) throws IOException {
        byte[] buf = new byte[4096];
        int total = 0;
        byte[] out = new byte[max];
        int n;
        while ((n = in.read(buf)) >= 0) {
            if (total + n > max) throw new IOException("response too large");
            System.arraycopy(buf, 0, out, total, n);
            total += n;
        }
        return new String(out, 0, total, StandardCharsets.UTF_8);
    }
}
