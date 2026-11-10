package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.Arrays;

/** Private overlay: backup, atomic publish, the storage cap, and restore. */
public class WallpaperSetsTest {
    private static File temp() throws Exception {
        return Files.createTempDirectory("finder").toFile();
    }

    private static void jpg(File dir, String name, int bytes) throws Exception {
        byte[] data = new byte[bytes];
        data[0] = 1;
        try (FileOutputStream out = new FileOutputStream(new File(dir, name))) {
            out.write(data);
        }
    }

    private static byte[] read(File file) throws Exception {
        return Files.readAllBytes(file.toPath());
    }

    @Test
    public void aHalfFinishedSearchDoesNotReplaceTheCurrentSet() throws Exception {
        File root = temp();
        File first = WallpaperSets.incoming(root);
        jpg(first, "00.jpg", 32);
        assertTrue(WallpaperSets.commit(root, first, WallpaperSets.CAP_BYTES));
        String id = WallpaperSets.activeDir(root).getName();

        File next = WallpaperSets.incoming(root);
        jpg(next, "00.jpg", 32);
        assertEquals(id, WallpaperSets.activeDir(root).getName());
        assertTrue(new File(next, "00.jpg").isFile());

        assertTrue(WallpaperSets.commit(root, next, WallpaperSets.CAP_BYTES));
        assertFalse(id.equals(WallpaperSets.activeDir(root).getName()));
        assertTrue(new File(new File(root, "sets"), id).isDirectory());
    }

    @Test
    public void anEmptyOrUnreadySetIsNotPublished() throws Exception {
        File root = temp();
        File first = WallpaperSets.incoming(root);
        jpg(first, "00.jpg", 16);
        assertTrue(WallpaperSets.commit(root, first, WallpaperSets.CAP_BYTES));
        String id = WallpaperSets.activeDir(root).getName();

        File empty = WallpaperSets.incoming(root);
        assertFalse(WallpaperSets.commit(root, empty, WallpaperSets.CAP_BYTES));
        assertEquals(id, WallpaperSets.activeDir(root).getName());

        File evil = new File(new File(root, "sets"), "evil");
        assertTrue(evil.mkdirs());
        jpg(evil, "00.jpg", 16);
        assertTrue(WallpaperSets.writePointer(root, "current", "evil"));
        assertNull(WallpaperSets.activeDir(root));
        assertTrue(WallpaperSets.writePointer(root, "current", "../backup"));
        assertNull(WallpaperSets.activeDir(root));
    }

    @Test
    public void olderSetsAreDeletedAndTheCapDropsThePreviousOne() throws Exception {
        File root = temp();
        String first = publish(root, 40, 10_000_000);
        String second = publish(root, 40, 10_000_000);
        String third = publish(root, 40, 10_000_000);
        assertFalse(new File(new File(root, "sets"), first).exists());
        assertTrue(new File(new File(root, "sets"), second).isDirectory());
        assertEquals(third, WallpaperSets.activeDir(root).getName());

        File tight = temp();
        publish(tight, 80, 100);
        publish(tight, 80, 100);
        assertEquals("", WallpaperSets.readPointer(new File(tight, "previous")));
        assertTrue(WallpaperSets.bytes(new File(tight, "sets")) <= 100);
    }

    @Test
    public void backupIsTakenOnceAndRestorePutsTheThemeBack() throws Exception {
        File theme = temp();
        File wallpaper = new File(theme, "wallpaper.jpg");
        try (FileOutputStream out = new FileOutputStream(wallpaper)) {
            out.write(new byte[]{1, 2, 3, 4});
        }
        File walls = new File(theme, "wallpapers");
        assertTrue(walls.mkdirs());
        File app = new File(walls, "1.jpg");
        try (FileOutputStream out = new FileOutputStream(app)) {
            out.write(new byte[]{9, 8, 7});
        }
        jpg(walls, "unused.jpg", 12);

        File root = temp();
        assertEquals(Arrays.asList(wallpaper, app),
                WallpaperSets.themeSources(theme, Arrays.asList("1.jpg", "../secret.jpg", "notes.txt")));
        assertTrue(WallpaperSets.backupOnce(root, WallpaperSets.themeSources(theme, Arrays.asList("1.jpg"))));
        byte[] saved = read(new File(new File(root, "backup"), "wallpaper.jpg"));
        try (FileOutputStream out = new FileOutputStream(wallpaper)) {
            out.write(new byte[]{5, 5, 5, 5});
        }
        assertTrue(WallpaperSets.backupOnce(root, WallpaperSets.themeSources(theme, Arrays.asList("1.jpg"))));
        assertTrue(Arrays.equals(saved, read(new File(new File(root, "backup"), "wallpaper.jpg"))));
        assertFalse(new File(new File(root, "backup"), "unused.jpg").exists());

        publish(root, 24, WallpaperSets.CAP_BYTES);
        assertTrue(WallpaperSets.activeDir(root) != null);
        WallpaperSets.restore(root);
        assertNull(WallpaperSets.activeDir(root));
        assertTrue(Arrays.equals(saved, read(new File(new File(root, "backup"), "wallpaper.jpg"))));
        assertTrue(Arrays.equals(new byte[]{9, 8, 7}, read(new File(new File(root, "backup"), "1.jpg"))));
    }

    private static String publish(File root, int bytes, long cap) throws Exception {
        File staging = WallpaperSets.incoming(root);
        jpg(staging, "00.jpg", bytes);
        assertTrue(WallpaperSets.commit(root, staging, cap));
        return WallpaperSets.activeDir(root).getName();
    }
}
