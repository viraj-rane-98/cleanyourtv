package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.Map;

/** app_wallpapers: which dock app shows which picture, and when the rotation is allowed to move. */
public class AppWallpapersTest {
    private static final String NETFLIX = "com.netflix.ninja";
    private static final String YOUTUBE = "com.google.android.youtube.tv";
    private static final String PRIME = "com.amazon.amazonvideo.livingroom";
    private static final String HOTSTAR = "in.startv.hotstar";

    private static Map<String, String> parse(Object[][] rows) {
        return AppWallpapers.parse(rows);
    }

    @Test
    public void aValidMapKeepsTheDockPackages() {
        Map<String, String> map = parse(new Object[][]{
                {NETFLIX, "1-netflix.jpg"},
                {YOUTUBE, "2-youtube.jpg"},
                {PRIME, "3-prime.png"},
                {HOTSTAR, "4-hotstar.webp"},
        });
        assertEquals("1-netflix.jpg", map.get(NETFLIX));
        assertEquals("2-youtube.jpg", map.get(YOUTUBE));
        assertEquals("3-prime.png", map.get(PRIME));
        assertEquals("4-hotstar.webp", map.get(HOTSTAR));
        assertEquals(4, map.size());
    }

    @Test
    public void aMissingKeyIsNoMap() {
        assertTrue(AppWallpapers.parse(null).isEmpty());
    }

    @Test
    public void aBadFilenameOrTraversalIsLeftOut() {
        Map<String, String> map = parse(new Object[][]{
                {NETFLIX, "../secret.jpg"},
                {YOUTUBE, "wallpapers/2-youtube.jpg"},
                {PRIME, "a b.jpg"},
                {HOTSTAR, "notes.txt"},
                {"com.disney.disneyplus", "1-netflix.jpg"},
        });
        assertEquals(1, map.size());
        assertEquals("1-netflix.jpg", map.get("com.disney.disneyplus"));
        assertNull(map.get(NETFLIX));
        assertNull(map.get(YOUTUBE));
    }

    @Test
    public void anUnknownPackageIsIgnored() {
        Map<String, String> map = parse(new Object[][]{
                {"netflix", "1-netflix.jpg"},
                {"", "1-netflix.jpg"},
                {"com", "1-netflix.jpg"},
                {NETFLIX, 1},
                {YOUTUBE, "2-youtube.jpg"},
        });
        assertEquals(1, map.size());
        assertEquals("2-youtube.jpg", map.get(YOUTUBE));
        assertFalse(map.containsKey("netflix"));
    }

    @Test
    public void focusPicksTheMappedPictureOrTheRotation() {
        Map<String, String> map = parse(new Object[][]{{NETFLIX, "1-netflix.jpg"}});
        assertEquals("1-netflix.jpg", AppWallpapers.forFocus(map, NETFLIX));
        assertNull(AppWallpapers.forFocus(map, YOUTUBE));
        assertNull(AppWallpapers.forFocus(map, null));
        assertNull(AppWallpapers.forFocus(null, NETFLIX));
    }

    @Test
    public void anAppPictureHoldsTheRotation() {
        assertEquals("1-netflix.jpg", AppWallpapers.shown("1-netflix.jpg", "a.jpg", "b.jpg", true));
        assertTrue(AppWallpapers.rotationPaused("1-netflix.jpg"));
        // Focus left: a tick that came due while the app picture was up moves on, one that did not stays.
        assertEquals("b.jpg", AppWallpapers.shown(null, "a.jpg", "b.jpg", true));
        assertEquals("a.jpg", AppWallpapers.shown(null, "a.jpg", "b.jpg", false));
        assertFalse(AppWallpapers.rotationPaused(null));
    }

    @Test
    public void aFastMoveHasNotSettledUntilTheDebounce() {
        assertFalse(AppWallpapers.settled(1_000, 1_000 + AppWallpapers.DEBOUNCE_MS - 1));
        assertTrue(AppWallpapers.settled(1_000, 1_000 + AppWallpapers.DEBOUNCE_MS));
        assertTrue(AppWallpapers.FADE_MS >= 300 && AppWallpapers.FADE_MS <= 400);
    }

    @Test
    public void aMissingOrOversizedFileDoesNotResolve() throws Exception {
        File dir = Files.createTempDirectory("app-walls").toFile();
        assertNull(AppWallpapers.resolve(dir, "1-netflix.jpg"));
        File empty = new File(dir, "1-netflix.jpg");
        assertTrue(empty.createNewFile());
        assertNull(AppWallpapers.resolve(dir, "1-netflix.jpg"));
        try (FileOutputStream out = new FileOutputStream(empty)) {
            out.write(new byte[]{1, 2, 3, 4});
        }
        assertEquals(empty.getCanonicalFile(), AppWallpapers.resolve(dir, "1-netflix.jpg"));
        assertNull(AppWallpapers.resolve(dir, "../1-netflix.jpg"));
        assertNull(AppWallpapers.resolve(dir, "notes.txt"));
        assertFalse(AppWallpapers.acceptedSize(0));
        assertFalse(AppWallpapers.acceptedSize(AppWallpapers.MAX_BYTES + 1));
        assertTrue(AppWallpapers.acceptedSize(AppWallpapers.MAX_BYTES));
    }
}
