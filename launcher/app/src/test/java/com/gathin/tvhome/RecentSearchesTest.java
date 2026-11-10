package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** The five names under the search field. */
public class RecentSearchesTest {
    @Test
    public void aRepeatMovesToTheFrontAndIgnoresCase() {
        List<String> once = RecentSearches.remember(null, "Lionel Messi");
        assertEquals(Collections.singletonList("Lionel Messi"), once);
        assertEquals(Collections.singletonList("lionel messi"), RecentSearches.remember(once, "  lionel messi  "));
    }

    @Test
    public void theSixthNameDropsTheOldest() {
        List<String> names = Collections.emptyList();
        for (String name : Arrays.asList("a", "b", "c", "d", "e", "f")) names = RecentSearches.remember(names, name);
        assertEquals(Arrays.asList("f", "e", "d", "c", "b"), names);
    }

    @Test
    public void aBlankNameChangesNothing() {
        List<String> names = RecentSearches.remember(null, "aurora");
        assertEquals(names, RecentSearches.remember(names, "  \n"));
    }
}
