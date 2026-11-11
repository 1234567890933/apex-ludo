package com.apexludo.core

/** A single legal action. */
data class Move(
    val token: Int,
    val from: Int,
    val to: Int,
    val captures: List<Int> = emptyList(),
    val entersHome: Boolean = false,
    val unlocks: Boolean = false,
    val formsBlockade: Boolean = false,
    val passesHome: Boolean = false
) {
    val player: Int get() = token / Board.TOKENS_PER_PLAYER
}

/** Result of resolving a move against the board. */
data class MoveOutcome(
    val move: Move,
    val captured: List<Int>,
    val reachedHome: Boolean,
    val finishedPlayer: Int,
    val extraTurn: Boolean
)

object MoveGen {

    /** All legal moves for [die]. Empty list = the player must pass. */
    fun legalMoves(s: GameState, die: Int, player: Int = s.turn): List<Move> {
        require(die in 1..6) { "die out of range: $die" }
        val rules = s.rules
        val out = ArrayList<Move>(4)
        if (s.rank[player] >= 0) return out

        for (t in s.tokenIds(player)) {
            val from = s.positions[t]
            if (from == Board.HOME_PROGRESS) continue

            if (from == 0) {
                // Leaving the yard.
                val allowed = if (rules.unlockRequiresSix) die == 6 else (die == 6 || die == 1)
                if (!allowed) continue
                val to = 1
                val ringIdx = Board.ringIndexOf(s.colorOf(player), to)
                if (rules.blockadesEnabled && rules.blockadesBlockPassing &&
                    s.isBlockadedByOpponent(player, ringIdx)
                ) continue
                val caps = capturable(s, player, to, ringIdx)
                out.add(
                    Move(
                        token = t, from = 0, to = to, captures = caps,
                        unlocks = true, formsBlockade = false
                    )
                )
                continue
            }

            var to = from + die
            if (to > Board.MAX_PROGRESS) {
                if (rules.exactRollToHome) continue
                to = Board.MAX_PROGRESS
            }

            // Cannot pass through an enemy blockade (ring portion only).
            if (rules.blockadesEnabled && rules.blockadesBlockPassing && from in 1..51) {
                if (blockedPath(s, player, from, to)) continue
            }

            val entersHome = to == Board.HOME_PROGRESS
            var caps: List<Int> = emptyList()
            var blockade = false
            if (to in 1..51) {
                val ringIdx = Board.ringIndexOf(s.colorOf(player), to)
                if (rules.blockadesEnabled && s.isBlockadedByOpponent(player, ringIdx)) continue
                caps = capturable(s, player, to, ringIdx)
                blockade = wouldFormBlockade(s, player, t, to)
            }
            if (rules.battleMode && caps.isNotEmpty()) {
                // Battle mode: a token can only capture once it is past the mid-point.
                if (from <= 26) continue
            }
            out.add(
                Move(
                    token = t, from = from, to = to, captures = caps,
                    entersHome = entersHome, formsBlockade = blockade,
                    passesHome = to > Board.HOME_PROGRESS
                )
            )
        }
        return out
    }

    private fun blockedPath(s: GameState, player: Int, from: Int, to: Int): Boolean {
        val color = s.colorOf(player)
        val last = minOf(to, 51)
        if (from >= last) return false
        for (p in (from + 1)..last) {
            val idx = Board.ringIndexOf(color, p)
            if (s.isBlockadedByOpponent(player, idx)) return true
        }
        return false
    }

    private fun capturable(s: GameState, player: Int, progress: Int, ringIdx: Int): List<Int> {
        val rules = s.rules
        val safe = (rules.safeStars && Board.STAR_INDEX.contains(ringIdx)) ||
            (rules.safeStartCells && Board.START_INDEX.contains(ringIdx))
        if (safe) return emptyList()
        val victims = s.opponentsAt(player, ringIdx)
        if (victims.isEmpty()) return emptyList()
        // NOTE: a blockade belongs to ONE colour. Two different opponents each holding a
        // single token on the same square is not a blockade — both get captured. (Treating
        // the stack as a blockade left two players illegally sharing a cell; the fuzz
        // suite caught it.)
        return victims
    }

    private fun wouldFormBlockade(s: GameState, player: Int, movingToken: Int, to: Int): Boolean {
        val color = s.colorOf(player)
        val idx = Board.ringIndexOf(color, to)
        var own = 0
        for (t in s.tokenIds(player)) {
            if (t == movingToken) continue
            val pr = s.positions[t]
            if (pr in 1..51 && Board.ringIndexOf(color, pr) == idx) own++
        }
        return own >= 1
    }
}
