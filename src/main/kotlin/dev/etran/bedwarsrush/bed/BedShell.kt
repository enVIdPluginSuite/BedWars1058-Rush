package dev.etran.bedwarsrush.bed

import dev.etran.bedwarsrush.config.BedShape
import kotlin.math.abs
import kotlin.math.max

data class Pos(val x: Int, val y: Int, val z: Int)

/**
 * Pure geometry for the bed defense, kept free of Bukkit so it can be unit tested.
 *
 * Layer `d` (1-based) is the set of cells at distance exactly `d` from the bed:
 *  - [BedShape.DIAMOND]: distance = straight-line steps (|dx|+|dy|+|dz|) to the nearest bed block, so no diagonal steps.
 *    Stacked layers form a gem (an octahedron stretched along the bed); with [includeBelow] off only the top half is built.
 *  - [BedShape.BOX]: distance = the largest of |dx|,|dy|,|dz| to the bed's bounding box, i.e. hollow cuboids.
 *
 * Either way every cell of layer d+1 touches a cell of layer d face to face, so the layers enclose the bed without gaps.
 */
object BedShell {
    fun layer(core: Collection<Pos>, distance: Int, includeBelow: Boolean, shape: BedShape = BedShape.DIAMOND): List<Pos> {
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
                    val d = when (shape) {
                        BedShape.DIAMOND -> core.minOf { abs(x - it.x) + abs(y - it.y) + abs(z - it.z) }
                        BedShape.BOX -> {
                            val dx = max(max(minX - x, x - maxX), 0)
                            val dy = max(max(minY - y, y - maxY), 0)
                            val dz = max(max(minZ - z, z - maxZ), 0)
                            max(dx, max(dy, dz))
                        }
                    }
                    if (d == distance) result.add(Pos(x, y, z))
                }
            }
        }
        return result
    }
}
