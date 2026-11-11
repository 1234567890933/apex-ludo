package com.apexludo.core

/** Bot personality. All numbers are honest, documented weights — no hidden cheating. */
data class AiProfile(
    val id: String,
    val labelKey: String,
    /** How much captures are worth. */
    val aggression: Float = 1.0f,
    /** How strongly it avoids leaving tokens exposed. */
    val caution: Float = 1.0f,
    /** Bonus for keeping tokens safe / stacked. */
    val safety: Float = 1.0f,
    /** Speed of play in ms (UI only). */
    val thinkMs: Int = 620,
    /** Monte-Carlo rollouts per decision (0 = pure heuristics). */
    val rollouts: Int = 0,
    /** Plies per rollout. */
    val rolloutDepth: Int = 220,
    /** Random tie-breaking noise, keeps bots from being predictable. */
    val noise: Float = 6f
) {
    companion object {
        val TACTICAL = AiProfile(
            id = "tactical", labelKey = "ai_tactical",
            aggression = 1.05f, caution = 1.15f, safety = 1.10f,
            thinkMs = 650, rollouts = 0, noise = 4f
        )
        val AGGRESSIVE = AiProfile(
            id = "aggressive", labelKey = "ai_aggressive",
            aggression = 1.55f, caution = 0.55f, safety = 0.75f,
            thinkMs = 420, rollouts = 0, noise = 7f
        )
        val DEFENSIVE = AiProfile(
            id = "defensive", labelKey = "ai_defensive",
            aggression = 0.70f, caution = 1.60f, safety = 1.55f,
            thinkMs = 480, rollouts = 0, noise = 4f
        )
        val BALANCED = AiProfile(
            id = "balanced", labelKey = "ai_balanced",
            aggression = 1.0f, caution = 1.0f, safety = 1.0f,
            thinkMs = 520, rollouts = 0, noise = 6f
        )
        val ADAPTIVE = AiProfile(
            id = "adaptive", labelKey = "ai_adaptive",
            aggression = 1.10f, caution = 1.05f, safety = 1.05f,
            thinkMs = 780, rollouts = 10, rolloutDepth = 200, noise = 3f
        )
        val GRANDMASTER = AiProfile(
            id = "grandmaster", labelKey = "ai_grandmaster",
            aggression = 1.2f, caution = 1.3f, safety = 1.2f,
            thinkMs = 900, rollouts = 18, rolloutDepth = 260, noise = 1.5f
        )
        val ALL = listOf(BALANCED, TACTICAL, AGGRESSIVE, DEFENSIVE, ADAPTIVE, GRANDMASTER)
        fun byId(id: String?): AiProfile = ALL.firstOrNull { it.id == id } ?: TACTICAL
    }
}

/**
 * On-device AI: three cooperating agents, exactly as specified.
 *
 *  1. BoardAnalystAgent  - scans every token, exposure, stack and distance-to-home.
 *  2. StrategicPlanner   - risk/reward heuristics shaped by the personality weights.
 *  3. ProbabilityAgent   - exact dice-reachability odds for capture & escape.
 *  4. (optional) RolloutAgent - short Monte-Carlo playouts, capped so it runs on a phone
 *     CPU in a few milliseconds; used by the ADAPTIVE / GRANDMASTER profiles.
 *
 * Everything runs locally, offline, with no network calls and no model downloads.
 */
