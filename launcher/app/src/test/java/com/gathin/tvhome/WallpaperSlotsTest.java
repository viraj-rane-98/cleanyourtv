package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/** Dock slots filled from the search, including when there are fewer pictures than apps. */
public class WallpaperSlotsTest {
    @Test
    public void fewerPicturesAreReusedInOrder() {
        List<String> slots = Arrays.asList(
                "com.netflix.ninja",
                "com.amazon.amazonvideo.livingroom",
                "com.google.android.youtube.tv",
                "in.startv.hotstar");
        assertEquals(Arrays.asList("00.jpg", "01.jpg", "00.jpg", "01.jpg"),
                Arrays.asList(WallpaperSlots.fill(slots, Arrays.asList("00.jpg", "01.jpg")).values().toArray()));
    }

    @Test
    public void extraPicturesDoNotAddSlots() {
        assertEquals("00.jpg", WallpaperSlots.fill(Arrays.asList("com.netflix.ninja", "com.google.android.youtube.tv"),
                Arrays.asList("00.jpg", "01.jpg", "02.jpg")).get("com.netflix.ninja"));
        assertEquals("01.jpg", WallpaperSlots.fill(Arrays.asList("com.netflix.ninja", "com.google.android.youtube.tv"),
                Arrays.asList("00.jpg", "01.jpg", "02.jpg")).get("com.google.android.youtube.tv"));
    }

    @Test
    public void noPicturesLeavesTheSlotsAlone() {
        assertTrue(WallpaperSlots.fill(Arrays.asList("com.netflix.ninja"), Collections.emptyList()).isEmpty());
        assertTrue(WallpaperSlots.fill(null, Arrays.asList("00.jpg")).isEmpty());
    }

    @Test
    public void dockOrderWinsOverTheOrderTheThemeNamedThem() {
        LinkedHashSet<String> named = new LinkedHashSet<>(Arrays.asList(
                "com.google.android.youtube.tv", "com.netflix.ninja", "in.startv.hotstar"));
        assertEquals(Arrays.asList("com.netflix.ninja", "com.google.android.youtube.tv", "in.startv.hotstar"),
                WallpaperSlots.order(Arrays.asList("com.netflix.ninja", "com.amazon.amazonvideo.livingroom",
                        "com.google.android.youtube.tv"), named));
    }
}
