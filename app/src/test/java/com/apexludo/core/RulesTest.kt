package com.apexludo.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RulesTest {

    private fun fresh(rules: RuleSet = RuleSet.CLASSIC, players: Int = 4): Pair<GameState, MatchEngine> =
        MatchFactory.create(rules, IntArray(players) { 1 }, seed = 42L)

    @Test
    fun tokenNeedsASixToLeaveTheYard() {
        val (s, _) = fresh()
        for (die in 1..5) {
            assertTrue("die $die should not unlock", MoveGen.legalMoves(s, die, 0).isEmpty())
        }
        val six = MoveGen.legalMoves(s, 6, 0)
        assertEquals(4, six.size)
        assertTrue(six.all { it.unlocks && it.to == 1 })
    }

    @Test
    fun unlockOnOneRuleWorks() {
        val (s, _) = fresh(RuleSet.CLASSIC.copy(unlockRequiresSix = false))
        assertEquals(4, MoveGen.legalMoves(s, 1, 0).size)
    }

    @Test
    fun exactRollRequiredForHome() {
        val (s, _) = fresh()
        s.positions[0] = 55
        assertTrue("57 needs a 2", MoveGen.legalMoves(s, 3, 0).none { it.token == 0 })
        assertTrue(MoveGen.legalMoves(s, 2, 0).any { it.token == 0 && it.entersHome })
        assertTrue("rolling a 6 from 55 overshoots", MoveGen.legalMoves(s, 6, 0).none { it.token == 0 })
    }

    @Test
    fun clampModeAllowsOvershoot() {
        val (s, _) = fresh(RuleSet.CLASSIC.copy(exactRollToHome = false))
        s.positions[0] = 55
        val m = MoveGen.legalMoves(s, 6, 0).first { it.token == 0 }
        assertEquals(Board.HOME_PROGRESS, m.to)
    }

    @Test
    fun captureSendsOpponentHome() {
        val (s, engine) = fresh()
        // RED token at progress 10 (ring idx 9), GREEN token one step behind at idx 8.
        s.positions[0] = 5          // ring idx 4
        s.positions[4] = 6          // GREEN ring idx (13+5)=18 -> move to idx 4?
        // Simpler: place GREEN token on the cell RED will land on.
        val redTarget = Board.ringIndexOf(0, 5 + 3)  // idx 7
        // find a GREEN progress whose ring index equals redTarget
        var greenProgress = -1
        for (p in 1..51) if (Board.ringIndexOf(1, p) == redTarget) { greenProgress = p; break }
        assertTrue(greenProgress > 0)
        s.positions[4] = greenProgress
        s.lastDie = 3
        val moves = MoveGen.legalMoves(s, 3, 0)
        val capture = moves.firstOrNull { it.token == 0 && it.captures.contains(4) }
        assertTrue("expected capture move, got $moves", capture != null)
        val out = engine.apply(capture!!)
        assertEquals(1, out.captured.size)
        assertEquals(0, s.positions[4])
    }

    @Test
    fun safeStarPreventsCapture() {
        val (s, _) = fresh()
        val safeIdx = Board.STAR_INDEX[0]     // idx 8
        var redProgress = -1
        for (p in 1..51) if (Board.ringIndexOf(0, p) == safeIdx - 3) { redProgress = p; break }
        var greenProgress = -1
        for (p in 1..51) if (Board.ringIndexOf(1, p) == safeIdx) { greenProgress = p; break }
        s.positions[0] = redProgress
        s.positions[4] = greenProgress
        val moves = MoveGen.legalMoves(s, 3, 0)
        assertTrue("no capture on a safe star", moves.none { it.captures.isNotEmpty() })
    }

    @Test
    fun blockadeBlocksLanding() {
        val (s, _) = fresh()
        val target = Board.ringIndexOf(0, 6)   // where RED lands with a 3 from progress 3
        var greenProgress = -1
        for (p in 1..51) if (Board.ringIndexOf(1, p) == target) { greenProgress = p; break }
        assertTrue(greenProgress > 0)
        s.positions[0] = 3
        // Two GREEN tokens on the same square = a blockade.
        s.positions[4] = greenProgress
        s.positions[5] = greenProgress
        val moves = MoveGen.legalMoves(s, 3, 0)
        assertTrue("blockade must not be landable: $moves", moves.none { it.token == 0 })
    }

    @Test
    fun blockadeStopsPassage() {
        val (s, _) = fresh()
        val mid = Board.ringIndexOf(0, 4)
        var greenProgress = -1
        for (p in 1..51) if (Board.ringIndexOf(1, p) == mid) { greenProgress = p; break }
        assertTrue(greenProgress > 0)
        s.positions[0] = 1
        s.positions[4] = greenProgress
        s.positions[5] = greenProgress
        // Rolling 5 would have to cross the blockade at progress 4.
        val moves = MoveGen.legalMoves(s, 5, 0)
        assertTrue("passage must be blocked: $moves", moves.none { it.token == 0 })
        // ... and with blockades disabled the same roll is legal again.
        val (s2, _) = fresh(RuleSet.CLASSIC.copy(blockadesEnabled = false))
        s2.positions[0] = 1
        s2.positions[4] = greenProgress
        s2.positions[5] = greenProgress
        assertTrue(MoveGen.legalMoves(s2, 5, 0).any { it.token == 0 })
    }

    @Test
    fun twoDifferentOpponentsOnOneCellAreBothCaptured() {
        // Regression: this used to leave two players sharing an unprotected cell, because
        // the "victims.size >= 2" test treated a mixed stack as a blockade.
        val (s, engine) = fresh()
        val target = Board.ringIndexOf(0, 6)      // RED lands here with a 3 from progress 3
        var greenProgress = -1
        var yellowProgress = -1
        for (p in 1..51) {
            if (greenProgress < 0 && Board.ringIndexOf(1, p) == target) greenProgress = p
            if (yellowProgress < 0 && Board.ringIndexOf(2, p) == target) yellowProgress = p
        }
        assertTrue(greenProgress > 0 && yellowProgress > 0)
        s.positions[0] = 3
        s.positions[4] = greenProgress
        s.positions[8] = yellowProgress          // YELLOW's first token
        s.lastDie = 3
        val move = MoveGen.legalMoves(s, 3, 0).firstOrNull { it.token == 0 }
        assertTrue("move should be legal and capture both", move != null && move.captures.size == 2)
        val out = engine.apply(move!!)
        assertEquals(2, out.captured.size)
        assertEquals(0, s.positions[4])
        assertEquals(0, s.positions[8])
    }

    @Test
    fun twoPlayerCaptureUsesColourIndexNotSeatIndex() {
        // Regression: GameState looked up ring cells with the *seat* index instead of the
        // *colour* index, so in 2-player matches (colours RED + YELLOW) captures silently
        // stopped working. Found by the 10k-cycle fuzz run.
        val (s, engine) = MatchFactory.create(RuleSet.CLASSIC, intArrayOf(1, 1), 6L)
        // Seat 1 is YELLOW: progress 32 -> ring index 5. Seat 0 (RED) reaches index 5 at progress 6.
        s.positions[4] = 32
        s.positions[0] = 2
        s.lastDie = 4
        val move = MoveGen.legalMoves(s, 4, 0).firstOrNull { it.token == 0 }
        assertTrue("expected a capturing move, got $move", move != null && move.captures.contains(4))
        engine.apply(move!!)
        assertEquals(0, s.positions[4])
    }

    @Test
    fun ownTokensStackFreely() {
        val (s, _) = fresh()
        s.positions[0] = 5
        s.positions[1] = 5
        val moves = MoveGen.legalMoves(s, 2, 0)
        assertTrue(moves.any { it.token == 0 })
    }

    @Test
    fun threeSixesForfeitTheTurn() {
        val (s, engine) = fresh()
        var forfeits = 0
        var guard = 0
        while (guard++ < 400) {
            val before = s.turn
            val r = engine.roll()
            if (r.forfeited) {
                forfeits++
                assertTrue(s.turn != before)
                break
            }
            if (r.hasMoves) engine.apply(r.moves[0]) else engine.pass()
        }
        assertTrue("triple six should eventually occur", forfeits >= 1)
    }

    @Test
    fun extraTurnOnSix() {
        val (s, engine) = fresh()
        s.positions[0] = 10
        s.lastDie = 6
        s.sixStreak = 1
        val moves = MoveGen.legalMoves(s, 6, 0)
        val out = engine.apply(moves[0])
        assertTrue(out.extraTurn)
        assertEquals(0, s.turn)
    }

    @Test
    fun turnPassesWithoutSix() {
        val (s, engine) = fresh()
        s.positions[0] = 10
        s.lastDie = 4
        val moves = MoveGen.legalMoves(s, 4, 0)
        engine.apply(moves[0])
        assertEquals(1, s.turn)
    }

    @Test
    fun finishingPlayerGetsRankAndIsSkipped() {
        val (s, engine) = fresh()
        for (t in 0..3) s.positions[t] = Board.HOME_PROGRESS
        s.positions[3] = 56
        s.lastDie = 1
        val move = MoveGen.legalMoves(s, 1, 0).first { it.token == 3 }
        val out = engine.apply(move)
        assertEquals(0, out.finishedPlayer)
        assertEquals(1, s.rank[0])
        assertEquals(1, s.turn)
    }

    @Test
    fun lastPlayerStandingWinsMatch() {
        val (s, engine) = fresh()
        // Everyone but RED has finished.
        for (p in 1..3) {
            for (t in s.tokenIds(p)) s.positions[t] = Board.HOME_PROGRESS
            s.rank[p] = p              // 1st, 2nd, 3rd
        }
        s.nextRank = 4
        for (t in 0..3) s.positions[t] = Board.HOME_PROGRESS
        s.positions[3] = 56
        s.lastDie = 1
        engine.apply(MoveGen.legalMoves(s, 1, 0).first { it.token == 3 })
        assertTrue(s.matchOver)
        assertEquals(4, s.rank[0])          // last player standing takes the final rank
        assertEquals(1, s.winner)           // GREEN had already won
    }

    @Test
    fun noMovesMeansPassAndTurnAdvances() {
        val (s, engine) = fresh()
        // All tokens in the yard, rolled a 3 -> nothing legal.
        val r = engine.roll()
        if (!r.forfeited && !r.hasMoves) {
            val before = s.turn
            engine.pass()
            assertTrue(s.turn != before)
        }
    }

    @Test
    fun twoPlayerMatchUsesOppositeSeats() {
        val (s, _) = MatchFactory.create(RuleSet.CLASSIC, intArrayOf(0, 1), 7L)
        assertEquals(2, s.playerCount)
        assertEquals(0, s.colorOf(0))
        assertEquals(2, s.colorOf(1))
    }

    @Test
    fun battleModeBlocksEarlyCaptures() {
        val (s, _) = MatchFactory.create(RuleSet.BATTLE, intArrayOf(1, 1), 9L)
        val target = Board.ringIndexOf(0, 2)
        var greenProgress = -1
        for (p in 1..51) if (Board.ringIndexOf(1, p) == target) { greenProgress = p; break }
        s.positions[0] = 1
        s.positions[4] = greenProgress
        val moves = MoveGen.legalMoves(s, 1, 0)
        assertTrue(moves.none { it.captures.isNotEmpty() })
    }

    @Test
    fun checksumChangesWhenStateChanges() {
        val (s, _) = fresh()
        val a = s.checksum()
        s.positions[0] = 12
        assertFalse(a == s.checksum())
    }

    @Test
    fun cloneIsIndependent() {
        val (s, _) = fresh()
        s.positions[0] = 20
        val c = s.clone()
        c.positions[0] = 30
        assertEquals(20, s.positions[0])
        assertEquals(s.checksum(), s.clone().checksum())
    }
}