class AiEngine(
    val profile: AiProfile,
    seed: Long = 0x51ED270B5A11E5L
) {
    private val rng = Rng(seed)
    var lastDecisionMicros: Long = 0
        private set

    fun chooseMove(state: GameState, die: Int, moves: List<Move>): Move {
        require(moves.isNotEmpty()) { "no legal moves" }
        if (moves.size == 1) return moves[0]
        val t0 = System.nanoTime()

        val analysis = BoardAnalystAgent.analyse(state)
        val best = if (profile.rollouts > 0) chooseWithRollouts(state, moves) else chooseHeuristic(state, analysis, moves)

        lastDecisionMicros = (System.nanoTime() - t0) / 1000
        return best
    }

    private fun chooseHeuristic(state: GameState, analysis: Analysis, moves: List<Move>): Move {
        var best = moves[0]
        var bestScore = -1e9f
        for (m in moves) {
            val s = score(state, analysis, m) + rng.nextFloat() * profile.noise
            if (s > bestScore) { bestScore = s; best = m }
        }
        return best
    }

    private fun chooseWithRollouts(state: GameState, moves: List<Move>): Move {
        var best = moves[0]
        var bestScore = -1e9f
        val player = state.turn
        val perMove = maxOf(1, profile.rollouts / moves.size)
        for (m in moves) {
            val h = score(state, BoardAnalystAgent.analyse(state), m)
            var rollout = 0f
            for (i in 0 until perMove) {
                rollout += RolloutAgent.evaluate(state, m, player, profile.rolloutDepth, rng)
            }
            rollout = if (perMove > 0) rollout / perMove else 0f
            val s = h * 0.35f + rollout * 120f + rng.nextFloat() * profile.noise
            if (s > bestScore) { bestScore = s; best = m }
        }
        return best
    }

    // ---------------------------------------------------------------- agents

    data class Analysis(
        val progressByPlayer: IntArray,
        val exposureByToken: FloatArray,
        val tokensHomeByPlayer: IntArray,
        val threatOnCell: HashMap<Int, Int>
    )

    object BoardAnalystAgent {
        fun analyse(state: GameState): Analysis {
            val n = state.positions.size
            val progress = IntArray(state.playerCount)
            val exposure = FloatArray(n)
            val home = IntArray(state.playerCount)
            val threat = HashMap<Int, Int>(32)

            for (p in 0 until state.playerCount) {
                var sum = 0
                for (t in state.tokenIds(p)) {
                    val pr = state.positions[t]
                    sum += pr
                    if (pr == Board.HOME_PROGRESS) home[p]++
                }
                progress[p] = sum
            }
            for (p in 0 until state.playerCount) {
                for (t in state.tokenIds(p)) {
                    val pr = state.positions[t]
                    exposure[t] = if (pr in 1..51) {
                        ProbabilityAgent.captureRisk(state, p, pr)
                    } else 0f
                }
            }
            // Threat map: for each ring cell, how many opponent tokens can reach it.
            for (p in 0 until state.playerCount) {
                for (t in state.tokenIds(p)) {
                    val pr = state.positions[t]
                    if (pr !in 1..51) continue
                    val color = state.colorOf(p)
                    for (d in 1..6) {
                        val target = pr + d
                        if (target > 51) continue
                        val idx = Board.ringIndexOf(color, target)
                        threat[idx] = (threat[idx] ?: 0) + 1
                    }
                }
            }
            return Analysis(progress, exposure, home, threat)
        }
    }

    object ProbabilityAgent {
        /** 0..1 chance that this token gets captured before the owner moves again. */
        fun captureRisk(state: GameState, player: Int, progress: Int): Float {
            val color = state.colorOf(player)
            val idx = Board.ringIndexOf(color, progress)
            if (state.rules.safeStars && Board.STAR_INDEX.contains(idx)) return 0f
            if (state.rules.safeStartCells && Board.START_INDEX.contains(idx)) return 0f
            var reach = 0
            for (o in 0 until state.playerCount) {
                if (o == player) continue
                val oc = state.colorOf(o)
                for (t in state.tokenIds(o)) {
                    val op = state.positions[t]
                    if (op == 0) {
                        // Could come out on a six and land straight on us only if we sit on their start.
                        if (idx == Board.START_INDEX[oc]) reach += 1
                        continue
                    }
                    if (op !in 1..51) continue
                    val delta = forwardDelta(oc, op, idx)
                    if (delta in 1..6) reach++
                }
            }
            return (reach / 12f).coerceIn(0f, 1f)
        }

        fun forwardDelta(color: Int, fromProgress: Int, targetRingIndex: Int): Int {
            val fromIdx = Board.ringIndexOf(color, fromProgress)
            var d = targetRingIndex - fromIdx
            if (d < 0) d += Board.RING
            return d
        }

        /** Number of exact dice values that let [progress] enter home from here. */
        fun exactHomeChance(progress: Int): Int = if (Board.HOME_PROGRESS - progress in 1..6) 1 else 0
    }

    object RolloutAgent {
        /**
         * Plays the move, then random-but-legal playouts. Returns the mover's expected
         * payoff: 1.0 for winning, 0.55 for second, 0.25 for third, 0.1 otherwise.
         */
        fun evaluate(
            state: GameState, move: Move, player: Int, depth: Int, rng: Rng
        ): Float {
            val sim = state.clone()
            val engine = MatchEngine(sim)
            // Apply the candidate move directly (same code path as the real engine).
            val legal = MoveGen.legalMoves(sim, sim.lastDie.coerceIn(1, 6), player)
            val chosen = legal.firstOrNull { it.token == move.token && it.to == move.to } ?: move
            sim.lastDie = sim.lastDie.coerceIn(1, 6)
            engine.apply(chosen)

            var plies = 0
            while (!sim.matchOver && plies < depth) {
                val r = engine.roll()
                if (r.forfeited) { plies++; continue }
                if (!r.hasMoves) { engine.pass(); plies++; continue }
                val pick = r.moves[rng.nextInt(r.moves.size)]
                engine.apply(pick)
                plies++
            }
            if (!sim.matchOver) {
                // Heuristic tie-break when the playout is cut short.
                val mine = scoreProgress(sim, player)
                val best = (0 until sim.playerCount).maxOfOrNull { scoreProgress(sim, it) } ?: 1
                return 0.5f * (mine.toFloat() / maxOf(1, best))
            }
            val rank = sim.rank[player]
            return when (rank) {
                1 -> 1.0f
                2 -> 0.55f
                3 -> 0.25f
                else -> 0.05f
            }
        }

        private fun scoreProgress(s: GameState, p: Int): Int {
            var sum = 0
            for (t in s.tokenIds(p)) sum += s.positions[t]
            return sum
        }
    }

    // ---------------------------------------------------------------- scoring

    fun score(state: GameState, analysis: Analysis, m: Move): Float {
        var s = 0f
        val player = m.player
        val color = state.colorOf(player)

        // 1. Capture value (aggression).
        if (m.captures.isNotEmpty()) {
            var v = 0f
            for (victim in m.captures) {
                val vp = state.positions[victim]
                v += 26f + vp * 1.15f
            }
            s += v * profile.aggression
        }

        // 2. Progress / tempo.
        s += (m.to - m.from) * 0.55f
        if (m.unlocks) s += 34f
        if (m.entersHome) s += 78f
        if (m.to in 52..56) s += 26f

        // 3. Destination safety.
        val destSafe = Board.isSafeProgress(color, m.to, state.rules.safeStars)
        if (destSafe && m.to in 1..51) s += 16f * profile.safety
        if (m.formsBlockade) s += 15f * profile.safety

        // 4. Risk of being captured on the destination.
        if (m.to in 1..51) {
            val idx = Board.ringIndexOf(color, m.to)
            val threats = analysis.threatOnCell[idx] ?: 0
            if (threats > 0 && !destSafe) {
                val pressure = (threats / 6f).coerceAtMost(1.6f)
                s -= (18f + m.to * 0.55f) * pressure * profile.caution
            }
        }

        // 5. Escaping a threatened token is worth something.
        if (m.from in 1..51) {
            val fromIdx = Board.ringIndexOf(color, m.from)
            val danger = analysis.exposureByToken[m.token]
            val underBlock = analysis.threatOnCell[fromIdx] ?: 0
            if (danger > 0f || underBlock > 0) {
                s += (10f + 14f * danger) * profile.caution
            }
        }

        // 6. Don't waste the last board token by unlocking more.
        if (m.unlocks) {
            val onBoard = Board.TOKENS_PER_PLAYER - state.tokensInYard(player)
            if (onBoard >= 3) s -= 12f
        }

        // 7. Late game: prefer pushing the leading token home.
        if (analysis.tokensHomeByPlayer[player] >= 2 && m.to >= 40) s += 8f

        return s
    }
}
