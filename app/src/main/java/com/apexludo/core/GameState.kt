package com.apexludo.core

/**
 * Deterministic, allocation-light match state.
 *
 * Token id = player * 4 + token. Progress per token lives in a flat IntArray so a
 * snapshot is a single clone() — this is what makes on-device Monte-Carlo rollouts cheap.
 */
class GameState(
    val rules: RuleSet,
    val playerCount: Int = 4,
    val playerColors: IntArray = IntArray(playerCount) { it }
) {
    val positions: IntArray = IntArray(playerCount * Board.TOKENS_PER_PLAYER)
    val finishedCount: IntArray = IntArray(playerCount)
    val rank: IntArray = IntArray(playerCount) { -1 }
    val isBot: BooleanArray = BooleanArray(playerCount)

    var turn: Int = 0
    var sixStreak: Int = 0
    var nextRank: Int = 1
    var turnsTaken: Long = 0
    var lastDie: Int = 0
    var matchOver: Boolean = false
    var winner: Int = -1

    /** Deterministic hash of the playable state — used by the desync/checksum checks. */
    fun checksum(): Long {
        var h = 1469598103934665603L
        for (p in positions) {
            h = h xor (p.toLong() + 0x9E3779B97F4A7C15uL.toLong())
            h *= 1099511628211L
        }
        h = h xor (turn.toLong() * 31 + sixStreak.toLong() * 131 + nextRank.toLong() * 17)
        return h
    }

    fun clone(): GameState {
        val s = GameState(rules, playerCount, playerColors.copyOf())
        positions.copyInto(s.positions)
        finishedCount.copyInto(s.finishedCount)
        rank.copyInto(s.rank)
        isBot.copyInto(s.isBot)
        s.turn = turn
        s.sixStreak = sixStreak
        s.nextRank = nextRank
        s.turnsTaken = turnsTaken
        s.lastDie = lastDie
        s.matchOver = matchOver
        s.winner = winner
        return s
    }

    fun tokenIds(player: Int): IntRange =
        (player * Board.TOKENS_PER_PLAYER) until ((player + 1) * Board.TOKENS_PER_PLAYER)

    fun progress(token: Int): Int = positions[token]

    fun colorOf(player: Int): Int = playerColors[player]

    fun tokensInYard(player: Int): Int {
        var n = 0
        for (t in tokenIds(player)) if (positions[t] == 0) n++
        return n
    }

    fun tokensHome(player: Int): Int {
        var n = 0
        for (t in tokenIds(player)) if (positions[t] == Board.HOME_PROGRESS) n++
        return n
    }

    /** True when [player] has finished the race. */
    fun hasFinished(player: Int): Boolean = tokensHome(player) == Board.TOKENS_PER_PLAYER

    /** Ring index currently occupied by a token, or -1. */
    fun ringIndexOfToken(token: Int): Int {
        val p = positions[token]
        if (p !in 1..51) return -1
        return Board.ringIndexOf(token / Board.TOKENS_PER_PLAYER, p)
    }

    /** How many tokens of [player] sit on the same physical cell as [token]. */
    fun stackSize(token: Int): Int {
        val player = token / Board.TOKENS_PER_PLAYER
        val p = positions[token]
        if (p !in 1..51) return if (p == 0) 0 else 1
        val color = colorOf(player)
        val idx = Board.ringIndexOf(color, p)
        var n = 0
        for (t in tokenIds(player)) {
            if (positions[t] in 1..51 && Board.ringIndexOf(color, positions[t]) == idx) n++
        }
        return n
    }

    /** Opponent tokens sitting on the ring cell [ringIdx], as token ids. */
    fun opponentsAt(player: Int, ringIdx: Int): MutableList<Int> {
        val out = ArrayList<Int>(4)
        for (p in 0 until playerCount) {
            if (p == player) continue
            val color = colorOf(p)
            for (t in tokenIds(p)) {
                val pr = positions[t]
                if (pr in 1..51 && Board.ringIndexOf(color, pr) == ringIdx) out.add(t)
            }
        }
        return out
    }

    /** True when an opponent blockade occupies [ringIdx]. */
    fun isBlockadedByOpponent(player: Int, ringIdx: Int): Boolean {
        for (p in 0 until playerCount) {
            if (p == player) continue
            val color = colorOf(p)
            var n = 0
            for (t in tokenIds(p)) {
                val pr = positions[t]
                if (pr in 1..51 && Board.ringIndexOf(color, pr) == ringIdx) n++
            }
            if (n >= 2) return true
        }
        return false
    }

    /** Order of players still racing, starting from [turn]. */
    fun activeOrder(): List<Int> {
        val out = ArrayList<Int>(playerCount)
        for (i in 0 until playerCount) {
            val p = (turn + i) % playerCount
            if (rank[p] < 0) out.add(p)
        }
        return out
    }

    fun livingPlayers(): Int = rank.count { it < 0 }
}
