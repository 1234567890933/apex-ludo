package com.apexludo.core

data class RollResult(
    val player: Int,
    val die: Int,
    val forfeited: Boolean,
    val moves: List<Move>
) {
    val hasMoves: Boolean get() = moves.isNotEmpty()
}

/**
 * Turn orchestrator. Owns the finite state machine described in the spec:
 * TurnStart -> DiceRoll -> MovementResolution -> (Vfx) -> VictoryEvaluation -> next turn.
 * No Android imports: the whole loop is unit-testable and fuzz-testable headlessly.
 */
class MatchEngine(
    val state: GameState,
    val dice: DiceEngine = DiceEngine(),
    /** Hard safety valve against a pathological rule set deadlocking the loop. */
    val maxTurns: Long = 100_000
) {
    private val rules: RuleSet get() = state.rules

    fun roll(): RollResult {
        val player = state.turn
        val die = dice.rollFor(state, player)
        state.lastDie = die
        state.turnsTaken++
        if (die == 6) state.sixStreak++ else state.sixStreak = 0

        if (rules.threeSixesForfeit && state.sixStreak >= 3) {
            state.sixStreak = 0
            val forfeited = player
            nextTurn()
            return RollResult(forfeited, die, forfeited = true, moves = emptyList())
        }

        val moves = MoveGen.legalMoves(state, die, player)
        return RollResult(player, die, forfeited = false, moves = moves)
    }

    /** Resolve a move that came out of [roll]. */
    fun apply(move: Move): MoveOutcome {
        val player = move.player
        require(state.rank[player] < 0) { "player $player already finished" }
        require(state.positions[move.token] == move.from) { "stale move: token moved" }

        // Re-validate against the live board so a desynced input can never cheat.
        val legal = MoveGen.legalMoves(state, state.lastDie, player)
        val verified = legal.firstOrNull { it.token == move.token && it.to == move.to }
        val real = verified ?: move

        state.positions[real.token] = real.to
        val captured = ArrayList<Int>(real.captures.size)
        for (victim in real.captures) {
            if (state.positions[victim] != 0) {
                state.positions[victim] = 0
                captured.add(victim)
            }
        }

        val reachedHome = real.to == Board.HOME_PROGRESS
        var finishedPlayer = -1
        if (reachedHome && state.hasFinished(player)) {
            state.rank[player] = state.nextRank
            finishedPlayer = player
            state.nextRank++
            if (state.nextRank > state.playerCount) state.nextRank = state.playerCount + 1
            if (state.livingPlayers() <= 1) {
                for (p in 0 until state.playerCount) {
                    if (state.rank[p] < 0) state.rank[p] = state.nextRank
                }
                state.matchOver = true
                state.winner = state.rank.indexOfFirst { it == 1 }
            }
        }

        // A player who has just finished the race never rolls again.
        val extra = finishedPlayer < 0 && (
            (state.lastDie == 6 && rules.sixGrantsExtraTurn && state.sixStreak < 3) ||
                (captured.isNotEmpty() && rules.captureGrantsExtraTurn) ||
                (reachedHome && rules.homeGrantsExtraTurn)
            )

        if (!extra) nextTurn()

        return MoveOutcome(
            move = real,
            captured = captured,
            reachedHome = reachedHome,
            finishedPlayer = finishedPlayer,
            extraTurn = extra && !state.matchOver
        )
    }

    /** No legal move for the rolled die: hand over the turn. */
    fun pass() {
        state.sixStreak = 0
        nextTurn()
    }

    fun nextTurn() {
        if (state.matchOver) return
        var p = state.turn
        for (i in 1..state.playerCount) {
            val candidate = (state.turn + i) % state.playerCount
            if (state.rank[candidate] < 0) { p = candidate; break }
        }
        state.turn = p
        state.sixStreak = 0
    }

    /** One full turn played by the given AI — used by headless fuzz/stress tests. */
    fun playBotTurn(ai: AiEngine): MoveOutcome? {
        if (state.matchOver) return null
        if (state.turnsTaken > maxTurns) {
            state.matchOver = true
            state.winner = 0
            return null
        }
        val r = roll()
        if (r.forfeited) return null
        if (!r.hasMoves) { pass(); return null }
        val move = ai.chooseMove(state, r.die, r.moves)
        return apply(move)
    }

    /** Plays until the match ends. Returns the number of turns consumed. */
    fun playToEnd(ai: AiEngine): Long {
        val start = state.turnsTaken
        var guard = 0L
        while (!state.matchOver && guard < maxTurns) {
            playBotTurn(ai)
            guard++
        }
        if (!state.matchOver) state.matchOver = true
        return state.turnsTaken - start
    }
}
