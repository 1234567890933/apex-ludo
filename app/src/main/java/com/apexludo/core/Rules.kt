package com.apexludo.core

/**
 * Apex Ludo — rule set. Every competitive toggle from the specification lives here,
 * so the UI only has to flip booleans and the (fully tested) core does the rest.
 */
data class RuleSet(
    /** Two or more of one colour on a cell form a blockade. */
    val blockadesEnabled: Boolean = true,
    /** A blockade physically blocks opponents from landing on / passing through it. */
    val blockadesBlockPassing: Boolean = true,
    /** Rolling three sixes in a row forfeits the turn. */
    val threeSixesForfeit: Boolean = true,
    /** A token may only leave the yard on a six. */
    val unlockRequiresSix: Boolean = true,
    /** An extra roll is granted on a six. */
    val sixGrantsExtraTurn: Boolean = true,
    /** An extra roll is granted after a capture. */
    val captureGrantsExtraTurn: Boolean = false,
    /** An extra roll is granted when a token reaches home. */
    val homeGrantsExtraTurn: Boolean = true,
    /** The exact number is required to enter home (otherwise it clamps). */
    val exactRollToHome: Boolean = true,
    /** Star cells are capture-protected. */
    val safeStars: Boolean = true,
    /** Capture-protection also applies to the four coloured start cells. */
    val safeStartCells: Boolean = true,
    /** Turn timer length in seconds; 0 disables the timer. */
    val turnTimerSeconds: Int = 25,
    /** Capture is only allowed when the capturing token has left its own half (battle mode). */
    val battleMode: Boolean = false
) {
    fun sanitized(): RuleSet = copy(
        turnTimerSeconds = turnTimerSeconds.coerceIn(0, 120)
    )

    fun describe(): String = buildString {
        append(if (blockadesEnabled) "blockades" else "no-blockades")
        append(if (exactRollToHome) "/exact-home" else "/clamp-home")
        append(if (threeSixesForfeit) "/3x6-forfeit" else "/no-3x6")
        if (battleMode) append("/battle")
    }

    companion object {
        val CLASSIC = RuleSet()
        val QUICK = RuleSet(
            blockadesEnabled = false,
            threeSixesForfeit = false,
            homeGrantsExtraTurn = true,
            turnTimerSeconds = 15
        )
        val BATTLE = RuleSet(
            battleMode = true,
            captureGrantsExtraTurn = true,
            safeStars = false,
            blockadesEnabled = false
        )
        val PEACEFUL = RuleSet(
            blockadesEnabled = true,
            captureGrantsExtraTurn = true,
            safeStars = true,
            turnTimerSeconds = 40
        )
    }
}
