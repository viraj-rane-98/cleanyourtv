package com.gathin.tvhome;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * Saves a search one image at a time. The original download is deleted; only the encoded JPEG stays.
 * A hit that fails is skipped. The caller commits the directory only when at least one file remains.
 */
final class WallpaperDownload {
    interface Fetcher {
        /** Writes the download to {@code dest}. False skips this hit. */
        boolean download(WallpaperSearch.Hit hit, File dest) throws IOException;
    }

    interface Encoder {
        boolean save(File source, File dest, int screenW, int screenH);
    }

    interface Progress {
        void saved(int saved, int total);
    }

    private WallpaperDownload() {}

    static int save(List<WallpaperSearch.Hit> hits, Fetcher fetcher, Encoder encoder, File dir,
                    int screenW, int screenH, Progress progress) {
        int saved = 0;
        int total = hits == null ? 0 : hits.size();
        if (hits == null || dir == null) return 0;
        for (int i = 0; i < hits.size(); i++) {
            File raw = new File(dir, "raw.tmp");
            File jpg = new File(dir, String.format(Locale.US, "%02d.jpg", saved));
            boolean ok = false;
            try {
                if (raw.exists()) raw.delete();
                ok = fetcher.download(hits.get(i), raw)
                        && WallpaperMagic.jpegOrPng(raw)
                        && encoder.save(raw, jpg, screenW, screenH)
                        && jpg.isFile() && jpg.length() > 0;
            } catch (IOException e) {
                ok = false;
            }
            raw.delete();
            // A leftover original must not become part of the set.
            if (raw.exists()) ok = false;
            if (!ok) {
                if (jpg.exists()) jpg.delete();
            } else {
                saved++;
            }
            if (progress != null) progress.saved(saved, total);
        }
        return saved;
    }
}
