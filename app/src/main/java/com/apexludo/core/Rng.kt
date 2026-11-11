package com.apexludo.core

/** Deterministic xorshift64* PRNG — identical results on every device and in tests. */
class Rng(seed: Long = 0x2545F4914F6CDD1DL) {
    private var state: Long = if (seed == 0L) 0x9E3779B97F4A7C15uL.toLong() else seed

    fun nextLong(): Long {
        var x = state
        x = x xor (x ushr 12)
        x = x xor (x shl 25)
        x = x xor (x ushr 27)
        state = x
        return x * 2685821657736338717L
    }

    fun nextInt(bound: Int): Int {
        if (bound <= 0) return 0
        val v = (nextLong() ushr 1) % bound
        return v.toInt()
    }

    fun nextInt(from: Int, toInclusive: Int): Int = from + nextInt(toInclusive - from + 1)

    fun nextFloat(): Float = ((nextLong() ushr 40).toFloat() / (1 shl 24).toFloat())

    fun chance(p: Float): Boolean = nextFloat() < p

    fun reseed(seed: Long) {
        state = if (seed == 0L) 0x9E3779B97F4A7C15uL.toLong() else seed
    }

    fun snapshot(): Long = state

    companion object {
        fun fromEntropy(): Rng = Rng(System.nanoTime() * 31 + 0x5DEECE66DL)
    }
}

/**
 * Dice engine.
 *
 * Two modes:
 *  - FAIR  : plain uniform 1..6 (default, competitive)
 *  - ASSIST: gentle catch-up assist for the human who is furthest behind. It is
 *            capped at a small influence and never touches bot rolls, so the game
 *            stays honest and replayable. Off by default.
 */
class DiceEngine(seed: Long = 0x2545F4914F6CDD1DL) {
    val rng = Rng(seed)
    var assistEnabled: Boolean = false

    private val histogram = IntArray(7)

    fun roll(): Int {
        val v = rng.nextInt(1, 6)
        histogram[v]++
        return v
    }

    /** Roll for a specific player; applies the optional catch-up assist. */
    fun rollFor(state: GameState, player: Int): Int {
        var v = rng.nextInt(1, 6)
        if (assistEnabled && !state.isBot[player]) {
            val mine = boardProgress(state, player)
            var worst = mine
            var worstPlayer = player
            for (p in 0 until state.playerCount) {
                if (p == player || state.rank[p] >= 0) continue
                val pr = boardProgress(state, p)
                if (pr < worst) { worst = pr; worstPlayer = p }
            }
            // Only nudge when the human is meaningfully behind; at most ~1 pip bias.
            if (worstPlayer == player && behindBy(state, player) > 25) {
                if (v == 1 && rng.chance(0.35f)) v = 6
            }
        }
        histogram[v]++
        return v
    }

    private fun boardProgress(s: GameState, p: Int): Int {
        var sum = 0
        for (t in s.tokenIds(p)) sum += s.positions[t]
        return sum
    }

    private fun behindBy(s: GameState, p: Int): Int {
        var best = 0
        for (o in 0 until s.playerCount) {
            if (o == p) continue
            var sum = 0
            for (t in s.tokenIds(o)) sum += s.positions[t]
            if (sum > best) best = sum
        }
        return best - boardProgress(s, p)
    }

    fun counts(): IntArray = histogram.copyOf()

    fun resetStats() {
        histogram.fill(0)
    }
}
