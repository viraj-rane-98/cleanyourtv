package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The last few names searched on the finder, newest first, one entry per name ignoring case. */
final class RecentSearches {
    static final int MAX = 5;

    private RecentSearches() {}

    static List<String> remember(List<String> existing, String name) {
        String trimmed = clean(name);
        List<String> out = new ArrayList<>();
        if (!trimmed.isEmpty()) out.add(trimmed);
        if (existing != null) {
            for (String old : existing) {
                String kept = clean(old);
                if (kept.isEmpty() || kept.equalsIgnoreCase(trimmed)) continue;
                out.add(kept);
                if (out.size() == MAX) break;
            }
        }
        return Collections.unmodifiableList(out);
    }

    private static String clean(String name) {
        if (name == null) return "";
        return name.replace('\n', ' ').replace('\r', ' ').trim();
    }
}
