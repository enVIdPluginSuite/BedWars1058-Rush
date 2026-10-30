package dev.etran.bedwarsrush

import dev.etran.bedwarsrush.bed.BedShell
import dev.etran.bedwarsrush.bed.Pos
import dev.etran.bedwarsrush.config.BedShape
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BedShellTest {
    private val bed = listOf(Pos(0, 64, 0), Pos(1, 64, 0)) // 2x1 bed

    @Test fun layerOneIsTheHollowBoxAroundTheBed() {
        val l1 = BedShell.layer(bed, 1, includeBelow = false, shape = BedShape.BOX).toSet()
        // box is x -1..2, z -1..1, y 64..65 -> 4*3*2 = 24 cells, minus the 2 bed cells = 22
        assertEquals(22, l1.size)
        assertFalse(Pos(0, 64, 0) in l1)
        assertTrue(Pos(0, 65, 0) in l1)     // lid
        assertTrue(Pos(-1, 64, -1) in l1)   // corner
        assertTrue(Pos(2, 65, 1) in l1)     // top corner
        assertFalse(l1.any { it.y < 64 })
    }

    @Test fun layersAreDisjointAndNested() {
        val all = (1..3).map { BedShell.layer(bed, it, false, BedShape.BOX).toSet() }
        for (i in all.indices) for (j in i + 1 until all.size) assertTrue(all[i].intersect(all[j]).isEmpty())
        // Together layers 1..3 fill the box x -3..4, z -3..3, y 64..67 completely except the bed.
        val union = all.flatten().toSet()
        assertEquals(8 * 7 * 4 - 2, union.size)
    }

    @Test fun everyOuterCellTouchesAnInnerOrBedCell() {
        // "No gaps": any layer-2 cell is adjacent (incl. diagonal) to a layer-1 cell.
        val l1 = BedShell.layer(bed, 1, false, BedShape.BOX).toSet()
        for (c in BedShell.layer(bed, 2, false, BedShape.BOX)) {
            val touches = (-1..1).any { dx -> (-1..1).any { dy -> (-1..1).any { dz -> Pos(c.x + dx, c.y + dy, c.z + dz) in l1 } } }
            assertTrue(touches, "layer-2 cell $c is floating")
        }
    }

    @Test fun includeBelowAddsFloor() {
        val below = BedShell.layer(bed, 1, includeBelow = true, shape = BedShape.BOX)
        assertTrue(below.any { it.y == 63 })
        assertEquals(4 * 3 * 3 - 2, below.size)
    }

    @Test fun singleBlockCore() {
        assertEquals(3 * 3 * 2 - 1, BedShell.layer(listOf(Pos(5, 10, 5)), 1, false, BedShape.BOX).size)
    }

    // ---- DIAMOND (the default): a gem, not a box ----

    @Test fun diamondLayerOneIsOnlyFaceNeighbours() {
        val l1 = BedShell.layer(bed, 1, includeBelow = false).toSet()
        assertEquals(
            setOf(
                Pos(0, 65, 0), Pos(1, 65, 0),                       // on top of each bed half
                Pos(-1, 64, 0), Pos(2, 64, 0),                      // ends
                Pos(0, 64, 1), Pos(1, 64, 1), Pos(0, 64, -1), Pos(1, 64, -1), // sides
            ),
            l1,
        )
        assertFalse(Pos(-1, 64, -1) in l1)  // no diagonal corner
        assertFalse(Pos(-1, 65, 0) in l1)
    }

    @Test fun diamondTapersWithHeight() {
        val l2 = BedShell.layer(bed, 2, false).toSet()
        assertTrue(Pos(0, 66, 0) in l2)       // straight up is 2 steps
        assertTrue(Pos(-2, 64, 0) in l2)
        assertFalse(Pos(-2, 66, 0) in l2)     // would be 4 steps away
        val l3 = BedShell.layer(bed, 3, false).toSet()
        assertTrue(Pos(-1, 65, -1) in l3)     // the "diagonal" cell only appears 3 steps out
        assertFalse(Pos(-1, 65, -1) in l2)
    }

    @Test fun diamondLayersAreDisjointAndSeamless() {
        val core = bed.toSet()
        var inner: Set<Pos> = core
        val seen = HashSet<Pos>(core)
        for (d in 1..4) {
            val layer = BedShell.layer(bed, d, includeBelow = false).toSet()
            assertTrue(layer.intersect(seen).isEmpty(), "layer $d overlaps an inner one")
            for (c in layer) {
                val faceTouch = listOf(Pos(1, 0, 0), Pos(-1, 0, 0), Pos(0, 1, 0), Pos(0, -1, 0), Pos(0, 0, 1), Pos(0, 0, -1))
                    .any { Pos(c.x + it.x, c.y + it.y, c.z + it.z) in inner }
                assertTrue(faceTouch, "layer-$d cell $c does not touch the layer inside it")
            }
            seen.addAll(layer)
            inner = layer
        }
    }

    @Test fun diamondIsSmallerThanBox() {
        val diamond = (1..3).sumOf { BedShell.layer(bed, it, false).size }
        val box = (1..3).sumOf { BedShell.layer(bed, it, false, BedShape.BOX).size }
        assertTrue(diamond < box)
    }

    @Test fun diamondBelowOnlyWhenAsked() {
        assertFalse(BedShell.layer(bed, 2, false).any { it.y < 64 })
        assertTrue(BedShell.layer(bed, 2, true).any { it.y < 64 })
    }
}
