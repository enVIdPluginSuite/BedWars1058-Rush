package dev.etran.bedwarsrush

import dev.etran.bedwarsrush.util.Glob
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GlobTest {
    @Test fun exactIsCaseInsensitive() = assertTrue(Glob.matches("Gardens", "gARDENS"))
    @Test fun starMatchesAnyRun() {
        assertTrue(Glob.matches("rush_*", "rush_solo"))
        assertTrue(Glob.matches("*WOOL", "RED_WOOL"))
        assertTrue(Glob.matches("*WOOL", "WOOL"))
        assertTrue(Glob.matches("a*b*c", "aXXbYYc"))
        assertTrue(Glob.matches("*", ""))
    }
    @Test fun questionMatchesOne() {
        assertTrue(Glob.matches("map?", "map1"))
        assertFalse(Glob.matches("map?", "map12"))
    }
    @Test fun nonMatches() {
        assertFalse(Glob.matches("rush_*", "solo_rush"))
        assertFalse(Glob.matches("abc", "abcd"))
        assertFalse(Glob.matches("*WOOL", "WOOL_CARPET"))
    }
    @Test fun firstMatchReturnsPattern() = assertEquals("r*", Glob.firstMatch(listOf("x", "r*"), "red"))
}
