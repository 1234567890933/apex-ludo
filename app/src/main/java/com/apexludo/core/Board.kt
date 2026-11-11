package com.apexludo.core

/**
 * Apex Ludo — board topology.
 *
 * Pure Kotlin, zero Android dependencies: the entire board graph, path indexing and
 * safety masks are testable on the JVM (see app/src/test).
 *
 * Geometry: a 15x15 matrix.
 *   - 52 shared main-track ring cells (13 per quadrant)
 *   - 5 home-column cells per colour + 1 centre "home" cell  -> path length 57
 *   - 4 yard blocks of 6x6, four token slots each
 *
 * Progress encoding for a token (per colour):
 *   0        -> in yard
 *   1..51    -> main ring, absolute ring index = (start + progress - 1) % 52
 *   52..56   -> own home column
 *   57       -> home (finished)
 */
data class Cell(val row: Int, val col: Int)

enum class Color4 { RED, GREEN, YELLOW, BLUE }

object Board {

    const val GRID = 15
    const val RING = 52
    const val HOME_COLUMN_CELLS = 5
    const val HOME_PROGRESS = 57
    const val MAX_PROGRESS = 57
    const val TOKENS_PER_PLAYER = 4

    /** Ring start index per colour ordinal (RED, GREEN, YELLOW, BLUE). */
    val START_INDEX = intArrayOf(0, 13, 26, 39)

    /** Star / safe cells on the ring (plus the four start cells). */
    val STAR_INDEX = intArrayOf(8, 21, 34, 47)

    private val ringCells: List<Cell> = buildRing()

    private fun buildRing(): List<Cell> {
        val out = ArrayList<Cell>(RING)
        // Quadrant 1: left arm top edge -> up the top arm left edge -> top centre
        for (c in 1..5) out.add(Cell(6, c))
        for (r in 5 downTo 0) out.add(Cell(r, 6))
        out.add(Cell(0, 7))
        out.add(Cell(0, 8))
        // Quadrant 2: down the top arm right edge -> right arm top edge -> right centre
        for (r in 1..5) out.add(Cell(r, 8))
        for (c in 9..14) out.add(Cell(6, c))
        out.add(Cell(7, 14))
        out.add(Cell(8, 14))
        // Quadrant 3: left along the right arm bottom edge -> down the bottom arm right edge
        for (c in 13 downTo 9) out.add(Cell(8, c))
        for (r in 9..14) out.add(Cell(r, 8))
        out.add(Cell(14, 7))
        out.add(Cell(14, 6))
        // Quadrant 4: up the bottom arm left edge -> left along the left arm bottom edge
        for (r in 13 downTo 9) out.add(Cell(r, 6))
        for (c in 5 downTo 0) out.add(Cell(8, c))
        out.add(Cell(7, 0))
        out.add(Cell(6, 0))
        return out
    }

    fun ringCell(index: Int): Cell = ringCells[((index % RING) + RING) % RING]

    fun ring(): List<Cell> = ringCells

    /** Home column cells, 5 per colour, ordered from ring-side to centre-side. */
    fun homeColumn(color: Int): List<Cell> = when (color) {
        0 -> (1..5).map { Cell(7, it) }          // RED   (enters from the left)
        1 -> (1..5).map { Cell(it, 7) }          // GREEN (enters from the top)
        2 -> (13 downTo 9).map { Cell(7, it) }   // YELLOW(enters from the right)
        else -> (13 downTo 9).map { Cell(it, 7) }// BLUE  (enters from the bottom)
    }

    /** Yard block origin (top-left corner row/col) per colour. */
    fun yardOrigin(color: Int): Cell = when (color) {
        0 -> Cell(0, 0)
        1 -> Cell(0, 9)
        2 -> Cell(9, 9)
        else -> Cell(9, 0)
    }

    /** Fractional (row, col) centre of a yard slot — used by the renderer. */
    fun yardSlot(color: Int, token: Int): Pair<Float, Float> {
        val o = yardOrigin(color)
        val dr = if (token < 2) 1.5f else 3.5f
        val dc = if (token % 2 == 0) 1.5f else 3.5f
        return Pair(o.row + dr, o.col + dc)
    }

    fun ringIndexOf(color: Int, progress: Int): Int {
        require(progress in 1..51) { "progress $progress is not on the ring" }
        return (START_INDEX[color] + progress - 1) % RING
    }

    /** Cell of a token, or null when it is in the yard or home. */
    fun cellOf(color: Int, progress: Int): Cell? = when {
        progress in 1..51 -> ringCell(ringIndexOf(color, progress))
        progress in 52..56 -> homeColumn(color)[progress - 52]
        else -> null
    }

    fun isSafeRing(index: Int): Boolean =
        START_INDEX.contains(index) || STAR_INDEX.contains(index)

    /** True when [progress] sits on a capture-protected cell. */
    fun isSafeProgress(color: Int, progress: Int, safeStars: Boolean): Boolean {
        if (progress in 52..56) return true
        if (progress == 0 || progress == HOME_PROGRESS) return true
        val idx = ringIndexOf(color, progress)
        if (START_INDEX.contains(idx)) return true
        return safeStars && STAR_INDEX.contains(idx)
    }

    /** Number of steps a token still needs to reach home. */
    fun stepsToHome(progress: Int): Int = HOME_PROGRESS - progress

    /** True when two ring indices are the same physical cell. */
    fun sameCell(a: Int, b: Int): Boolean = ((a % RING) + RING) % RING == ((b % RING) + RING) % RING
}
