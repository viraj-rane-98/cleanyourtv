package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Which downloaded picture replaces which dock app. Fewer pictures than slots are reused in order. */
final class WallpaperSlots {
    private WallpaperSlots() {}

    /** Dock order first, then any mapped package the dock list does not name. */
    static List<String> order(List<String> dock, Collection<String> packages) {
        List<String> slots = new ArrayList<>();
        if (packages == null || packages.isEmpty()) return slots;
        if (dock != null) for (String pkg : dock) if (packages.contains(pkg) && !slots.contains(pkg)) slots.add(pkg);
        for (String pkg : packages) if (pkg != null && !slots.contains(pkg)) slots.add(pkg);
        return slots;
    }

    /** Package to image filename. An empty image list leaves every slot alone. */
    static Map<String, String> fill(List<String> slots, List<String> images) {
        Map<String, String> out = new LinkedHashMap<>();
        if (slots == null || images == null || images.isEmpty()) return out;
        int n = 0;
        for (String slot : slots) {
            if (slot == null) continue;
            String image = images.get(n % images.size());
            n++;
            if (image == null) continue;
            out.put(slot, image);
        }
        return Collections.unmodifiableMap(out);
    }
}
