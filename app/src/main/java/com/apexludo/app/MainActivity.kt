package com.apexludo.app

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.apexludo.core.AiProfile
import com.apexludo.core.RuleSet
import java.util.Locale

/**
 * Single-activity shell. The board is one custom view; every screen is a programmatic
 * overlay card so nothing can fail to inflate at runtime.
 */
class MainActivity : Activity() {

    private lateinit var root: FrameLayout
    private lateinit var game: GameView
    private lateinit var overlayLayer: FrameLayout

    private lateinit var prefsStore: Prefs
    private lateinit var audio: SoundEngine
    private lateinit var vibe: Haptics
    private val perf = PerfMonitor()

    var session: Session? = null
        private set

    private var overlayOpen = false
    private var pendingSetupPassAndPlay = false
    private var tutorialPage = 0

    // ---------------------------------------------------------------- lifecycle
    override fun attachBaseContext(newBase: Context) {
        val p = Prefs(newBase)
        val lang = p.language
        if (lang == "system") {
            super.attachBaseContext(newBase)
            return
        }
        try {
            val locale = Locale(lang)
            Locale.setDefault(locale)
            val cfg = Configuration(newBase.resources.configuration)
            cfg.setLocale(locale)
            super.attachBaseContext(newBase.createConfigurationContext(cfg))
        } catch (t: Throwable) {
            super.attachBaseContext(newBase)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLog.install(this)
        prefsStore = Prefs(this)
        audio = SoundEngine(this)
        audio.init()
        vibe = Haptics(this)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        applyImmersive()

        root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)

        game = GameView(this)
        game.perf = perf
        root.addView(game, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        overlayLayer = FrameLayout(this)
        root.addView(overlayLayer, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        setContentView(root)
        applyVisualPrefs()
        game.bindInput(
            dice = { session?.onDiceTap() },
            pause = { showPauseMenu() },
            token = { session?.onTokenTap(it) }
        )
        audio.startMusic()
        showMenu()
    }

    override fun onResume() {
        super.onResume()
        audio.startMusic()
        if (prefsStore.musicOn) audio.updateMusicVolume(prefsStore.musicVolume)
    }

    override fun onPause() {
        super.onPause()
        audio.pauseMusic()
    }

    override fun onDestroy() {
        super.onDestroy()
        session?.destroy()
        session = null
        audio.release()
    }

    override fun onBackPressed() {
        when {
            overlayOpen -> {
                val s = session
                if (s != null && !s.matchFinished && s.phase != Session.Phase.IDLE) showPauseMenu()
                else closeOverlay()
            }
            session != null -> showPauseMenu()
            else -> showMenu()
        }
    }

    private fun applyImmersive() {
        try {
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
        } catch (t: Throwable) {
            // non-fatal
        }
    }

    fun currentTheme(): Theme = Themes.byId(prefsStore.themeId)

    fun prefs(): Prefs = prefsStore
    fun sound(): SoundEngine = audio
    fun haptics(): Haptics = vibe

    private fun applyVisualPrefs() {
        val theme = currentTheme()
        game.theme = theme
        game.boardSkin = BoardSkins.byId(prefsStore.boardSkin)
        game.tokenSkin = TokenSkins.byId(prefsStore.tokenSkin)
        game.particleDensity = prefsStore.particleDensity
        game.animScale = prefsStore.animScale
        game.batterySaver = prefsStore.batterySaver
        game.showPerf = prefsStore.showFps
        game.invalidate()

        audio.masterOn = prefsStore.soundOn
        audio.musicOn = prefsStore.musicOn
        audio.sfxVolume = prefsStore.sfxVolume
        audio.updateMusicVolume(prefsStore.musicVolume)
        if (prefsStore.soundOn && prefsStore.musicOn) audio.startMusic() else audio.pauseMusic()
        vibe.enabled = prefsStore.haptics
    }

    fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    // ---------------------------------------------------------------- overlay plumbing
    private fun openOverlay(content: View, dismissible: Boolean = true) {
        overlayLayer.removeAllViews()
        overlayOpen = true
        val scrim = View(this)
        scrim.setBackgroundColor(Color.argb(190, 0, 0, 0))
        scrim.isClickable = true
        if (dismissible) scrim.setOnClickListener { closeOverlay() }
        overlayLayer.addView(scrim, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val wrapper = FrameLayout(this)
        val maxH = (resources.displayMetrics.heightPixels * 0.86f).toInt()
        val cardWidth = minOf(resources.displayMetrics.widthPixels - Ui.dp(this, 28f), Ui.dp(this, 520f))
        val params = FrameLayout.LayoutParams(cardWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
        params.gravity = Gravity.CENTER
        wrapper.addView(content)
        overlayLayer.addView(wrapper, params)

        content.alpha = 0f
        content.scaleX = 0.88f
        content.scaleY = 0.88f
        content.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(240).setInterpolator(android.view.animation.OvershootInterpolator(0.9f)).start()
        audio.open()
    }

    private fun closeOverlay() {
        overlayLayer.removeAllViews()
        overlayOpen = false
        audio.close()
    }

    private fun card(): LinearLayout {
        val theme = currentTheme()
        val c = Ui.panel(this, theme)
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        c.layoutParams = lp
        return c
    }

    private fun scrollCard(content: LinearLayout): View {
        val sv = ScrollView(this)
        sv.isFillViewport = false
        sv.addView(content)
        val maxH = (resources.displayMetrics.heightPixels * 0.82f).toInt()
        sv.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, maxH)
        val holder = LinearLayout(this)
        holder.orientation = LinearLayout.VERTICAL
        holder.addView(sv)
        return holder
    }

    private fun header(c: LinearLayout, title: String, subtitle: String? = null) {
        val theme = currentTheme()
        c.addView(Ui.title(this, theme, title, 22f))
        if (subtitle != null) c.addView(Ui.body(this, theme, subtitle, 13f, dim = true))
        c.addView(Ui.spacer(this, 6f))
    }

    // ---------------------------------------------------------------- home
    private fun showMenu() {
        val theme = currentTheme()
        val c = card()
        c.addView(Ui.title(this, theme, getString(R.string.app_name), 30f))
        val sub = Ui.row(this)
        sub.addView(Ui.body(this, theme, getString(R.string.tagline), 13.5f, dim = true))
        sub.addView(Ui.space(this))
        sub.addView(Ui.chip(this, theme, getString(R.string.version_label), theme.accent))
        c.addView(sub)
        c.addView(Ui.spacer(this, 10f))

        val stats = Ui.row(this)
        stats.addView(Ui.chip(this, theme, getString(R.string.coins) + ": " + prefsStore.coins, theme.playerColors[2]))
        stats.addView(Ui.spacer(this, 0f))
        stats.addView(View(this).also { it.layoutParams = LinearLayout.LayoutParams(Ui.dp(this, 8f), 1) })
        stats.addView(Ui.chip(this, theme, getString(R.string.daily_streak) + ": " + prefsStore.streak, theme.playerColors[1]))
        c.addView(stats)
        c.addView(Ui.spacer(this, 12f))

        c.addView(Ui.button(this, theme, getString(R.string.play_quick), primary = true) {
            audio.tap(); pendingSetupPassAndPlay = false; showSetup(false)
        })
        c.addView(Ui.spacer(this, 8f))
        c.addView(Ui.button(this, theme, getString(R.string.play_pass)) {
            audio.tap(); pendingSetupPassAndPlay = true; showSetup(true)
        })
        c.addView(Ui.spacer(this, 8f))

        val grid1 = Ui.row(this)
        grid1.addView(Ui.button(this, theme, getString(R.string.custom_rules)) { audio.tap(); showRules() },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        grid1.addView(View(this).also { it.layoutParams = LinearLayout.LayoutParams(Ui.dp(this, 8f), 1) })
        grid1.addView(Ui.button(this, theme, getString(R.string.ai_profiles)) { audio.tap(); showAiProfiles() },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        c.addView(grid1)
        c.addView(Ui.spacer(this, 8f))

        val grid2 = Ui.row(this)
        grid2.addView(Ui.button(this, theme, getString(R.string.stats)) { audio.tap(); showStats() },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        grid2.addView(View(this).also { it.layoutParams = LinearLayout.LayoutParams(Ui.dp(this, 8f), 1) })
        grid2.addView(Ui.button(this, theme, getString(R.string.settings)) { audio.tap(); showSettings() },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        c.addView(grid2)
        c.addView(Ui.spacer(this, 8f))

        val grid3 = Ui.row(this)
        grid3.addView(Ui.button(this, theme, getString(R.string.tutorial)) { audio.tap(); tutorialPage = 0; showTutorial() },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        grid3.addView(View(this).also { it.layoutParams = LinearLayout.LayoutParams(Ui.dp(this, 8f), 1) })
        grid3.addView(Ui.button(this, theme, getString(R.string.about)) { audio.tap(); showAbout() },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        c.addView(grid3)

        if (session != null && !session!!.matchFinished) {
            c.addView(Ui.spacer(this, 10f))
            c.addView(Ui.button(this, theme, getString(R.string.resume), primary = true) {
                audio.tap(); closeOverlay(); session?.resume()
            })
        }

        c.addView(Ui.spacer(this, 10f))
        val daily = Ui.row(this)
        val canClaim = canClaimDaily()
        daily.addView(Ui.button(this, theme,
            if (canClaim) getString(R.string.daily_claim) else getString(R.string.daily_claimed)) {
            if (canClaim) claimDaily() else toast(getString(R.string.daily_claimed))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        daily.addView(View(this).also { it.layoutParams = LinearLayout.LayoutParams(Ui.dp(this, 8f), 1) })
        daily.addView(Ui.button(this, theme, getString(R.string.replay_log)) { audio.tap(); showLog() },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        c.addView(daily)

        openOverlay(scrollCard(c), dismissible = session == null)
    }

    private fun dayIndex(): Long = System.currentTimeMillis() / 86_400_000L

    private fun canClaimDaily(): Boolean = prefsStore.lastClaimDay != dayIndex()

    private fun claimDaily() {
        val today = dayIndex()
        if (prefsStore.lastClaimDay == today) return
        val yesterday = today - 1
        prefsStore.streak = if (prefsStore.lastClaimDay == yesterday) prefsStore.streak + 1 else 1
        if (prefsStore.streak > prefsStore.bestStreak) prefsStore.bestStreak = prefsStore.streak
        prefsStore.lastClaimDay = today
        val reward = 20 + minOf(30, prefsStore.streak * 5)
        prefsStore.coins = prefsStore.coins + reward
        audio.home()
        toast(getString(R.string.daily_reward, reward))
        showMenu()
    }

    // ---------------------------------------------------------------- match setup
    private fun showSetup(passAndPlay: Boolean) {
        val theme = currentTheme()
        val c = card()
        header(c, getString(R.string.seats_title))

        val seatCount = prefsStore.lastSeatCount
        var count = seatCount
        var rules = when (prefsStore.presetId) {
            "quick" -> RuleSet.QUICK
            "battle" -> RuleSet.BATTLE
            "peaceful" -> RuleSet.PEACEFUL
            "custom" -> prefsStore.customRules
            else -> RuleSet.CLASSIC
        }
        val isBot = BooleanArray(4) { !(passAndPlay && it == 1) }
        if (!passAndPlay) {
            isBot[0] = false
            for (i in 1 until 4) isBot[i] = true
        } else {
            for (i in 0 until 4) isBot[i] = false
        }
        val profiles = Array(4) { AiProfile.byId(prefsStore.lastBotProfile) }

        val countLabel = Ui.body(this, theme, "${getString(R.string.players_count)}: $count", 14f)
        val rowCount = Ui.row(this)
        rowCount.addView(countLabel, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val minus = Ui.button(this, theme, "–") {
            if (count > 2) { count--; prefsStore.lastSeatCount = count; audio.tap(); showSetup(passAndPlay) }
        }
        val plus = Ui.button(this, theme, "+") {
            if (count < 4) { count++; prefsStore.lastSeatCount = count; audio.tap(); showSetup(passAndPlay) }
        }
        val mw = LinearLayout.LayoutParams(Ui.dp(this, 52f), ViewGroup.LayoutParams.WRAP_CONTENT)
        mw.rightMargin = Ui.dp(this, 8f)
        rowCount.addView(minus, mw)
        rowCount.addView(plus, LinearLayout.LayoutParams(Ui.dp(this, 52f), ViewGroup.LayoutParams.WRAP_CONTENT))
        c.addView(rowCount)
        c.addView(Ui.divider(this, theme))
        c.addView(Ui.spacer(this, 4f))

        val seatColors = seatColorMap(count)
        for (i in 0 until count) {
            val color = theme.playerColors[seatColors[i]]
            val row = Ui.row(this)
            row.setPadding(0, Ui.dp(this, 6f), 0, Ui.dp(this, 6f))
            val dot = View(this)
            val dotLp = LinearLayout.LayoutParams(Ui.dp(this, 14f), Ui.dp(this, 14f))
            dotLp.rightMargin = Ui.dp(this, 8f)
            dot.background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(color)
            }
            row.addView(dot, dotLp)
            val name = Ui.body(this, theme,
                when (i) { 0 -> getString(R.string.player_red); 1 -> getString(R.string.player_green)
                    2 -> getString(R.string.player_yellow); else -> getString(R.string.player_blue) }, 14f)
            name.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            row.addView(name)
            val seatBtn = Ui.button(this, theme, if (isBot[i]) getString(R.string.seat_bot) else getString(R.string.seat_human)) {
                isBot[i] = !isBot[i]
                audio.tap()
                showSetup(passAndPlay)
            }
            row.addView(seatBtn)
            c.addView(row)
        }

        c.addView(Ui.divider(this, theme))
        c.addView(Ui.spacer(this, 4f))

        val presetRow = Ui.row(this)
        presetRow.addView(Ui.body(this, theme, getString(R.string.rules_preset), 14f),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        presetRow.addView(Ui.chip(this, theme,
            getString(
                when (prefsStore.presetId) {
                    "quick" -> R.string.preset_quick
                    "battle" -> R.string.preset_battle
                    "peaceful" -> R.string.preset_peaceful
                    "custom" -> R.string.preset_custom
                    else -> R.string.preset_classic
                }
            ), theme.accent))
        c.addView(presetRow)
        val presetButtons = Ui.row(this)
        val presets = listOf("classic" to R.string.preset_classic, "quick" to R.string.preset_quick,
            "battle" to R.string.preset_battle, "custom" to R.string.preset_custom)
        for ((id, res) in presets) {
            val b = Ui.button(this, theme, getString(res)) {
                prefsStore.presetId = id
                audio.tap()
                showSetup(passAndPlay)
            }
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            lp.rightMargin = Ui.dp(this, 6f)
            presetButtons.addView(b, lp)
        }
        c.addView(presetButtons)
        c.addView(Ui.spacer(this, 10f))

        if (isBot.any { it }) {
            val botRow = Ui.row(this)
            botRow.addView(Ui.body(this, theme, getString(R.string.bot_style), 14f),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            botRow.addView(Ui.button(this, theme, profileLabel(AiProfile.byId(prefsStore.lastBotProfile))) {
                audio.tap(); showAiProfiles()
            })
            c.addView(botRow)
            c.addView(Ui.spacer(this, 10f))
        }

        c.addView(Ui.button(this, theme, getString(R.string.start_match), primary = true) {
            val seats = IntArray(count) { if (isBot[it]) 1 else 0 }
            val profs = Array(count) { profiles[it] }
            audio.tap()
            closeOverlay()
            startMatch(seats, profs, rules)
        })
        c.addView(Ui.spacer(this, 8f))
        c.addView(Ui.button(this, theme, getString(R.string.back)) { audio.tap(); showMenu() })
        openOverlay(scrollCard(c))
    }

    private fun seatColorMap(count: Int): IntArray = when (count) {
        2 -> intArrayOf(0, 2)
        3 -> intArrayOf(0, 1, 3)
        else -> intArrayOf(0, 1, 2, 3)
    }

    private fun profileLabel(p: AiProfile): String = getString(
        when (p.id) {
            "aggressive" -> R.string.ai_aggressive
            "defensive" -> R.string.ai_defensive
            "adaptive" -> R.string.ai_adaptive
            "grandmaster" -> R.string.ai_grandmaster
            "balanced" -> R.string.ai_balanced
            else -> R.string.ai_tactical
        }
    )

    fun startMatch(seats: IntArray, profiles: Array<AiProfile>, rules: RuleSet) {
        session?.destroy()
        val s = Session(this, rules, seats, profiles, System.nanoTime())
        session = s
        s.view = game
        game.stopAnimations()
        game.resetClock()
        game.session = s
        game.bindInput(
            dice = { s.onDiceTap() },
            pause = { showPauseMenu() },
            token = { s.onTokenTap(it) }
        )
        s.start()
        game.invalidate()
    }

    // ---------------------------------------------------------------- pause
    private fun showPauseMenu() {
        session?.pause()
        val theme = currentTheme()
        val c = card()
        header(c, getString(R.string.paused))
        c.addView(Ui.body(this, theme, session?.rulesSummary() ?: "", 12.5f, dim = true))
        c.addView(Ui.spacer(this, 8f))
        c.addView(Ui.button(this, theme, getString(R.string.resume), primary = true) {
            audio.tap(); closeOverlay(); session?.resume()
        })
        c.addView(Ui.spacer(this, 8f))
        c.addView(Ui.button(this, theme, getString(R.string.settings)) { audio.tap(); showSettings() })
        c.addView(Ui.spacer(this, 8f))

        val s = session
        if (s != null) {
            val afkRow = Ui.row(this)
            afkRow.addView(Ui.body(this, theme, getString(R.string.seat_afk), 14f),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            afkRow.addView(Ui.button(this, theme,
                if (s.afk.isNotEmpty() && s.afk[0]) getString(R.string.on) else getString(R.string.off)) {
                s.toggleAfk(0)
                showPauseMenu()
            })
            c.addView(afkRow)
            c.addView(Ui.spacer(this, 8f))
        }

        c.addView(Ui.button(this, theme, getString(R.string.restart)) {
            audio.tap()
            val s2 = session
            if (s2 != null) {
                val seats = IntArray(s2.state.playerCount) { if (s2.isHuman(it)) 0 else 1 }
                val profs = Array(s2.state.playerCount) { AiProfile.byId(prefsStore.lastBotProfile) }
                closeOverlay()
                startMatch(seats, profs, s2.rules)
            }
        })
        c.addView(Ui.spacer(this, 8f))
        c.addView(Ui.button(this, theme, getString(R.string.quit_match)) {
            audio.tap()
            session?.destroy()
            session = null
            game.session = null
            game.stopAnimations()
            closeOverlay()
            showMenu()
        })
        c.addView(Ui.spacer(this, 8f))
        c.addView(Ui.button(this, theme, getString(R.string.replay_log)) { audio.tap(); showLog() })
        openOverlay(scrollCard(c), dismissible = false)
    }

    // ---------------------------------------------------------------- settings
    private fun showSettings() {
        val theme = currentTheme()
        val c = card()
        header(c, getString(R.string.settings))

        c.addView(Ui.switchRow(this, theme, getString(R.string.sound), prefsStore.soundOn) {
            prefsStore.soundOn = it; applyVisualPrefs()
        })
        c.addView(Ui.switchRow(this, theme, getString(R.string.music), prefsStore.musicOn) {
            prefsStore.musicOn = it; applyVisualPrefs()
        })
        c.addView(Ui.sliderRow(this, theme, getString(R.string.sfx),
            (prefsStore.sfxVolume * 100).toInt(), 0, 100) {
            prefsStore.sfxVolume = it / 100f; applyVisualPrefs()
        })
        c.addView(Ui.sliderRow(this, theme, getString(R.string.music_volume),
            (prefsStore.musicVolume * 100).toInt(), 0, 100) {
            prefsStore.musicVolume = it / 100f; applyVisualPrefs()
        })
        c.addView(Ui.divider(this, theme))
        c.addView(Ui.switchRow(this, theme, getString(R.string.haptics), prefsStore.haptics) {
            prefsStore.haptics = it; vibe.enabled = it; if (it) vibe.tap()
        })
        c.addView(Ui.switchRow(this, theme, getString(R.string.battery_saver), prefsStore.batterySaver) {
            prefsStore.batterySaver = it; applyVisualPrefs()
        })
        c.addView(Ui.switchRow(this, theme, getString(R.string.show_fps), prefsStore.showFps) {
            prefsStore.showFps = it; applyVisualPrefs()
        })
        c.addView(Ui.sliderRow(this, theme, getString(R.string.particles), prefsStore.particleDensity, 0, 2) {
            prefsStore.particleDensity = it; applyVisualPrefs()
        })
        c.addView(Ui.sliderRow(this, theme, getString(R.string.anim_speed), prefsStore.animSpeedIndex, 0, 2) {
            prefsStore.animSpeedIndex = it; applyVisualPrefs()
        })
        c.addView(Ui.sliderRow(this, theme, getString(R.string.ai_budget), prefsStore.aiBudget, 0, 2) {
            prefsStore.aiBudget = it
        })
        c.addView(Ui.divider(this, theme))

        // theme picker
        c.addView(Ui.body(this, theme, getString(R.string.theme), 14f))
        val themeRow = Ui.row(this)
        for (t in Themes.ALL) {
            val b = Ui.button(this, theme, getString(t.nameRes)) {
                prefsStore.themeId = t.id; applyVisualPrefs(); showSettings()
            }
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            lp.rightMargin = Ui.dp(this, 6f)
            themeRow.addView(b, lp)
            if (themeRow.childCount % 3 == 0) { c.addView(themeRow); }
        }
        if (themeRow.parent == null) c.addView(themeRow)

        c.addView(Ui.spacer(this, 6f))
        c.addView(Ui.body(this, theme, getString(R.string.board_skin), 14f))
        val skinRow = Ui.row(this)
        for (sk in BoardSkins.ALL) {
            val b = Ui.button(this, theme, getString(sk.nameRes)) {
                prefsStore.boardSkin = sk.id; applyVisualPrefs(); showSettings()
            }
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            lp.rightMargin = Ui.dp(this, 6f)
            skinRow.addView(b, lp)
        }
        c.addView(skinRow)

        c.addView(Ui.spacer(this, 6f))
        c.addView(Ui.body(this, theme, getString(R.string.token_skin), 14f))
        val tokRow = Ui.row(this)
        for (tk in TokenSkins.ALL) {
            val b = Ui.button(this, theme, getString(tk.nameRes)) {
                prefsStore.tokenSkin = tk.id; applyVisualPrefs(); showSettings()
            }
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            lp.rightMargin = Ui.dp(this, 6f)
            tokRow.addView(b, lp)
        }
        c.addView(tokRow)

        c.addView(Ui.spacer(this, 6f))
        c.addView(Ui.body(this, theme, getString(R.string.language), 14f))
        val langRow = Ui.row(this)
        val langs = listOf("system" to R.string.lang_system, "en" to R.string.lang_en, "hi" to R.string.lang_hi)
        for ((id, res) in langs) {
            val b = Ui.button(this, theme, getString(res)) {
                prefsStore.language = id
                recreate()
            }
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            lp.rightMargin = Ui.dp(this, 6f)
            langRow.addView(b, lp)
        }
        c.addView(langRow)

        c.addView(Ui.divider(this, theme))
        c.addView(Ui.switchRow(this, theme, getString(R.string.assist), prefsStore.assist) {
            prefsStore.assist = it
            session?.engine?.dice?.assistEnabled = it
        })
        c.addView(Ui.switchRow(this, theme, getString(R.string.auto_roll), prefsStore.autoRoll) {
            prefsStore.autoRoll = it
        })
        c.addView(Ui.spacer(this, 10f))
        c.addView(Ui.button(this, theme, getString(R.string.back), primary = true) {
            audio.tap(); applyVisualPrefs(); showMenu()
        })
        openOverlay(scrollCard(c))
    }

    // ---------------------------------------------------------------- rules editor
    private fun showRules() {
        val theme = currentTheme()
        val c = card()
        header(c, getString(R.string.rules_title))
        var r = prefsStore.customRules

        fun save() {
            prefsStore.customRules = r
            prefsStore.presetId = "custom"
        }

        c.addView(Ui.switchRow(this, theme, getString(R.string.rule_blockades), r.blockadesEnabled) {
            r = r.copy(blockadesEnabled = it); save()
        })
        c.addView(Ui.switchRow(this, theme, getString(R.string.rule_block_pass), r.blockadesBlockPassing) {
            r = r.copy(blockadesBlockPassing = it); save()
        })
        c.addView(Ui.switchRow(this, theme, getString(R.string.rule_three_six), r.threeSixesForfeit) {
            r = r.copy(threeSixesForfeit = it); save()
        })
        c.addView(Ui.switchRow(this, theme, getString(R.string.rule_six_unlock), r.unlockRequiresSix) {
            r = r.copy(unlockRequiresSix = it); save()
        })
        c.addView(Ui.switchRow(this, theme, getString(R.string.rule_six_extra), r.sixGrantsExtraTurn) {
            r = r.copy(sixGrantsExtraTurn = it); save()
        })
        c.addView(Ui.switchRow(this, theme, getString(R.string.rule_capture_extra), r.captureGrantsExtraTurn) {
            r = r.copy(captureGrantsExtraTurn = it); save()
        })
        c.addView(Ui.switchRow(this, theme, getString(R.string.rule_home_extra), r.homeGrantsExtraTurn) {
            r = r.copy(homeGrantsExtraTurn = it); save()
        })
        c.addView(Ui.switchRow(this, theme, getString(R.string.rule_exact_home), r.exactRollToHome) {
            r = r.copy(exactRollToHome = it); save()
        })
        c.addView(Ui.switchRow(this, theme, getString(R.string.rule_safe_stars), r.safeStars) {
            r = r.copy(safeStars = it); save()
        })
        c.addView(Ui.switchRow(this, theme, getString(R.string.rule_safe_start), r.safeStartCells) {
            r = r.copy(safeStartCells = it); save()
        })
        c.addView(Ui.switchRow(this, theme, getString(R.string.rule_battle), r.battleMode) {
            r = r.copy(battleMode = it); save()
        })
        c.addView(Ui.sliderRow(this, theme, getString(R.string.rule_timer), r.turnTimerSeconds, 0, 60) {
            r = r.copy(turnTimerSeconds = it); save()
        })
        c.addView(Ui.spacer(this, 10f))
        c.addView(Ui.button(this, theme, getString(R.string.back), primary = true) {
            audio.tap(); showMenu()
        })
        openOverlay(scrollCard(c))
    }

    // ---------------------------------------------------------------- stats
    private fun showStats() {
        val theme = currentTheme()
        val c = card()
        header(c, getString(R.string.stats_title))
        val matches = prefsStore.matches
        val wins = prefsStore.wins
        fun statRow(label: String, value: String) {
            val row = Ui.row(this)
            row.setPadding(0, Ui.dp(this, 5f), 0, Ui.dp(this, 5f))
            row.addView(Ui.body(this, theme, label, 14f), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(Ui.value(this, theme, value))
            c.addView(row)
        }
        statRow(getString(R.string.stat_matches), matches.toString())
        statRow(getString(R.string.stat_wins), wins.toString())
        statRow(getString(R.string.stat_rate),
            if (matches > 0) "${wins * 100 / matches}%" else "—")
        statRow(getString(R.string.stat_captures), prefsStore.captures.toString())
        statRow(getString(R.string.stat_homes), prefsStore.homes.toString())
        statRow(getString(R.string.stat_streak), prefsStore.bestStreak.toString())
        if (matches == 0) c.addView(Ui.body(this, theme, getString(R.string.stat_empty), 13f, dim = true))
        c.addView(Ui.spacer(this, 10f))
        c.addView(Ui.button(this, theme, getString(R.string.stat_reset)) {
            prefsStore.resetStats()
            toast(getString(R.string.stat_reset_done))
            showStats()
        })
        c.addView(Ui.spacer(this, 8f))
        c.addView(Ui.button(this, theme, getString(R.string.back), primary = true) {
            audio.tap(); showMenu()
        })
        openOverlay(scrollCard(c))
    }

    // ---------------------------------------------------------------- AI profiles
    private fun showAiProfiles() {
        val theme = currentTheme()
        val c = card()
        header(c, getString(R.string.ai_title), getString(R.string.ai_offline))
        for (p in AiProfile.ALL) {
            val selected = prefsStore.lastBotProfile == p.id
            val b = Ui.button(this, theme, profileLabel(p) + if (selected) "  ✓" else "") {
                prefsStore.lastBotProfile = p.id
                audio.tap()
                showAiProfiles()
            }
            c.addView(b)
            c.addView(Ui.spacer(this, 4f))
            c.addView(Ui.body(this, theme, getString(
                when (p.id) {
                    "aggressive" -> R.string.ai_desc_aggressive
                    "defensive" -> R.string.ai_desc_defensive
                    "adaptive" -> R.string.ai_desc_adaptive
                    "grandmaster" -> R.string.ai_desc_grandmaster
                    "balanced" -> R.string.ai_desc_balanced
                    else -> R.string.ai_desc_tactical
                }), 12.5f, dim = true))
        }
        c.addView(Ui.spacer(this, 8f))
        c.addView(Ui.button(this, theme, getString(R.string.back), primary = true) {
            audio.tap(); showMenu()
        })
        openOverlay(scrollCard(c))
    }

    // ---------------------------------------------------------------- tutorial
    private fun showTutorial() {
        val theme = currentTheme()
        val c = card()
        header(c, getString(R.string.tut_title))
        val titles = intArrayOf(R.string.tut_1_title, R.string.tut_2_title, R.string.tut_3_title, R.string.tut_4_title)
        val bodies = intArrayOf(R.string.tut_1_body, R.string.tut_2_body, R.string.tut_3_body, R.string.tut_4_body)
        val page = tutorialPage.coerceIn(0, 3)
        c.addView(Ui.chip(this, theme, "${page + 1} / 4", theme.accent))
        c.addView(Ui.spacer(this, 8f))
        c.addView(Ui.title(this, theme, getString(titles[page]), 19f))
        c.addView(Ui.body(this, theme, getString(bodies[page]), 14f))
        c.addView(Ui.spacer(this, 10f))
        val row = Ui.row(this)
        row.addView(Ui.button(this, theme, getString(R.string.skip)) {
            audio.tap(); showMenu()
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(View(this).also { it.layoutParams = LinearLayout.LayoutParams(Ui.dp(this, 8f), 1) })
        row.addView(Ui.button(this, theme,
            if (page < 3) getString(R.string.next) else getString(R.string.done), primary = true) {
            audio.tap()
            if (page < 3) { tutorialPage = page + 1; showTutorial() } else showMenu()
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        c.addView(row)
        openOverlay(c)
    }

    // ---------------------------------------------------------------- about
    private fun showAbout() {
        val theme = currentTheme()
        val c = card()
        header(c, getString(R.string.about_title), getString(R.string.version_label))
        c.addView(Ui.body(this, theme, getString(R.string.about_body), 13.5f))
        c.addView(Ui.body(this, theme, getString(R.string.about_credits), 12f, dim = true))
        val crash = CrashLog.latest(this)
        if (crash != null) {
            c.addView(Ui.spacer(this, 6f))
            c.addView(Ui.body(this, theme, getString(R.string.crash_saved) + ": " + crash.name, 12f, dim = true))
        }
        c.addView(Ui.spacer(this, 10f))
        c.addView(Ui.button(this, theme, getString(R.string.back), primary = true) {
            audio.tap(); showMenu()
        })
        openOverlay(scrollCard(c))
    }

    // ---------------------------------------------------------------- log
    private fun showLog() {
        val theme = currentTheme()
        val c = card()
        header(c, getString(R.string.replay_log))
        val text = session?.logText().orEmpty()
        if (text.isEmpty()) {
            c.addView(Ui.body(this, theme, getString(R.string.stat_empty), 13f, dim = true))
        } else {
            c.addView(Ui.body(this, theme, text, 11.5f, dim = false))
        }
        c.addView(Ui.spacer(this, 10f))
        c.addView(Ui.button(this, theme, getString(R.string.back), primary = true) {
            audio.tap()
            if (session != null && !session!!.matchFinished) showPauseMenu() else showMenu()
        })
        openOverlay(scrollCard(c))
    }

    // ---------------------------------------------------------------- match over
    fun onMatchFinished(s: Session, winner: Int) {
        val humanSeats = (0 until s.state.playerCount).filter { s.isHuman(it) || (s.state.isBot[it].not() && s.afk[it]) }
        val humanWon = humanSeats.contains(winner)
        prefsStore.matches = prefsStore.matches + 1
        if (humanWon) prefsStore.wins = prefsStore.wins + 1
        var caps = 0
        var homes = 0
        for (e in s.telemetry.entries()) {
            if (e.capturedCount > 0) caps += e.capturedCount
            if (e.kind == com.apexludo.core.TelemetryEntry.Kind.HOME) homes++
        }
        prefsStore.captures = prefsStore.captures + caps
        prefsStore.homes = prefsStore.homes + homes
        prefsStore.coins = prefsStore.coins + if (humanWon) 60 else 15

        val theme = currentTheme()
        val c = card()
        header(c, getString(R.string.match_over),
            getString(R.string.winner_is, s.playerName(winner)))
        val order = (0 until s.state.playerCount).sortedBy { s.state.rank[it] }
        for ((i, p) in order.withIndex()) {
            val row = Ui.row(this)
            row.setPadding(0, Ui.dp(this, 4f), 0, Ui.dp(this, 4f))
            row.addView(Ui.body(this, theme,
                when (i) { 0 -> getString(R.string.rank_first); 1 -> getString(R.string.rank_second)
                    2 -> getString(R.string.rank_third); else -> getString(R.string.rank_fourth) }, 14f),
                LinearLayout.LayoutParams(Ui.dp(this, 48f), ViewGroup.LayoutParams.WRAP_CONTENT))
            row.addView(Ui.body(this, theme, s.playerName(p), 14f),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(Ui.chip(this, theme, getString(R.string.stat_captures) + " " +
                s.telemetry.entries().count { it.player == p && it.capturedCount > 0 },
                theme.playerColors[s.state.colorOf(p)]))
            c.addView(row)
        }
        c.addView(Ui.spacer(this, 12f))
        c.addView(Ui.button(this, theme, getString(R.string.rematch), primary = true) {
            audio.tap()
            val seats = IntArray(s.state.playerCount) { if (s.state.isBot[it]) 1 else 0 }
            val profs = Array(s.state.playerCount) { AiProfile.byId(prefsStore.lastBotProfile) }
            closeOverlay()
            startMatch(seats, profs, s.rules)
        })
        c.addView(Ui.spacer(this, 8f))
        c.addView(Ui.button(this, theme, getString(R.string.home)) {
            audio.tap()
            session?.destroy()
            session = null
            game.session = null
            game.stopAnimations()
            closeOverlay()
            showMenu()
        })
        openOverlay(scrollCard(c), dismissible = false)
    }
}
