package dev.etran.bedwarsrush

import dev.etran.bedwarsrush.bed.BedShell
import dev.etran.bedwarsrush.bed.Pos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BedShellTest {
    private val bed = listOf(Pos(0, 64, 0), Pos(1, 64, 0)) // 2x1 bed

    @Test fun layerOneIsTheHollowBoxAroundTheBed() {
        val l1 = BedShell.layer(bed, 1, includeBelow = false).toSet()
        // box is x -1..2, z -1..1, y 64..65 -> 4*3*2 = 24 cells, minus the 2 bed cells = 22
        assertEquals(22, l1.size)
        assertFalse(Pos(0, 64, 0) in l1)
        assertTrue(Pos(0, 65, 0) in l1)     // lid
        assertTrue(Pos(-1, 64, -1) in l1)   // corner
        assertTrue(Pos(2, 65, 1) in l1)     // top corner
        assertFalse(l1.any { it.y < 64 })
    }

    @Test fun layersAreDisjointAndNested() {
        val all = (1..3).map { BedShell.layer(bed, it, false).toSet() }
        for (i in all.indices) for (j in i + 1 until all.size) assertTrue(all[i].intersect(all[j]).isEmpty())
        // Together layers 1..3 fill the box x -3..4, z -3..3, y 64..67 completely except the bed.
        val union = all.flatten().toSet()
        assertEquals(8 * 7 * 4 - 2, union.size)
    }

    @Test fun everyOuterCellTouchesAnInnerOrBedCell() {
        // "No gaps": any layer-2 cell is adjacent (incl. diagonal) to a layer-1 cell.
        val l1 = BedShell.layer(bed, 1, false).toSet()
        for (c in BedShell.layer(bed, 2, false)) {
            val touches = (-1..1).any { dx -> (-1..1).any { dy -> (-1..1).any { dz -> Pos(c.x + dx, c.y + dy, c.z + dz) in l1 } } }
            assertTrue(touches, "layer-2 cell $c is floating")
        }
    }

    @Test fun includeBelowAddsFloor() {
        val below = BedShell.layer(bed, 1, includeBelow = true)
        assertTrue(below.any { it.y == 63 })
        assertEquals(4 * 3 * 3 - 2, below.size)
    }

    @Test fun singleBlockCore() {
        assertEquals(3 * 3 * 2 - 1, BedShell.layer(listOf(Pos(5, 10, 5)), 1, false).size)
    }
}
