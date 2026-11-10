package com.gathin.tvhome;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Per-app dock wallpapers ({@code app_wallpapers} in a theme's config.json). Filenames are files in
 * the theme's {@code wallpapers/} folder, accepted only when {@link ThemeFiles} accepts that path.
 * The choice of which picture to show is pure: the player decodes and fades, this class only decides.
 */
final class AppWallpapers {
    /** How long focus must sit still before a dock move changes the picture. */
    static final long DEBOUNCE_MS = 200;
    /** Cross-fade, in the same range as the caption and shelf fades. */
    static final int FADE_MS = 350;
    /** Same byte cap as {@link WallpaperRotation#images}. */
    static final long MAX_BYTES = 10L * 1024 * 1024;

    private static final Pattern PACKAGE = Pattern.compile("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+");

    private AppWallpapers() {}

    /**
     * The filename to keep for this config entry, or null. A key that is not a package name is
     * ignored. A value that is not a string, or a filename {@link ThemeFiles} refuses (traversal,
     * a slash, the wrong extension), is left out.
     */
    static String keep(String key, Object value) {
        if (key == null || !(value instanceof String)) return null;
        String pkg = key.trim();
        if (!PACKAGE.matcher(pkg).matches()) return null;
        String name = ((String) value).trim();
        if (ThemeFiles.path(Arrays.asList(ThemeFiles.WALLPAPERS, name)) == null) return null;
        return name;
    }

    /** Package to filename. A null table (the key was missing) is an empty map. */
    static Map<String, String> parse(Object[][] rows) {
        if (rows == null) return Collections.emptyMap();
        Map<String, String> out = new LinkedHashMap<>();
        for (Object[] row : rows) {
            if (row == null || row.length < 2 || !(row[0] instanceof String)) continue;
            String key = (String) row[0];
            String name = keep(key, row[1]);
            if (name != null) out.put(key.trim(), name);
        }
        return Collections.unmodifiableMap(out);
    }

    /**
     * The wallpapers/ filename for this focus, or null when the rotation picture should stay.
     * A null package is focus that is not a dock app (a button, a grid row, an empty slot).
     */
    static String forFocus(Map<String, String> map, String pkg) {
        if (map == null || pkg == null) return null;
        return map.get(pkg);
    }

    /** True while an app picture is the one on screen, so a rotation tick must not replace it. */
    static boolean rotationPaused(String appWallpaper) {
        return appWallpaper != null;
    }

    /**
     * The filename to draw. An app picture wins, and a due rotation is not applied until focus leaves
     * ({@code appWallpaper} null), which is when the rotation carries on from where the clock is.
     */
    static String shown(String appWallpaper, String rotationCurrent, String rotationNext, boolean due) {
        if (appWallpaper != null) return appWallpaper;
        if (due && rotationNext != null) return rotationNext;
        return rotationCurrent;
    }

    /** True once focus has been still for {@link #DEBOUNCE_MS}. */
    static boolean settled(long movedAtMillis, long nowMillis) {
        return nowMillis - movedAtMillis >= DEBOUNCE_MS;
    }

    static boolean acceptedSize(long length) {
        return length > 0 && length <= MAX_BYTES;
    }

    /**
     * The file {@code name} names inside {@code directory} (the theme's wallpapers folder), or null
     * when the name is refused, the file is missing, or it is outside the size cap. The canonical
     * path has to stay in that folder.
     */
    static File resolve(File directory, String name) {
        if (directory == null || name == null) return null;
        if (ThemeFiles.path(Arrays.asList(ThemeFiles.WALLPAPERS, name)) == null) return null;
        try {
            File dir = directory.getCanonicalFile();
            File file = new File(dir, name).getCanonicalFile();
            File parent = file.getParentFile();
            if (parent == null || !parent.equals(dir)) return null;
            if (!file.isFile() || !acceptedSize(file.length())) return null;
            return file;
        } catch (IOException e) {
            return null;
        }
    }
}
