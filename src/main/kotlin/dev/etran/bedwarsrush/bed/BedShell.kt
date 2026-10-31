package dev.etran.bedwarsrush.bed

import kotlin.math.max

data class Pos(val x: Int, val y: Int, val z: Int)

/**
 * Pure geometry for the bed defense, kept free of Bukkit so it can be unit tested.
 *
 * Layer `d` (1-based) is the hollow box of cells whose Chebyshev distance from the bed's bounding box is exactly `d`.
 * Stacking layers 1..n therefore gives the bed concentric shells, each fully enclosing the one inside it, with no
 * gaps at corners. Cells below the bed are only included when [includeBelow] is set (normally the island is there).
 */
object BedShell {
    fun layer(core: Collection<Pos>, distance: Int, includeBelow: Boolean): List<Pos> {
        require(core.isNotEmpty()) { "core must not be empty" }
        require(distance >= 1) { "distance must be >= 1" }

        val minX = core.minOf { it.x }
        val maxX = core.maxOf { it.x }
        val minY = core.minOf { it.y }
        val maxY = core.maxOf { it.y }
        val minZ = core.minOf { it.z }
        val maxZ = core.maxOf { it.z }

        val yStart = if (includeBelow) minY - distance else minY
        val result = ArrayList<Pos>()
        for (y in yStart..(maxY + distance)) {
            for (x in (minX - distance)..(maxX + distance)) {
                for (z in (minZ - distance)..(maxZ + distance)) {
                    val dx = max(max(minX - x, x - maxX), 0)
                    val dy = max(max(minY - y, y - maxY), 0)
                    val dz = max(max(minZ - z, z - maxZ), 0)
                    if (max(dx, max(dy, dz)) == distance) result.add(Pos(x, y, z))
                }
            }
        }
        return result
    }
}
