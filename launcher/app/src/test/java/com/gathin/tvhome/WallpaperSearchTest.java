package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Wallhaven query, which results are kept, and what the screen should do when a search fails. */
public class WallpaperSearchTest {
    private static WallpaperSearch.Hit hit(String path, int w, int h, long size, String type, String purity) {
        return new WallpaperSearch.Hit(path, w, h, size, type, purity);
    }

    private static WallpaperSearch.Hit ok(int w, int h) {
        return hit("https://w.wallhaven.cc/full/ab/wallhaven-ab.jpg", w, h, 1000, "image/jpeg", "sfw");
    }

    @Test
    public void theQueryUrlIsEncodedAndHasNoKey() {
        String url = WallpaperSearch.url("Lionel Messi");
        assertTrue(url.startsWith("https://wallhaven.cc/api/v1/search?q=Lionel+Messi&"));
        assertTrue(url.contains("atleast=3840x2160"));
        assertTrue(url.contains("ratios=landscape"));
        assertTrue(url.contains("categories=111"));
        assertTrue(url.contains("purity=100"));
        assertTrue(url.contains("sorting=relevance"));
        assertFalse(url.toLowerCase().contains("apikey"));
        assertFalse(url.contains("key="));
        assertTrue(WallpaperSearch.url("a&b=c").contains("q=a%26b%3Dc"));
        assertEquals(WallpaperSearch.url("  Lionel Messi  "), url);
    }

    @Test
    public void onlyWallhavenHttpsPathsAreAccepted() {
        assertTrue(WallpaperSearch.allowedPath("https://w.wallhaven.cc/full/ab/wallhaven-ab.jpg"));
        assertTrue(WallpaperSearch.allowedPath("https://wallhaven.cc/x"));
        assertFalse(WallpaperSearch.allowedPath("http://w.wallhaven.cc/full/ab.jpg"));
        assertFalse(WallpaperSearch.allowedPath("https://evil.example/w.wallhaven.cc/a.jpg"));
        assertFalse(WallpaperSearch.allowedPath("https://wallhaven.cc.evil.com/a.jpg"));
        assertFalse(WallpaperSearch.allowedPath(null));
    }

    @Test
    public void smallPortraitSketchyOrHugeResultsAreLeftOut() {
        List<WallpaperSearch.Hit> hits = Arrays.asList(
                ok(1920, 1080),
                ok(2160, 3840),
                ok(4000, 4000),
                hit("https://w.wallhaven.cc/a.jpg", 3840, 2160, 1000, "image/jpeg", "sketchy"),
                hit("https://w.wallhaven.cc/a.jpg", 3840, 2160, 1000, "image/jpeg", "nsfw"),
                hit("https://w.wallhaven.cc/a.jpg", 3840, 2160, 1000, "image/webp", "sfw"),
                hit("https://w.wallhaven.cc/a.jpg", 3840, 2160, WallpaperSearch.MAX_DOWNLOAD_BYTES + 1, "image/png", "sfw"),
                hit("http://w.wallhaven.cc/a.jpg", 3840, 2160, 1000, "image/jpeg", "sfw"),
                ok(3840, 2160));
        List<WallpaperSearch.Hit> kept = WallpaperSearch.select(hits);
        assertEquals(1, kept.size());
        assertEquals(3840, kept.get(0).width);
        assertEquals(2160, kept.get(0).height);
    }

    @Test
    public void closerToSixteenByNineComesFirstAndEightIsTheCap() {
        List<WallpaperSearch.Hit> hits = new ArrayList<>();
        hits.add(ok(5120, 2160));
        hits.add(ok(3840, 2160));
        hits.add(ok(7680, 4320));
        for (int i = 0; i < 8; i++) hits.add(ok(3840, 2160));
        List<WallpaperSearch.Hit> kept = WallpaperSearch.select(hits);
        assertEquals(8, kept.size());
        assertEquals(3840, kept.get(0).width);
        assertEquals(2160, kept.get(0).height);
        // Same ratio as the first picture, so it stays behind it.
        assertEquals(7680, kept.get(1).width);
        assertFalse(kept.contains(hits.get(0)));
    }

    @Test
    public void anUnknownFileSizeOrTypeIsStillACandidate() {
        WallpaperSearch.Hit hit = hit("https://w.wallhaven.cc/a.jpg", 3840, 2160, 0, "", "SFW");
        assertEquals(Collections.singletonList(hit), WallpaperSearch.select(Collections.singletonList(hit)));
    }

    @Test
    public void fallbacksKeepTheCurrentSetUnlessSomethingWasSaved() {
        assertEquals(WallpaperSearch.Outcome.OFFLINE, WallpaperSearch.classify(false, false, 0, 0));
        assertEquals(WallpaperSearch.Outcome.FAILED, WallpaperSearch.classify(true, false, 0, 0));
        assertEquals(WallpaperSearch.Outcome.NONE, WallpaperSearch.classify(true, true, 0, 0));
        assertEquals(WallpaperSearch.Outcome.KEPT, WallpaperSearch.classify(true, true, 8, 0));
        assertEquals(WallpaperSearch.Outcome.APPLIED, WallpaperSearch.classify(true, true, 8, 1));
    }

    @Test
    public void theFortyFifthRequestInsideAMinuteWaits() {
        List<Long> few = new ArrayList<>();
        for (int i = 0; i < 44; i++) few.add(0L);
        assertEquals(0, WallpaperSearch.delayMs(few, 1_000));
        List<Long> full = new ArrayList<>(few);
        full.add(0L);
        assertEquals(59_000, WallpaperSearch.delayMs(full, 1_000));
        assertEquals(0, WallpaperSearch.delayMs(full, 60_000));
        assertEquals(0, WallpaperSearch.delayMs(null, 0));
        assertEquals(45, WallpaperSearch.MAX_PER_MINUTE);
    }
}
