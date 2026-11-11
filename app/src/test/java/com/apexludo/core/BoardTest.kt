package com.apexludo.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardTest {

    @Test
    fun ringHas52UniqueCells() {
        val ring = Board.ring()
        assertEquals(52, ring.size)
        assertEquals(52, ring.toSet().size)
    }

    @Test
    fun everyRingCellInsideGrid() {
        for (c in Board.ring()) {
            assertTrue("row ${c.row}", c.row in 0..14)
            assertTrue("col ${c.col}", c.col in 0..14)
        }
    }

    @Test
    fun ringIsContiguous() {
        // Four corners step diagonally around the centre block; everything else is
        // orthogonally adjacent. Anything further apart would be a broken path.
        val ring = Board.ring()
        var diagonals = 0
        for (i in 0 until ring.size) {
            val a = ring[i]
            val b = ring[(i + 1) % ring.size]
            val d = Math.abs(a.row - b.row) + Math.abs(a.col - b.col)
            assertTrue("gap between $a and $b", d in 1..2)
            if (d == 2) diagonals++
        }
        assertEquals("exactly four corner turns", 4, diagonals)
    }

    @Test
    fun ringNeverUsesTheCentreBlock() {
        for (c in Board.ring()) {
            val centre = c.row in 6..8 && c.col in 6..8
            assertTrue("ring cell $c intrudes into the centre", !centre)
        }
    }

    @Test
    fun startsAreEvenlySpaced() {
        assertEquals(0, Board.START_INDEX[0])
        assertEquals(13, Board.START_INDEX[1])
        assertEquals(26, Board.START_INDEX[2])
        assertEquals(39, Board.START_INDEX[3])
    }

    @Test
    fun startCellsMatchYardGeometry() {
        // RED starts on the left arm, GREEN on top, YELLOW right, BLUE bottom.
        val red = Board.ringCell(Board.START_INDEX[0])
        val green = Board.ringCell(Board.START_INDEX[1])
        val yellow = Board.ringCell(Board.START_INDEX[2])
        val blue = Board.ringCell(Board.START_INDEX[3])
        assertEquals(Cell(6, 1), red)
        assertEquals(Cell(1, 8), green)
        assertEquals(Cell(8, 13), yellow)
        assertEquals(Cell(13, 6), blue)
    }

    @Test
    fun progressMapsToMonotonicRingWalk() {
        for (color in 0..3) {
            var prev = -1
            for (p in 1..51) {
                val idx = Board.ringIndexOf(color, p)
                assertTrue(idx in 0..51)
                if (prev >= 0) assertEquals((prev + 1) % 52, idx)
                prev = idx
            }
        }
    }

    @Test
    fun lastRingStepIsAdjacentToOwnHomeColumn() {
        for (color in 0..3) {
            val last = Board.cellOf(color, 51)!!
            val firstHome = Board.cellOf(color, 52)!!
            val d = Math.abs(last.row - firstHome.row) + Math.abs(last.col - firstHome.col)
            assertEquals("colour $color: $last -> $firstHome", 1, d)
        }
    }

    @Test
    fun homeColumnIsContiguousTowardsCentre() {
        for (color in 0..3) {
            for (p in 52..56) {
                assertNotNull(Board.cellOf(color, p))
            }
            val cells = Board.homeColumn(color)
            assertEquals(5, cells.size)
            for (i in 0 until cells.size - 1) {
                val d = Math.abs(cells[i].row - cells[i + 1].row) + Math.abs(cells[i].col - cells[i + 1].col)
                assertEquals(1, d)
            }
        }
    }

    @Test
    fun homeColumnsDoNotOverlapRing() {
        val ringSet = Board.ring().toSet()
        for (color in 0..3) {
            for (c in Board.homeColumn(color)) {
                assertTrue("home cell $c collides with ring", !ringSet.contains(c))
            }
        }
    }

    @Test
    fun yardSlotsAreInsideOwnBlock() {
        for (color in 0..3) {
            val o = Board.yardOrigin(color)
            for (t in 0..3) {
                val (r, c) = Board.yardSlot(color, t)
                assertTrue(r > o.row && r < o.row + 6)
                assertTrue(c > o.col && c < o.col + 6)
            }
        }
    }

    @Test
    fun safeCellsAreTheExpectedEight() {
        val safe = (Board.START_INDEX + Board.STAR_INDEX).toSet()
        assertEquals(8, safe.size)
        assertTrue(Board.isSafeRing(0))
        assertTrue(Board.isSafeRing(8))
        assertTrue(!Board.isSafeRing(5))
    }

    @Test
    fun stepsToHomeIsMonotonic() {
        assertEquals(57, Board.stepsToHome(0))
        assertEquals(1, Board.stepsToHome(56))
        assertEquals(0, Board.stepsToHome(57))
    }
}
