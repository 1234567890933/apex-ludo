package com.apexludo.app

import android.os.Handler
import android.os.Looper
import com.apexludo.core.AiEngine
import com.apexludo.core.AiProfile
import com.apexludo.core.Board
import com.apexludo.core.DiceEngine
import com.apexludo.core.GameState
import com.apexludo.core.MatchEngine
import com.apexludo.core.Move
import com.apexludo.core.MoveOutcome
import com.apexludo.core.RuleSet
import com.apexludo.core.TelemetryRecorder

/**
 * Turn controller: owns the finite state machine, the timers, the bot scheduling and the
 * bridge between the (pure, tested) core and the renderer.
 *
 *   AWAIT_ROLL -> ROLLING -> (AWAIT_MOVE) -> ANIMATING -> AWAIT_ROLL / OVER
 */
class Session(
    private val host: MainActivity,
    val rules: RuleSet,
    private val seats: IntArray,
    private val profiles: Array<AiProfile>,
    val seed: Long
) {

    enum class Phase { IDLE, AWAIT_ROLL, ROLLING, AWAIT_MOVE, ANIMATING, OVER }

    val state: GameState
    val engine: MatchEngine
    val telemetry = TelemetryRecorder()

    var view: GameView? = null
    var phase: Phase = Phase.IDLE
        private set
    var legalMoves: List<Move> = emptyList()
        private set
    var matchFinished: Boolean = false
        private set
    var paused: Boolean = false

    val afk = BooleanArray(seats.size)

    private val handler = Handler(Looper.getMainLooper())
    private var generation = 0
    private var timerTotal = 0
    private var timerEndNs = 0L
    private var timerRunning = false
    private var message = ""
    private var messageUntil = 0L

    val turnTimerSeconds: Int get() = rules.turnTimerSeconds

    init {
        val pair = com.apexludo.core.MatchFactory.create(rules, seats, seed)
        state = pair.first
        engine = pair.second
        engine.dice.assistEnabled = host.prefs().assist
        for (i in 0 until state.playerCount) {
            afk[i] = false
        }
    }

    // ---------------------------------------------------------------- lifecycle
    fun start() {
        generation++
        phase = Phase.AWAIT_ROLL
        matchFinished = false
        state.turn = 0
        beginTurn()
    }

    fun destroy() {
        generation++
        handler.removeCallbacksAndMessages(null)
        timerRunning = false
    }

    fun pause() {
        paused = true
        generation++
        handler.removeCallbacksAndMessages(null)
    }

    fun resume() {
        paused = false
        when (phase) {
            Phase.AWAIT_ROLL, Phase.AWAIT_MOVE -> {
                if (isAuto(state.turn)) scheduleAuto(650L) else startTimer()
            }
            Phase.ANIMATING -> {
                // The move was already applied to the state before the animation started,
                // so the board is consistent: just pick the turn loop back up.
                view?.stopAnimations()
                legalMoves = emptyList()
                view?.highlighted = emptyList()
                beginTurn()
            }
            Phase.ROLLING -> {
                // A roll was in flight when the player paused. Resume from the moves it
                // produced rather than silently eating the turn.
                view?.stopAnimations()
                if (legalMoves.isNotEmpty()) {
                    phase = Phase.AWAIT_MOVE
                    view?.highlighted = legalMoves.map { it.token }.distinct()
                    if (isAuto(state.turn)) scheduleBotMove() else startTimer()
                } else {
                    beginTurn()
                }
            }
            else -> { }
        }
        view?.invalidate()
    }

    // ---------------------------------------------------------------- helpers
    fun currentPlayer(): Int = state.turn

    fun isHuman(p: Int): Boolean = p in seats.indices && seats[p] == 0 && !afk[p]

    fun isAuto(p: Int): Boolean = !isHuman(p)

    fun canRoll(): Boolean = phase == Phase.AWAIT_ROLL && !paused && isHuman(state.turn)

    fun playerName(p: Int): String {
        val colorName = when (state.colorOf(p)) {
            0 -> host.getString(R.string.player_red)
            1 -> host.getString(R.string.player_green)
            2 -> host.getString(R.string.player_yellow)
            else -> host.getString(R.string.player_blue)
        }
        return when {
            p !in seats.indices -> colorName
            seats[p] == 0 && afk[p] -> colorName + " · AFK"
            seats[p] == 0 -> colorName + " (" + host.getString(R.string.you) + ")"
            else -> colorName + " · " + profiles.getOrNull(p)?.let {
                host.getString(
                    when (it.id) {
                        "aggressive" -> R.string.ai_aggressive
                        "defensive" -> R.string.ai_defensive
                        "adaptive" -> R.string.ai_adaptive
                        "grandmaster" -> R.string.ai_grandmaster
                        "balanced" -> R.string.ai_balanced
                        else -> R.string.ai_tactical
                    }
                )
            } ?: host.getString(R.string.bot)
        }
    }

    fun themeColor(color: Int): Int = host.currentTheme().colorFor(color)

    fun statusText(): String = when (phase) {
        Phase.IDLE -> host.getString(R.string.loading)
        Phase.AWAIT_ROLL -> if (isHuman(state.turn)) host.getString(R.string.your_turn)
        else host.getString(R.string.turn_of, playerName(state.turn))
        Phase.ROLLING -> host.getString(R.string.rolled, playerName(state.turn), state.lastDie)
        Phase.AWAIT_MOVE -> if (isHuman(state.turn)) host.getString(R.string.tap_token)
        else host.getString(R.string.bot_turn)
        Phase.ANIMATING -> message.ifEmpty { host.getString(R.string.tap_token) }
        Phase.OVER -> host.getString(R.string.match_over)
    }

    fun tickerText(): String {
        val arr = host.resources.getStringArray(R.array.ticker)
        if (arr.isEmpty()) return ""
        val idx = ((System.currentTimeMillis() / 4200) % arr.size).toInt()
        return "◆ " + arr[idx]
    }

    fun timerFraction(): Float {
        if (timerTotal <= 0 || !timerRunning) return 1f
        val left = (timerEndNs - System.nanoTime()) / 1_000_000_000f
        return (left / timerTotal).coerceIn(0f, 1f)
    }

    fun remainingSeconds(): Int {
        if (timerTotal <= 0 || !timerRunning) return 0
        return ((timerEndNs - System.nanoTime()) / 1_000_000_000L).toInt().coerceAtLeast(0)
    }

    // ---------------------------------------------------------------- turn flow
    private fun beginTurn() {
        if (matchFinished) return
        legalMoves = emptyList()
        view?.highlighted = emptyList()
        phase = Phase.AWAIT_ROLL
        view?.invalidate()
        if (isAuto(state.turn)) {
            timerRunning = false
            scheduleAuto((profiles.getOrNull(state.turn)?.thinkMs ?: 600).toLong())
        } else {
            startTimer()
            if (host.prefs().autoRoll) scheduleAuto(900L)
        }
    }

    private fun startTimer() {
        val secs = rules.turnTimerSeconds
        timerTotal = secs
        if (secs <= 0) {
            timerRunning = false
            return
        }
        timerEndNs = System.nanoTime() + secs * 1_000_000_000L
        timerRunning = true
        val gen = generation
        handler.postDelayed({
            if (gen != generation || paused) return@postDelayed
            val left = remainingSeconds()
            if (left <= 5 && left > 0) host.sound().tick()
            if (left <= 0) {
                timerRunning = false
                onTimerExpired()
            } else {
                startTimerTick(gen)
            }
        }, 1000L)
    }

    private fun startTimerTick(gen: Int) {
        handler.postDelayed({
            if (gen != generation || paused) return@postDelayed
            val left = remainingSeconds()
            if (left <= 5 && left > 0) host.sound().tick()
            if (left <= 0) {
                timerRunning = false
                onTimerExpired()
            } else {
                startTimerTick(gen)
            }
        }, 1000L)
    }

    private fun onTimerExpired() {
        if (phase == Phase.AWAIT_ROLL) {
            doRoll()
        } else if (phase == Phase.AWAIT_MOVE) {
            // hand the decision to the tactical engine rather than a random move
            val ai = AiEngine(AiProfile.TACTICAL, System.nanoTime())
            if (legalMoves.isNotEmpty()) {
                val m = ai.chooseMove(state, state.lastDie, legalMoves)
                doMove(m)
            }
        }
    }

    private fun scheduleAuto(delayMs: Long) {
        val gen = generation
        handler.postDelayed({
            if (gen != generation || paused || matchFinished) return@postDelayed
            when (phase) {
                Phase.AWAIT_ROLL -> doRoll()
                Phase.AWAIT_MOVE -> scheduleBotMove()
                else -> { }
            }
        }, delayMs)
    }

    private fun scheduleBotMove() {
        if (legalMoves.isEmpty()) return
        phase = Phase.AWAIT_MOVE
        val gen = generation
        val snapshot = state.clone()
        val die = state.lastDie
        val moves = legalMoves
        val profile = profiles.getOrNull(state.turn) ?: AiProfile.TACTICAL
        val adjusted = adjustProfile(profile)
        val ai = AiEngine(adjusted, System.nanoTime())
        view?.invalidate()
        Thread {
            val chosen = try {
                ai.chooseMove(snapshot, die, moves)
            } catch (t: Throwable) {
                moves.firstOrNull()
            }
            handler.post {
                if (gen != generation || paused || matchFinished) return@post
                if (chosen == null) {
                    if (legalMoves.isNotEmpty()) doMove(legalMoves.first())
                } else {
                    val live = legalMoves.firstOrNull { it.token == chosen.token && it.to == chosen.to }
                    doMove(live ?: chosen)
                }
            }
        }.start()
    }

    private fun adjustProfile(p: AiProfile): AiProfile = when (host.prefs().aiBudget) {
        0 -> p.copy(rollouts = 0, thinkMs = 320)
        2 -> p.copy(rollouts = p.rollouts + 8, rolloutDepth = p.rolloutDepth + 40, thinkMs = 900)
        else -> p
    }

    // ---------------------------------------------------------------- actions
    fun onDiceTap() {
        if (!canRoll()) return
        doRoll()
    }

    private fun doRoll() {
        if (phase != Phase.AWAIT_ROLL || matchFinished) return
        handler.removeCallbacksAndMessages(null)
        timerRunning = false
        phase = Phase.ROLLING
        host.sound().roll()
        host.haptics().roll()
        val gen = generation
        val result = engine.roll()
        view?.playDice(result.die) {
            if (gen != generation) return@playDice
            afterRoll(result.die, result.forfeited, result.moves)
        }
        view?.invalidate()
    }

    private fun afterRoll(die: Int, forfeited: Boolean, moves: List<Move>) {
        if (matchFinished) return
        if (forfeited) {
            telemetry.recordForfeit(state.turn, die, state.turnsTaken)
            flash(host.getString(R.string.triple_six))
            host.sound().play("sfx_close", 0.6f)
            val gen = generation
            handler.postDelayed({
                if (gen != generation) return@postDelayed
                beginTurn()
            }, 950)
            view?.invalidate()
            return
        }
        if (moves.isEmpty()) {
            telemetry.recordPass(state.turn, die, state.turnsTaken)
            flash(host.getString(R.string.no_moves))
            engine.pass()
            val gen = generation
            handler.postDelayed({
                if (gen != generation) return@postDelayed
                beginTurn()
            }, 850)
            view?.invalidate()
            return
        }
        legalMoves = moves
        phase = Phase.AWAIT_MOVE
        view?.highlighted = moves.map { it.token }.distinct()
        if (die == 6) host.sound().six()
        host.haptics().land()

        if (isAuto(state.turn)) {
            scheduleBotMove()
        } else {
            if (moves.size == 1) {
                val gen = generation
                handler.postDelayed({
                    if (gen != generation || phase != Phase.AWAIT_MOVE) return@postDelayed
                    doMove(moves.first())
                }, 520)
            }
            startTimer()
        }
        view?.invalidate()
    }

    fun onTokenTap(token: Int) {
        if (phase != Phase.AWAIT_MOVE || matchFinished) return
        if (!isHuman(state.turn)) return
        val move = legalMoves.firstOrNull { it.token == token } ?: return
        doMove(move)
    }

    private fun doMove(move: Move) {
        if (matchFinished) return
        handler.removeCallbacksAndMessages(null)
        timerRunning = false
        phase = Phase.ANIMATING
        view?.highlighted = emptyList()
        val gen = generation
        val victims = move.captures.toList()
        val outcome: MoveOutcome
        try {
            outcome = engine.apply(move)
        } catch (t: Throwable) {
            // Defensive: a stale/illegal move must never deadlock the match.
            legalMoves = emptyList()
            beginTurn()
            return
        }
        telemetry.record(state, outcome)
        if (outcome.captured.isNotEmpty()) {
            host.sound().capture()
            host.haptics().capture()
            flash(host.getString(R.string.captured, playerName(move.player),
                outcome.captured.joinToString { playerName(it / Board.TOKENS_PER_PLAYER) }))
        } else if (outcome.reachedHome) {
            host.sound().home()
            host.haptics().home()
            flash(host.getString(R.string.token_home, playerName(move.player)))
        } else {
            host.sound().move()
            host.haptics().move()
        }
        view?.animateMove(move, victims) {
            if (gen != generation) return@animateMove
            afterMove(outcome)
        }
        view?.invalidate()
    }

    private fun afterMove(outcome: MoveOutcome) {
        if (matchFinished) return
        if (state.matchOver) {
            finishMatch()
            return
        }
        if (outcome.extraTurn) {
            flash(host.getString(R.string.six_again))
        }
        val gen = generation
        handler.postDelayed({
            if (gen != generation) return@postDelayed
            beginTurn()
        }, if (outcome.extraTurn) 320L else 180L)
    }

    private fun finishMatch() {
        matchFinished = true
        phase = Phase.OVER
        timerRunning = false
        handler.removeCallbacksAndMessages(null)
        val winner = if (state.winner >= 0) state.winner else 0
        host.sound().win()
        host.haptics().win()
        view?.celebrate(host.currentTheme().playerColors)
        host.onMatchFinished(this, winner)
    }

    private fun flash(msg: String) {
        message = msg
        messageUntil = System.currentTimeMillis() + 2200
        view?.invalidate()
    }

    fun currentMessage(): String = if (System.currentTimeMillis() < messageUntil) message else ""

    // ---------------------------------------------------------------- extras
    fun toggleAfk(player: Int) {
        if (player !in afk.indices) return
        afk[player] = !afk[player]
        host.toast(
            if (afk[player]) host.getString(R.string.afk_on, playerName(player))
            else host.getString(R.string.afk_off, playerName(player))
        )
        if (afk[player] && state.turn == player && phase == Phase.AWAIT_ROLL) scheduleAuto(400L)
        view?.invalidate()
    }

    fun logText(): String = telemetry.entries().joinToString("\n") { it.describe() }

    fun rulesSummary(): String = rules.describe()
}
