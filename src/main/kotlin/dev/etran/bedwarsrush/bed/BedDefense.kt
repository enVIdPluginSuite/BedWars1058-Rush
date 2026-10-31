package dev.etran.bedwarsrush.bed

import com.andrei1058.bedwars.api.arena.IArena
import com.andrei1058.bedwars.api.arena.team.ITeam
import dev.etran.bedwarsrush.compat.Blocks
import dev.etran.bedwarsrush.compat.Materials
import dev.etran.bedwarsrush.config.BedDefenseSettings
import dev.etran.bedwarsrush.config.ReplaceMode
import org.bukkit.Material
import org.bukkit.block.Block

/** Wraps every team's bed in the configured concentric layers. */
object BedDefense {
    private val horizontal = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)

    /** @return total number of blocks placed */
    fun buildAll(arena: IArena, settings: BedDefenseSettings, warn: (String) -> Unit): Int {
        var total = 0
        for (team in arena.teams) {
            if (team.isBedDestroyed) continue
            total += build(arena, team, settings, warn)
        }
        return total
    }

    fun build(arena: IArena, team: ITeam, settings: BedDefenseSettings, warn: (String) -> Unit): Int {
        val core = coreBlocks(team)
        if (core.isEmpty()) {
            warn("Team ${team.name} in ${arena.arenaName} has no bed location; skipping its defense.")
            return 0
        }
        val world = core[0].world
        val corePositions = core.map { Pos(it.x, it.y, it.z) }
        val colorName = team.color.name
        val maxY = world.maxHeight - 1

        var distance = 1
        var placed = 0
        for (layer in settings.layers) {
            val spec = Materials.block(layer.material, colorName)
            repeat(layer.thickness) {
                if (spec != null && placed < settings.maxBlocksPerTeam) {
                    for (pos in BedShell.layer(corePositions, distance, settings.includeBelow)) {
                        if (pos.y > maxY || pos.y < 0) continue
                        val block = world.getBlockAt(pos.x, pos.y, pos.z)
                        if (!canReplace(block.type, settings.replace)) continue
                        if (settings.skipProtected && arena.isProtected(block.location)) continue
                        Blocks.place(block, spec)
                        arena.addPlacedBlock(block) // so players are allowed to break it again
                        if (++placed >= settings.maxBlocksPerTeam) break
                    }
                }
                distance++
            }
        }
        return placed
    }

    /** The bed's block(s): BedWars1058 stores one half, the other half is the neighbouring bed block. */
    fun coreBlocks(team: ITeam): List<Block> {
        val location = team.bed ?: return emptyList()
        val first = location.block
        val result = arrayListOf(first)
        if (Materials.isBed(first.type)) {
            for ((dx, dz) in horizontal) {
                val neighbour = first.getRelative(dx, 0, dz)
                if (Materials.isBed(neighbour.type)) {
                    result.add(neighbour)
                    break
                }
            }
        }
        return result
    }

    private fun canReplace(type: Material, mode: ReplaceMode): Boolean = when (mode) {
        ReplaceMode.AIR -> Materials.isAir(type)
        ReplaceMode.AIR_AND_LIQUID -> Materials.isAir(type) || Materials.isLiquid(type)
        ReplaceMode.ANY_EXCEPT_BED -> !Materials.isBed(type)
    }
}
