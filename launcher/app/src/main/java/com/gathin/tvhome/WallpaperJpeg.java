package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Decodes a download at the size Home draws and writes a JPEG. The source file is the caller's to delete.
 * Quality stays 90 unless the file would be over the 10 MB the wallpaper reader skips.
 */
final class WallpaperJpeg {
    private WallpaperJpeg() {}

    static boolean save(File source, File dest, int screenW, int screenH) {
        if (source == null || dest == null || screenW <= 0 || screenH <= 0) return false;
        Bitmap decoded = null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(source.getPath(), bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false;
            int sample = WallpaperSize.sampleSize(bounds.outWidth, bounds.outHeight, screenW, screenH);
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            int sampledW = Math.max(1, bounds.outWidth / sample);
            int sampledH = Math.max(1, bounds.outHeight / sample);
            int[] scale = WallpaperSize.coverScale(sampledW, sampledH, screenW, screenH);
            if (scale != null) {
                opts.inScaled = true;
                opts.inDensity = scale[0];
                opts.inTargetDensity = scale[1];
            }
            decoded = BitmapFactory.decodeFile(source.getPath(), opts);
            if (decoded == null) return false;
            int quality = 90;
            if (!write(decoded, dest, quality)) return false;
            while (dest.length() > AppWallpapers.MAX_BYTES && quality > 60) {
                quality -= 10;
                if (!write(decoded, dest, quality)) return false;
            }
            if (dest.length() <= 0 || dest.length() > AppWallpapers.MAX_BYTES) {
                dest.delete();
                return false;
            }
            return true;
        } catch (Exception | OutOfMemoryError e) {
            if (dest.exists()) dest.delete();
            return false;
        } finally {
            if (decoded != null) decoded.recycle();
        }
    }

    private static boolean write(Bitmap bitmap, File dest, int quality) {
        try (FileOutputStream out = new FileOutputStream(dest)) {
            return bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out);
        } catch (IOException e) {
            return false;
        }
    }
}
