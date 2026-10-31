package dev.etran.bedwarsrush

import dev.etran.bedwarsrush.config.BridgeToggle
import dev.etran.bedwarsrush.config.INFINITE_TICKS
import dev.etran.bedwarsrush.config.LoadResult
import dev.etran.bedwarsrush.config.LocationSource
import dev.etran.bedwarsrush.config.ReplaceMode
import dev.etran.bedwarsrush.config.RushConfig
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ConfigTest {
    private fun load(text: String): LoadResult {
        val f = File.createTempFile("rush", ".yml")
        f.deleteOnExit()
        f.writeText(text)
        return RushConfig.load(f)
    }

    private fun shipped(): RushConfig {
        val f = File("src/main/resources/config.yml")
        val r = RushConfig.load(f)
        assertIs<LoadResult.Success>(r)
        return r.config
    }

    @Test fun shippedConfigHasNoWarningsAndMatchesTheOriginalRushDefaults() {
        val c = shipped()
        assertEquals(emptyList(), c.warnings)
        assertEquals(listOf("rush_solo"), c.groups)
        val s = c.settingsFor(listOf("Any"), "rush_solo")
        assertEquals("BEDS_DESTROY", s.game.nextEvent)
        assertEquals(3, s.game.teamUpgrades["upgrade-forge"])
        assertEquals(listOf("SPEED", "FAST_DIGGING"), s.game.teamEffects.map { it.type })
        assertEquals(INFINITE_TICKS, s.game.teamEffects[0].durationTicks)
        assertEquals(listOf("WOOD", "WOOL", "GLASS"), s.bedDefense.layers.map { it.material })
        assertEquals(ReplaceMode.AIR, s.bedDefense.replace)
        assertEquals(BridgeToggle.LEFT_CLICK, s.bridge.toggle)
        assertEquals(5, s.bridge.extraBlocks)
        assertEquals(2, s.generators.globalDiamond.tierUps)
        assertEquals(1, s.generators.teamIron.delaySeconds)
        assertEquals(2, s.generators.teamIron.amount)
        assertEquals(3, s.generators.teamGold.delaySeconds)
        val extra = s.generators.extras.single()
        assertEquals(LocationSource.TEAM_EMERALD, extra.source)
        assertEquals(15.0, extra.drops.single().delaySeconds)
        assertEquals(8, extra.drops.single().spawnLimit)
    }

    @Test fun groupAndArenaOverridesLayerPerLeaf() {
        val r = load(
            """
            bed-defense: {layers: [WOOD, WOOL, GLASS]}
            generators:
              team: {iron: {delay: 2, amount: 1}}
            overrides:
              groups:
                rush_doubles:
                  generators: {team: {iron: {delay: 1}}}
              arenas:
                Gardens:
                  bed-defense: {layers: [END_STONE]}
                "Gar*":
                  generators: {team: {iron: {delay: 9, amount: 4}}}
            """.trimIndent(),
        )
        val c = (r as LoadResult.Success).config
        // plain arena: global values
        assertEquals(2, c.settingsFor(listOf("Other"), "x").generators.teamIron.delaySeconds)
        // group override changes only delay
        val g = c.settingsFor(listOf("Other"), "rush_doubles").generators.teamIron
        assertEquals(1, g.delaySeconds); assertEquals(1, g.amount)
        // exact arena beats wildcard arena beats group; leaves merge across layers
        val a = c.settingsFor(listOf("Gardens"), "rush_doubles")
        assertEquals(listOf("END_STONE"), a.bedDefense.layers.map { it.material })
        assertEquals(9, a.generators.teamIron.delaySeconds)   // from "Gar*" (beats group)
        assertEquals(4, a.generators.teamIron.amount)
        assertEquals(3, c.overrideCount)
    }

    @Test fun badValuesWarnAndFallBack() {
        val r = load(
            """
            game: {next-event: NONE, team-effects: {SPEED: {duration: abc}}}
            bed-defense: {replace: SOMETIMES, layers: [NOT_A_BLOCK, WOOL]}
            bridge: {toggle: SHIFT}
            generators:
              team: {iron: {delay: 0}}
              extra:
                broken: {location: NOWHERE, drops: {a: {material: EMERALD}}}
                nodrops: {location: TEAM_IRON, drops: {a: {material: NOT_AN_ITEM}}}
            """.trimIndent(),
        )
        val c = (r as LoadResult.Success).config
        val joined = c.warnings.joinToString("\n")
        for (needle in listOf("duration", "bed-defense.replace", "NOT_A_BLOCK", "bridge.toggle", "generators.team.iron.delay", "broken", "nodrops")) {
            assertTrue(needle in joined, "expected a warning mentioning '$needle' in:\n$joined")
        }
        val s = c.settingsFor(listOf("a"), "g")
        assertEquals(null, s.game.nextEvent)
        assertEquals(ReplaceMode.AIR, s.bedDefense.replace)
        assertEquals(null, s.generators.teamIron.delaySeconds)
        assertTrue(s.generators.extras.isEmpty())
    }

    @Test fun invalidYamlIsReportedNotThrown() {
        val r = load("arenas: [unclosed")
        assertIs<LoadResult.Failure>(r)
        assertFalse(r.message.isBlank())
    }

    @Test fun overrideCanDisableAnExtraGeneratorForOneArena() {
        val c = shipped()
        val r = load(
            File("src/main/resources/config.yml").readText().replace(
                "overrides:\n  groups: {}\n",
                "overrides:\n  groups: {}\n  arenas:\n    NoEmerald:\n      generators: {extra: {team-emerald: {enabled: false}}}\n",
            ).replace("  arenas: {}\n", ""),
        )
        val cfg = (r as LoadResult.Success).config
        assertEquals(1, c.settingsFor(listOf("Gardens"), "g").generators.extras.size)
        assertNotNull(cfg)
        assertEquals(0, cfg.settingsFor(listOf("NoEmerald"), "g").generators.extras.size)
        assertEquals(1, cfg.settingsFor(listOf("Gardens"), "g").generators.extras.size)
    }
}
