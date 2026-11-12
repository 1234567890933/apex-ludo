package com.apexludo.app

import android.content.Context
import android.content.SharedPreferences
import com.apexludo.core.RuleSet

/**
 * Local-only settings store. No network, no accounts: everything lives in
 * SharedPreferences on the device.
 */
class Prefs(ctx: Context) {

    private val sp: SharedPreferences =
        ctx.getSharedPreferences("apex_ludo_prefs", Context.MODE_PRIVATE)

    // ---- audio
    var soundOn: Boolean
        get() = sp.getBoolean("sound_on", true)
        set(v) = sp.edit().putBoolean("sound_on", v).apply()

    var musicOn: Boolean
        get() = sp.getBoolean("music_on", true)
        set(v) = sp.edit().putBoolean("music_on", v).apply()

    var sfxVolume: Float
        get() = sp.getFloat("sfx_vol", 0.85f)
        set(v) = sp.edit().putFloat("sfx_vol", v.coerceIn(0f, 1f)).apply()

    var musicVolume: Float
        get() = sp.getFloat("music_vol", 0.45f)
        set(v) = sp.edit().putFloat("music_vol", v.coerceIn(0f, 1f)).apply()

    // ---- feel
    var haptics: Boolean
        get() = sp.getBoolean("haptics", true)
        set(v) = sp.edit().putBoolean("haptics", v).apply()

    var batterySaver: Boolean
        get() = sp.getBoolean("battery_saver", false)
        set(v) = sp.edit().putBoolean("battery_saver", v).apply()

    /** 0 = low, 1 = medium, 2 = high */
    var particleDensity: Int
        get() = sp.getInt("particles", 1)
        set(v) = sp.edit().putInt("particles", v.coerceIn(0, 2)).apply()

    /** 0 = relaxed (0.75x), 1 = normal, 2 = fast (1.6x) */
    var animSpeedIndex: Int
        get() = sp.getInt("anim_speed", 1)
        set(v) = sp.edit().putInt("anim_speed", v.coerceIn(0, 2)).apply()

    val animScale: Float
        get() = when (animSpeedIndex) { 0 -> 0.75f; 2 -> 1.6f; else -> 1f }

    var highContrast: Boolean
        get() = sp.getBoolean("high_contrast", false)
        set(v) = sp.edit().putBoolean("high_contrast", v).apply()

    // ---- looks
    var themeId: String
        get() = sp.getString("theme", "day") ?: "day"
        set(v) = sp.edit().putString("theme", v).apply()

    var boardSkin: String
        get() = sp.getString("board_skin", "classic") ?: "classic"
        set(v) = sp.edit().putString("board_skin", v).apply()

    var tokenSkin: String
        get() = sp.getString("token_skin", "pawn") ?: "pawn"
        set(v) = sp.edit().putString("token_skin", v).apply()

    var language: String
        get() = sp.getString("lang", "system") ?: "system"
        set(v) = sp.edit().putString("lang", v).apply()

    // ---- gameplay
    var assist: Boolean
        get() = sp.getBoolean("assist", false)
        set(v) = sp.edit().putBoolean("assist", v).apply()

    var showFps: Boolean
        get() = sp.getBoolean("show_fps", false)
        set(v) = sp.edit().putBoolean("show_fps", v).apply()

    /** 0 = quick, 1 = normal, 2 = deep */
    var aiBudget: Int
        get() = sp.getInt("ai_budget", 1)
        set(v) = sp.edit().putInt("ai_budget", v.coerceIn(0, 2)).apply()

    var autoRoll: Boolean
        get() = sp.getBoolean("auto_roll", true)
        set(v) = sp.edit().putBoolean("auto_roll", v).apply()

    var lastBotProfile: String
        get() = sp.getString("bot_profile", "tactical") ?: "tactical"
        set(v) = sp.edit().putString("bot_profile", v).apply()

    var lastSeatCount: Int
        get() = sp.getInt("seat_count", 4)
        set(v) = sp.edit().putInt("seat_count", v.coerceIn(2, 4)).apply()

    // ---- progression (local only)
    var coins: Int
        get() = sp.getInt("coins", 120)
        set(v) = sp.edit().putInt("coins", v.coerceAtLeast(0)).apply()

    var streak: Int
        get() = sp.getInt("streak", 0)
        set(v) = sp.edit().putInt("streak", v).apply()

