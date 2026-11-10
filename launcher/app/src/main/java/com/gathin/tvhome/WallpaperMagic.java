package com.gathin.tvhome;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/** JPEG and PNG signatures. Anything else is not saved. */
final class WallpaperMagic {
    private WallpaperMagic() {}

    static boolean jpegOrPng(byte[] header, int length) {
        if (header == null || length < 3 || header.length < length) return false;
        if (jpeg(header)) return true;
        if (length < 8 || header.length < 8) return false;
        return (header[0] & 0xFF) == 0x89 && header[1] == 'P' && header[2] == 'N' && header[3] == 'G'
                && header[4] == 0x0D && header[5] == 0x0A && header[6] == 0x1A && header[7] == 0x0A;
    }

    static boolean jpegOrPng(File file) {
        byte[] header = new byte[8];
        try (InputStream in = new FileInputStream(file)) {
            int n = in.read(header);
            return jpegOrPng(header, n);
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean jpeg(byte[] header) {
        return (header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF;
    }
}
