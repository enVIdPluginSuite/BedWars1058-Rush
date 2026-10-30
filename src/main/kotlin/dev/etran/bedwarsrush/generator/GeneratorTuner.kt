package dev.etran.bedwarsrush.generator

import com.andrei1058.bedwars.api.arena.IArena
import com.andrei1058.bedwars.api.arena.generator.GeneratorType
import com.andrei1058.bedwars.api.arena.generator.IGenerator
import dev.etran.bedwarsrush.compat.Materials
import dev.etran.bedwarsrush.config.GenTune
import dev.etran.bedwarsrush.config.GeneratorSettings
import org.bukkit.inventory.ItemStack

/**
 * Adjusts BedWars1058's *own* generators (team iron/gold/emerald, map diamond/emerald).
 *
 * [tune] is idempotent - it only writes values that differ - so it is safe to call repeatedly to defend the configured
 * numbers against BedWars1058's tier events and shop upgrades. [tierUp] must run exactly once per game.
 */
object GeneratorTuner {
    fun tune(arena: IArena, s: GeneratorSettings) {
        for (team in arena.teams) {
            for (gen in team.generators) {
                when (gen.type) {
                    GeneratorType.IRON -> apply(gen, s.teamIron)
                    GeneratorType.GOLD -> apply(gen, s.teamGold)
                    GeneratorType.EMERALD -> apply(gen, s.teamEmerald)
                    else -> Unit
                }
            }
        }
        for (gen in arena.oreGenerators) {
            when (gen.type) {
                GeneratorType.DIAMOND -> apply(gen, s.globalDiamond)
                GeneratorType.EMERALD -> apply(gen, s.globalEmerald)
                else -> Unit
            }
        }
    }

    fun tierUp(arena: IArena, s: GeneratorSettings) {
        for (gen in arena.oreGenerators) {
            val tune = when (gen.type) {
                GeneratorType.DIAMOND -> s.globalDiamond
                GeneratorType.EMERALD -> s.globalEmerald
                else -> continue
            }
            repeat(tune.tierUps) { gen.upgrade() }
        }
    }

    private fun apply(gen: IGenerator, t: GenTune) {
        if (t.isEmpty) return

        t.material?.let { name ->
            val material = Materials.resolve(name)
            if (material != null && gen.ore?.type != material) gen.setOre(ItemStack(material))
        }
        t.delaySeconds?.let { delay ->
            if (gen.delay != delay) {
                gen.setDelay(delay)
                // Don't make the player wait out a countdown that was started with the old, slower delay.
                if (gen.nextSpawn > delay) gen.setNextSpawn(delay)
            }
        }
        t.amount?.let { if (gen.amount != it) gen.setAmount(it) }
        t.spawnLimit?.let { if (gen.spawnLimit != it) gen.setSpawnLimit(it) }
    }
}
