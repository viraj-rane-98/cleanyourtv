package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Downloads are checked, the originals are not kept, and a partial set still counts. */
public class WallpaperDownloadTest {
    private static final byte[] JPEG = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 1, 2, 3};
    private static final byte[] PNG = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1};

    @Test
    public void magicBytesAcceptJpegAndPngOnly() {
        assertTrue(WallpaperMagic.jpegOrPng(JPEG, JPEG.length));
        assertTrue(WallpaperMagic.jpegOrPng(PNG, PNG.length));
        assertFalse(WallpaperMagic.jpegOrPng(new byte[]{1, 2, 3, 4}, 4));
        assertFalse(WallpaperMagic.jpegOrPng(new byte[]{(byte) 0xFF, (byte) 0xD8}, 2));
        assertFalse(WallpaperMagic.jpegOrPng(null, 0));
    }

    @Test
    public void aPartialDownloadKeepsTheGoodFilesAndDeletesTheOriginals() throws Exception {
        File dir = Files.createTempDirectory("dl").toFile();
        List<WallpaperSearch.Hit> hits = Arrays.asList(hit(), hit(), hit(), hit());
        int[] step = {0};
        List<int[]> progress = new ArrayList<>();
        int saved = WallpaperDownload.save(hits, (hit, dest) -> {
            int n = step[0]++;
            if (n == 1) return false;
            if (n == 2) throw new java.io.IOException("dropped");
            byte[] bytes = n == 0 ? JPEG : PNG;
            try (FileOutputStream out = new FileOutputStream(dest)) {
                out.write(bytes);
            }
            return true;
        }, (source, dest, w, h) -> {
            try {
                Files.copy(source.toPath(), dest.toPath());
            } catch (java.io.IOException e) {
                return false;
            }
            return true;
        }, dir, 1920, 1080, (done, total) -> progress.add(new int[]{done, total}));

        assertEquals(2, saved);
        assertTrue(new File(dir, "00.jpg").isFile());
        assertTrue(new File(dir, "01.jpg").isFile());
        assertFalse(new File(dir, "02.jpg").exists());
        assertFalse(new File(dir, "raw.tmp").exists());
        assertEquals(4, progress.size());
        assertEquals(1, progress.get(0)[0]);
        assertEquals(1, progress.get(1)[0]);
        assertEquals(1, progress.get(2)[0]);
        assertEquals(2, progress.get(3)[0]);
        assertEquals(4, progress.get(3)[1]);
    }

    @Test
    public void nothingSavedLeavesTheDirectoryEmpty() throws Exception {
        File dir = Files.createTempDirectory("dl-none").toFile();
        int saved = WallpaperDownload.save(Arrays.asList(hit()), (hit, dest) -> {
            try (FileOutputStream out = new FileOutputStream(dest)) {
                out.write(new byte[]{1, 2, 3, 4});
            }
            return true;
        }, (source, dest, w, h) -> true, dir, 1920, 1080, null);
        assertEquals(0, saved);
        assertFalse(new File(dir, "00.jpg").exists());
        assertFalse(new File(dir, "raw.tmp").exists());
    }

    private static WallpaperSearch.Hit hit() {
        return new WallpaperSearch.Hit("https://w.wallhaven.cc/a.jpg", 3840, 2160, 100, "image/jpeg", "sfw");
    }
}
