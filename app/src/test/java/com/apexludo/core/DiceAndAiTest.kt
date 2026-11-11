package com.apexludo.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiceAndAiTest {

    @Test
    fun diceAlwaysInRange() {
        val d = DiceEngine(1234L)
        for (i in 0 until 200_000) {
            val v = d.roll()
            assertTrue("rolled $v", v in 1..6)
        }
    }

    @Test
    fun diceIsReasonablyUniform() {
        val d = DiceEngine(98765L)
        val n = 600_000
        repeat(n) { d.roll() }
        val c = d.counts()
        val expected = n / 6.0
        for (face in 1..6) {
            val dev = Math.abs(c[face] - expected) / expected
            assertTrue("face $face deviation $dev", dev < 0.03)
        }
    }

    @Test
    fun sameSeedSameSequence() {
        val a = DiceEngine(555L)
        val b = DiceEngine(555L)
        repeat(500) { assertEquals(a.roll(), b.roll()) }
    }

    @Test
    fun assistNeverProducesInvalidFace() {
        val (s, _) = MatchFactory.create(RuleSet.CLASSIC, intArrayOf(0, 1, 1, 1), 3L)
        val d = DiceEngine(77L)
        d.assistEnabled = true
        repeat(50_000) {
            val v = d.rollFor(s, 0)
            assertTrue(v in 1..6)
        }
    }

    @Test
    fun aiAlwaysReturnsALegalMove() {
        val (s, _) = MatchFactory.create(RuleSet.CLASSIC, intArrayOf(1, 1, 1, 1), 11L)
        s.positions[0] = 12
        val moves = MoveGen.legalMoves(s, 4, 0)
        val ai = AiEngine(AiProfile.BALANCED, 5L)
        val m = ai.chooseMove(s, 4, moves)
        assertTrue(moves.contains(m))
    }

    @Test
    fun aiIsDeterministicForAGivenSeed() {
        val (s, _) = MatchFactory.create(RuleSet.CLASSIC, intArrayOf(1, 1, 1, 1), 12L)
        s.positions[0] = 20
        s.positions[1] = 30
        val moves = MoveGen.legalMoves(s, 5, 0)
        val a = AiEngine(AiProfile.AGGRESSIVE, 9L).chooseMove(s, 5, moves)
        val b = AiEngine(AiProfile.AGGRESSIVE, 9L).chooseMove(s, 5, moves)
        assertEquals(a, b)
    }

    @Test
    fun everyProfileFinishesAMatch() {
        for (profile in AiProfile.ALL) {
            val (s, engine) = MatchFactory.create(RuleSet.CLASSIC, intArrayOf(1, 1, 1, 1), 21L)
            val ai = AiEngine(profile, 21L)
            val turns = engine.playToEnd(ai)
            assertTrue("${profile.id} did not finish", s.matchOver)
            assertNotNull(s.winner)
            assertTrue("${profile.id} took $turns turns", turns in 20..20_000)
        }
    }

    @Test
    fun aggressiveProfileCapturesMoreThanDefensive() {
        var aggCaps = 0
        var defCaps = 0
        repeat(12) { seed ->
            for ((profile, isAgg) in listOf(AiProfile.AGGRESSIVE to true, AiProfile.DEFENSIVE to false)) {
                val (s, engine) = MatchFactory.create(RuleSet.CLASSIC, intArrayOf(1, 1, 1, 1), seed * 31L + 7)
                val ai = AiEngine(profile, seed * 17L + 3)
                var caps = 0
                var guard = 0
                while (!s.matchOver && guard++ < 20_000) {
                    val out = engine.playBotTurn(ai) ?: continue
                    caps += out.captured.size
                }
                if (isAgg) aggCaps += caps else defCaps += caps
            }
        }
        assertTrue("aggressive=$aggCaps defensive=$defCaps", aggCaps > defCaps)
    }

    @Test
    fun rolloutProfileStaysWithinTimeBudget() {
        val (s, _) = MatchFactory.create(RuleSet.CLASSIC, intArrayOf(1, 1, 1, 1), 33L)
        s.positions[0] = 20
        s.positions[1] = 24
        s.positions[2] = 8
        val moves = MoveGen.legalMoves(s, 4, 0)
        val ai = AiEngine(AiProfile.ADAPTIVE, 3L)
        val t0 = System.nanoTime()
        ai.chooseMove(s, 4, moves)
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertTrue("adaptive took ${ms}ms", ms < 900)
    }

    @Test
    fun probabilityAgentScoresSafeCellsAtZero() {
        val (s, _) = MatchFactory.create(RuleSet.CLASSIC, intArrayOf(1, 1, 1, 1), 41L)
        val star = Board.STAR_INDEX[0]
        var prog = -1
        for (p in 1..51) if (Board.ringIndexOf(0, p) == star) { prog = p; break }
        assertEquals(0f, AiEngine.ProbabilityAgent.captureRisk(s, 0, prog), 0.0001f)
    }

    @Test
    fun telemetryRoundTrips() {
        val rec = TelemetryRecorder()
        val (s, engine) = MatchFactory.create(RuleSet.CLASSIC, intArrayOf(1, 1), 51L)
        s.positions[0] = 10
        s.lastDie = 4
        rec.record(s, engine.apply(MoveGen.legalMoves(s, 4, 0).first()))
        val encoded = rec.export()
        val back = TelemetryRecorder()
        back.import(encoded)
        assertEquals(rec.entries().size, back.entries().size)
        assertEquals(rec.entries().first().to, back.entries().first().to)
    }
}
