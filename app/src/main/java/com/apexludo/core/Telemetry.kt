package com.apexludo.core

/** One replayable record of a resolved action. */
data class TelemetryEntry(
    val index: Int,
    val turn: Int,
    val player: Int,
    val die: Int,
    val token: Int,
    val from: Int,
    val to: Int,
    val capturedCount: Int,
    val kind: Kind
) {
    enum class Kind { MOVE, CAPTURE, HOME, FORFEIT, PASS, FINISH }

    /** Compact wire form used by the local replay recorder. */
    fun encode(): String = "$index|$turn|$player|$die|$token|$from|$to|$capturedCount|${kind.ordinal}"

    fun describe(): String = when (kind) {
        Kind.FORFEIT -> "turn $turn · P$player · triple six forfeit"
        Kind.PASS -> "turn $turn · P$player · rolled $die, no move"
        Kind.FINISH -> "turn $turn · P$player · finished the race"
        Kind.HOME -> "turn $turn · P$player · token $token home ($die)"
        Kind.CAPTURE -> "turn $turn · P$player · captured $capturedCount with $die"
        Kind.MOVE -> "turn $turn · P$player · token $token $from->$to ($die)"
    }

    companion object {
        fun decode(line: String): TelemetryEntry? {
            val p = line.split('|')
            if (p.size < 9) return null
            return try {
                TelemetryEntry(
                    index = p[0].toInt(), turn = p[1].toInt(), player = p[2].toInt(),
                    die = p[3].toInt(), token = p[4].toInt(), from = p[5].toInt(),
                    to = p[6].toInt(), capturedCount = p[7].toInt(),
                    kind = Kind.values()[p[8].toInt()]
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

/** Session telemetry + replay recorder (feature 5). Purely local, no analytics SDK. */
class TelemetryRecorder(private val capacity: Int = 4000) {
    private val entries = ArrayList<TelemetryEntry>(256)
    private var counter = 0
    var diceHistogram: IntArray = IntArray(7)
        private set
    var captures: IntArray = IntArray(4)
        private set
    var homes: IntArray = IntArray(4)
        private set

    fun reset() {
        entries.clear()
        counter = 0
        diceHistogram = IntArray(7)
        captures = IntArray(4)
        homes = IntArray(4)
    }

    fun record(state: GameState, outcome: MoveOutcome) {
        if (entries.size >= capacity) entries.removeAt(0)
        val kind = when {
            outcome.finishedPlayer >= 0 -> TelemetryEntry.Kind.FINISH
            outcome.reachedHome -> TelemetryEntry.Kind.HOME
            outcome.captured.isNotEmpty() -> TelemetryEntry.Kind.CAPTURE
            else -> TelemetryEntry.Kind.MOVE
        }
        entries.add(
            TelemetryEntry(
                index = counter++, turn = state.turnsTaken.toInt(),
                player = outcome.move.player, die = state.lastDie,
                token = outcome.move.token, from = outcome.move.from, to = outcome.move.to,
                capturedCount = outcome.captured.size, kind = kind
            )
        )
        diceHistogram[state.lastDie.coerceIn(1, 6)]++
        if (outcome.captured.isNotEmpty()) captures[outcome.move.player] += outcome.captured.size
        if (outcome.reachedHome) homes[outcome.move.player]++
    }

    fun recordPass(player: Int, die: Int, turn: Long) {
        if (entries.size >= capacity) entries.removeAt(0)
        entries.add(TelemetryEntry(counter++, turn.toInt(), player, die, -1, -1, -1, 0, TelemetryEntry.Kind.PASS))
    }

    fun recordForfeit(player: Int, die: Int, turn: Long) {
        if (entries.size >= capacity) entries.removeAt(0)
        entries.add(TelemetryEntry(counter++, turn.toInt(), player, die, -1, -1, -1, 0, TelemetryEntry.Kind.FORFEIT))
    }

    fun entries(): List<TelemetryEntry> = entries

    fun export(): String = entries.joinToString("\n") { it.encode() }

    fun import(data: String) {
        entries.clear()
        for (line in data.lineSequence()) {
            val e = TelemetryEntry.decode(line) ?: continue
            entries.add(e)
            counter = maxOf(counter, e.index + 1)
        }
    }

    /** Short human summary for the post-match screen. */
    fun summary(playerCount: Int): String {
        val sb = StringBuilder()
        sb.append("turns=").append(counter).append('\n')
        sb.append("dice=")
        for (i in 1..6) sb.append(i).append(':').append(diceHistogram[i]).append(' ')
        sb.append('\n')
        for (p in 0 until playerCount) {
            sb.append("P").append(p).append(" captures=").append(captures[p])
                .append(" homes=").append(homes[p]).append('\n')
        }
        return sb.toString()
    }
}

/** Factory for building a match with human/bot seats. */
object MatchFactory {
    fun create(
        rules: RuleSet,
        seats: IntArray,
        seed: Long = 0x2545F4914F6CDD1DL
    ): Pair<GameState, MatchEngine> {
        val count = seats.size.coerceIn(2, 4)
        val colors = IntArray(count)
        // Two players sit opposite each other for a fair race.
        when (count) {
            2 -> { colors[0] = 0; colors[1] = 2 }
            3 -> { colors[0] = 0; colors[1] = 1; colors[2] = 3 }
            else -> { colors[0] = 0; colors[1] = 1; colors[2] = 2; colors[3] = 3 }
        }
        val state = GameState(rules.sanitized(), count, colors)
        for (i in 0 until count) state.isBot[i] = seats[i] == 1
        val engine = MatchEngine(state, DiceEngine(seed))
        return Pair(state, engine)
    }
}
