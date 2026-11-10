package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The finder's private files, under the app's files directory. The theme folder is never written.
 * A set becomes current only when its pointer file is replaced, so a half-finished search stays invisible.
 * The byte cap applies to the downloaded sets, not the one-time theme copy beside them.
 */
final class WallpaperSets {
    static final String ROOT = "wallpaper-finder";
    static final long CAP_BYTES = 60L * 1024 * 1024;

    private static final String SETS = "sets";
    private static final String BACKUP = "backup";
    private static final String READY = "ready";
    private static final String CURRENT = "current";
    private static final String PREVIOUS = "previous";
    private static final String INCOMING = "incoming";
    private static final Pattern ID = Pattern.compile("[A-Za-z0-9_-]{1,40}");

    private WallpaperSets() {}

    static File root(File filesDir) {
        return filesDir == null ? null : new File(filesDir, ROOT);
    }

    /** A fresh directory for the search in progress. Anything left from a crashed search is removed. */
    static File incoming(File root) {
        File dir = new File(root, INCOMING);
        deleteTree(dir);
        dir.mkdirs();
        return dir;
    }

    static List<File> images(File dir) {
        return WallpaperRotation.images(dir);
    }

    /** The set Home should draw, or null when the theme's own files should. */
    static File activeDir(File root) {
        if (root == null) return null;
        String id = readPointer(new File(root, CURRENT));
        if (!ID.matcher(id).matches()) return null;
        try {
            File sets = new File(root, SETS).getCanonicalFile();
            File dir = new File(sets, id).getCanonicalFile();
            File parent = dir.getParentFile();
            if (parent == null || !parent.equals(sets)) return null;
            if (!new File(dir, READY).isFile()) return null;
            return dir;
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Publishes {@code staging} as the current set and keeps the one it replaced as the previous set.
     * Returns false without changing the current pointer when there is nothing to publish.
     */
    static boolean commit(File root, File staging, long cap) {
        if (root == null || staging == null || !staging.isDirectory()) return false;
        if (images(staging).isEmpty()) {
            deleteTree(staging);
            return false;
        }
        try {
            File ready = new File(staging, READY);
            if (!ready.isFile() && !ready.createNewFile()) return false;
        } catch (IOException e) {
            return false;
        }
        File sets = new File(root, SETS);
        if (!sets.isDirectory() && !sets.mkdirs()) return false;
        String id = newId(sets);
        File dest = new File(sets, id);
        if (!staging.renameTo(dest)) return false;
        String old = readPointer(new File(root, CURRENT));
        if (ID.matcher(old).matches()) writePointer(root, PREVIOUS, old);
        if (!writePointer(root, CURRENT, id)) {
            trim(root, cap);
            return false;
        }
        trim(root, cap);
        return activeDir(root) != null;
    }

    /** Stops preferring the overlay. The theme folder is not touched; the theme copy is kept. */
    static void restore(File root) {
        if (root == null) return;
        writePointer(root, CURRENT, "");
        trim(root, CAP_BYTES);
    }

    /**
     * Copies the theme's wallpaper and app-wallpaper files once, before the first set is published.
     * A later call leaves that copy as it was.
     */
    static boolean backupOnce(File root, List<File> sources) {
        if (root == null) return false;
        File dir = new File(root, BACKUP);
        File ready = new File(dir, READY);
        if (ready.isFile()) return true;
        if (!dir.isDirectory() && !dir.mkdirs()) return false;
        try {
            if (sources != null) {
                for (File src : sources) {
                    if (src == null || !src.isFile()) continue;
                    String name = src.getName();
                    if (!ThemeFiles.collectionImage(name)) continue;
                    copy(src, new File(dir, name));
                }
            }
            return ready.createNewFile() || ready.isFile();
        } catch (IOException e) {
            return false;
        }
    }

    /** {@code wallpaper.jpg} plus each app-wallpaper file that is actually in the theme folder. */
    static List<File> themeSources(File themeDir, List<String> appNames) {
        List<File> out = new ArrayList<>();
        if (themeDir == null) return out;
        File single = new File(themeDir, Config.WALLPAPER_NAME);
        if (single.isFile()) out.add(single);
        File walls = new File(themeDir, ThemeFiles.WALLPAPERS);
        if (appNames != null) {
            for (String name : appNames) {
                File file = AppWallpapers.resolve(walls, name);
                if (file != null && !out.contains(file)) out.add(file);
            }
        }
        return out;
    }

    /** Filenames from the theme's own config.json, not from the overlay Home is drawing. */
    static List<String> appNames(File themeDir) {
        List<String> names = new ArrayList<>();
        if (themeDir == null) return names;
        File file = new File(themeDir, Config.FILE_NAME);
        if (!file.isFile() || file.length() <= 0 || file.length() > 1024 * 1024) return names;
        try {
            String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            JSONObject map = new JSONObject(text).optJSONObject("app_wallpapers");
            if (map == null) return names;
            JSONArray keys = map.names();
            if (keys == null) return names;
            for (int i = 0; i < keys.length(); i++) {
                String key = keys.optString(i, "");
                String kept = AppWallpapers.keep(key, map.opt(key));
                if (kept != null) names.add(kept);
            }
        } catch (IOException | JSONException e) {
            return names;
        }
        return names;
    }

    /** Drops sets other than the current and previous ones, then the previous one if the two exceed {@code cap}. */
    static void trim(File root, long cap) {
        if (root == null) return;
        String current = readPointer(new File(root, CURRENT));
        String previous = readPointer(new File(root, PREVIOUS));
        File sets = new File(root, SETS);
        File[] dirs = sets.listFiles(File::isDirectory);
        if (dirs != null) {
            for (File dir : dirs) {
                String name = dir.getName();
                if (!name.equals(current) && !name.equals(previous)) deleteTree(dir);
            }
        }
        if (!previous.isEmpty() && !previous.equals(current)) {
            long used = bytes(new File(sets, current)) + bytes(new File(sets, previous));
            if (used > cap) {
                deleteTree(new File(sets, previous));
                writePointer(root, PREVIOUS, "");
            }
        }
    }

    static long bytes(File file) {
        if (file == null || !file.exists()) return 0;
        if (file.isFile()) return file.length();
        long n = 0;
        File[] kids = file.listFiles();
        if (kids != null) for (File kid : kids) n += bytes(kid);
        return n;
    }

    static String readPointer(File file) {
        try {
            if (file == null || !file.isFile() || file.length() > 64) return "";
            byte[] bytes = Files.readAllBytes(file.toPath());
            return new String(bytes, StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            return "";
        }
    }

    private static String newId(File sets) {
        for (int i = 0; i < 100; i++) {
            String id = "s" + System.nanoTime();
            if (ID.matcher(id).matches() && !new File(sets, id).exists()) return id;
        }
        return "s" + System.nanoTime();
    }

    static boolean writePointer(File root, String name, String id) {
        try {
            if (!root.isDirectory() && !root.mkdirs()) return false;
            File tmp = new File(root, name + ".tmp");
            Files.write(tmp.toPath(), (id == null ? "" : id).getBytes(StandardCharsets.UTF_8));
            Files.move(tmp.toPath(), new File(root, name).toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static void copy(File src, File dest) throws IOException {
        try (InputStream in = new FileInputStream(src); OutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
        }
    }

    static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        File[] kids = file.listFiles();
        if (kids != null) for (File kid : kids) deleteTree(kid);
        file.delete();
    }
}
