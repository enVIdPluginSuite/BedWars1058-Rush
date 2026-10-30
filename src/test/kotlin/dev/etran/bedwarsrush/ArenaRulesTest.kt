package dev.etran.bedwarsrush

import dev.etran.bedwarsrush.arena.ArenaRules
import dev.etran.bedwarsrush.arena.Reason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArenaRulesTest {
    private fun rules(groups: List<String> = emptyList(), wl: List<String> = emptyList(), bl: List<String> = emptyList(), only: Boolean = false) =
        ArenaRules(groups, wl, bl, only)

    @Test fun groupMatches() {
        val d = rules(groups = listOf("rush_solo")).evaluate(listOf("Gardens"), "RUSH_SOLO")
        assertTrue(d.rush); assertEquals(Reason.GROUP, d.reason)
    }
    @Test fun noMatchIsNormal() {
        val d = rules(groups = listOf("rush_solo")).evaluate(listOf("Gardens"), "Default")
        assertFalse(d.rush); assertEquals(Reason.NO_MATCH, d.reason)
    }
    @Test fun whitelistForcesRushOutsideGroups() {
        val d = rules(wl = listOf("Gardens")).evaluate(listOf("gardens"), "Default")
        assertTrue(d.rush); assertEquals(Reason.WHITELISTED, d.reason)
    }
    @Test fun blacklistBeatsGroupAndWhitelist() {
        val r = rules(groups = listOf("rush_*"), wl = listOf("Gardens"), bl = listOf("Gardens"))
        val d = r.evaluate(listOf("Gardens"), "rush_solo")
        assertFalse(d.rush); assertEquals(Reason.BLACKLISTED, d.reason)
    }
    @Test fun whitelistOnlyIgnoresGroups() {
        val r = rules(groups = listOf("rush_solo"), wl = listOf("Gardens"), only = true)
        assertFalse(r.evaluate(listOf("Aquarium"), "rush_solo").rush)
        assertEquals(Reason.NOT_WHITELISTED, r.evaluate(listOf("Aquarium"), "rush_solo").reason)
        assertTrue(r.evaluate(listOf("Gardens"), "other").rush)
    }
    @Test fun anyAlternateNameCanMatch() {
        val r = rules(bl = listOf("Gardens_*"))
        assertFalse(r.evaluate(listOf("Gardens", "Gardens_17"), "rush_solo").rush)
    }
    @Test fun wildcardsAndBlankEntries() {
        val r = rules(groups = listOf("  ", "rush_*"), bl = listOf("*_test"))
        assertTrue(r.evaluate(listOf("a"), "rush_doubles").rush)
        assertFalse(r.evaluate(listOf("map_test"), "rush_doubles").rush)
        assertEquals(listOf("rush_*"), r.groups)
    }
    @Test fun nullGroupIsFine() = assertFalse(rules(groups = listOf("*")).evaluate(listOf("a"), null).rush)
}