    var lastClaimDay: Long
        get() = sp.getLong("last_claim_day", 0L)
        set(v) = sp.edit().putLong("last_claim_day", v).apply()

    // ---- stats
    var matches: Int
        get() = sp.getInt("st_matches", 0)
        set(v) = sp.edit().putInt("st_matches", v).apply()

    var wins: Int
        get() = sp.getInt("st_wins", 0)
        set(v) = sp.edit().putInt("st_wins", v).apply()

    var captures: Int
        get() = sp.getInt("st_captures", 0)
        set(v) = sp.edit().putInt("st_captures", v).apply()

    var homes: Int
        get() = sp.getInt("st_homes", 0)
        set(v) = sp.edit().putInt("st_homes", v).apply()

    var bestStreak: Int
        get() = sp.getInt("st_best_streak", 0)
        set(v) = sp.edit().putInt("st_best_streak", v).apply()

    fun resetStats() {
        sp.edit().putInt("st_matches", 0).putInt("st_wins", 0)
            .putInt("st_captures", 0).putInt("st_homes", 0)
            .putInt("st_best_streak", 0).apply()
    }

    // ---- custom rule set
    var customRules: RuleSet
        get() = RuleSet(
            blockadesEnabled = sp.getBoolean("r_blockades", true),
            blockadesBlockPassing = sp.getBoolean("r_block_pass", true),
            threeSixesForfeit = sp.getBoolean("r_three_six", true),
            unlockRequiresSix = sp.getBoolean("r_six_unlock", true),
            sixGrantsExtraTurn = sp.getBoolean("r_six_extra", true),
            captureGrantsExtraTurn = sp.getBoolean("r_capture_extra", false),
            homeGrantsExtraTurn = sp.getBoolean("r_home_extra", true),
            exactRollToHome = sp.getBoolean("r_exact_home", true),
            safeStars = sp.getBoolean("r_safe_stars", true),
            safeStartCells = sp.getBoolean("r_safe_start", true),
            turnTimerSeconds = sp.getInt("r_timer", 25),
            battleMode = sp.getBoolean("r_battle", false)
        )
        set(v) = sp.edit()
            .putBoolean("r_blockades", v.blockadesEnabled)
            .putBoolean("r_block_pass", v.blockadesBlockPassing)
            .putBoolean("r_three_six", v.threeSixesForfeit)
            .putBoolean("r_six_unlock", v.unlockRequiresSix)
            .putBoolean("r_six_extra", v.sixGrantsExtraTurn)
            .putBoolean("r_capture_extra", v.captureGrantsExtraTurn)
            .putBoolean("r_home_extra", v.homeGrantsExtraTurn)
            .putBoolean("r_exact_home", v.exactRollToHome)
            .putBoolean("r_safe_stars", v.safeStars)
            .putBoolean("r_safe_start", v.safeStartCells)
            .putInt("r_timer", v.turnTimerSeconds)
            .putBoolean("r_battle", v.battleMode)
            .apply()

    // ---- cosmetics and unlocks (all local, nothing to buy with real money)
    private val freeItems = setOf(
        "theme:day", "theme:night", "theme:contrast",
        "board:classic", "token:pawn", "avatar:crown"
    )

    var owned: MutableSet<String>
        get() = HashSet(sp.getStringSet("owned", freeItems) ?: freeItems)
        set(v) = sp.edit().putStringSet("owned", HashSet(v + freeItems)).apply()

    fun owns(id: String): Boolean = owned.contains(id) || freeItems.contains(id)

    fun unlock(id: String) {
        val set = owned
        set.add(id)
        owned = set
    }

    var avatarId: String
        get() = sp.getString("avatar", "crown") ?: "crown"
        set(v) = sp.edit().putString("avatar", v).apply()

    var titleId: Int
        get() = sp.getInt("title", 0)
        set(v) = sp.edit().putInt("title", v).apply()

    var shields: Int
        get() = sp.getInt("shields", 1)
        set(v) = sp.edit().putInt("shields", v.coerceAtLeast(0)).apply()

    var watchSpeed: Int
        get() = sp.getInt("watch_speed", 1)
        set(v) = sp.edit().putInt("watch_speed", v.coerceIn(0, 2)).apply()

    var presetId: String
        get() = sp.getString("preset", "classic") ?: "classic"
        set(v) = sp.edit().putString("preset", v).apply()
}
