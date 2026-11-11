package com.apexludo.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Headless fuzz + stress suite (spec parts 3 and 5).
 * Simulates tens of thousands of turn cycles across rule sets, player counts and seeds,
 * asserting board invariants after every single action.
 */
class StressTest {

    private val ruleSets = listOf(
        RuleSet.CLASSIC, RuleSet.QUICK, RuleSet.BATTLE, RuleSet.PEACEFUL,
        RuleSet.CLASSIC.copy(homeGrantsExtraTurn = false, safeStars = false),
        RuleSet.CLASSIC.copy(blockadesEnabled = false, exactRollToHome = false),
        RuleSet.CLASSIC.copy(unlockRequiresSix = false, threeSixesForfeit = false),
        RuleSet.CLASSIC.copy(captureGrantsExtraTurn = true, homeGrantsExtraTurn = false)
    )

    @Test
    fun fuzzTenThousandTurnCyclesKeepEveryInvariant() {
        var cycles = 0
        var seed = 1L
        while (cycles < 10_000) {
            val rules = ruleSets[(seed % ruleSets.size).toInt()]
            val players = 2 + (seed % 3).toInt()
            val (s, engine) = MatchFactory.create(rules, IntArray(players) { 1 }, seed)
            val ai = AiEngine(AiProfile.TACTICAL, seed)
            var guard = 0
            while (cycles < 10_000 && guard < 4000 && !s.matchOver) {
                val r = engine.roll()
                cycles++
                assertTrue("die range", r.die in 1..6)
                if (!r.forfeited && r.hasMoves) {
                    val move = ai.chooseMove(s, r.die, r.moves)
                    val out = engine.apply(move)
                    assertInvariants(s, "after move (rules=${rules.describe()} players=$players seed=$seed)")
                    assertEquals("captures must reset", 0, out.captured.count { s.positions[it] != 0 })
                } else if (!r.forfeited) {
                    engine.pass()
                }
                guard++
            }
            assertInvariants(s, "end of match seed=$seed")
            seed++
        }
        assertTrue(cycles >= 10_000)
    }

    @Test
    fun twoHundredFullMatchesTerminateWithAValidWinner() {
        for (i in 0 until 200) {
            val rules = ruleSets[i % ruleSets.size]
            val players = 2 + (i % 3)
            val (s, engine) = MatchFactory.create(rules, IntArray(players) { 1 }, i.toLong() * 7919 + 13)
            val ai = AiEngine(AiProfile.byId(AiProfile.ALL[i % AiProfile.ALL.size].id), i.toLong())
            val turns = engine.playToEnd(ai)
            assertTrue("match $i never finished", s.matchOver)
            assertTrue("match $i winner", s.winner in 0 until players)
            assertTrue("match $i turns=$turns", turns in 10..60_000)
            val first = s.rank.count { it == 1 }
            assertEquals("match $i must have exactly one winner", 1, first)
            assertInvariants(s, "finished match $i")
        }
    }

    @Test
    fun manyTokensOnOneCellNeverCorruptsTheBoard() {
        val (s, engine) = MatchFactory.create(RuleSet.CLASSIC, intArrayOf(1, 1, 1, 1), 99L)
        // Cram all sixteen tokens onto two adjacent ring cells.
        for (p in 0 until 4) {
            for ((i, t) in s.tokenIds(p).withIndex()) {
                s.positions[t] = if (i % 2 == 0) 4 else 5
            }
        }
        assertInvariants(s, "crammed board")
        for (die in 1..6) {
            val moves = MoveGen.legalMoves(s, die, 0)
            for (m in moves) {
                val sim = s.clone()
                val e2 = MatchEngine(sim)
                sim.lastDie = die
                e2.apply(m)
                assertInvariants(sim, "crammed apply die=$die")
            }
        }
        // Engine still functions.
        val r = engine.roll()
        if (!r.forfeited && r.hasMoves) engine.apply(r.moves.first())
        assertInvariants(s, "crammed engine turn")
    }

    @Test
    fun yardTokensNeverAppearOnTheBoardByAccident() {
        val (s, engine) = MatchFactory.create(RuleSet.CLASSIC, intArrayOf(1, 1, 1, 1), 123L)
        val ai = AiEngine(AiProfile.BALANCED, 123L)
        repeat(3000) {
            if (s.matchOver) return@repeat
            val r = engine.roll()
            if (!r.forfeited && r.hasMoves) engine.apply(ai.chooseMove(s, r.die, r.moves)) else if (!r.forfeited) engine.pass()
            for (t in s.positions.indices) {
                val p = s.positions[t]
                assertTrue("token $t progress $p out of range", p in 0..57)
            }
        }
    }

    @Test
    fun rolloutsNeverMutateTheRealState() {
        val (s, _) = MatchFactory.create(RuleSet.CLASSIC, intArrayOf(1, 1, 1, 1), 321L)
        s.positions[0] = 15
        val before = s.checksum()
        val moves = MoveGen.legalMoves(s, 3, 0)
        AiEngine(AiProfile.GRANDMASTER, 7L).chooseMove(s, 3, moves)
        assertEquals("rollouts leaked into live state", before, s.checksum())
    }

    private fun assertInvariants(s: GameState, where: String) {
        for (t in s.positions.indices) {
            assertTrue("$where: token $t progress ${s.positions[t]}", s.positions[t] in 0..Board.HOME_PROGRESS)
        }
        // No two different players on the same ring cell (safe cells included: capture clears them).
        val occupancy = HashMap<Int, MutableSet<Int>>()
        for (p in 0 until s.playerCount) {
            for (t in s.tokenIds(p)) {
                val pr = s.positions[t]
                if (pr !in 1..51) continue
                val idx = Board.ringIndexOf(s.colorOf(p), pr)
                occupancy.getOrPut(idx) { HashSet() }.add(p)
            }
        }
        for ((idx, players) in occupancy) {
            // A shared cell is only legal if the cell is capture-protected (a star/start),
            // which can happen transiently on safe cells.
            if (players.size > 1) {
                val protectedCell = Board.START_INDEX.contains(idx) || Board.STAR_INDEX.contains(idx)
                assertTrue("$where: players $players share unprotected cell $idx", protectedCell)
            }
        }
        for (p in 0 until s.playerCount) {
            assertTrue("$where: rank ${s.rank[p]} invalid", s.rank[p] in -1..s.playerCount)
        }
        if (s.matchOver) assertTrue("$where: winner set", s.winner >= 0)
    }
}
