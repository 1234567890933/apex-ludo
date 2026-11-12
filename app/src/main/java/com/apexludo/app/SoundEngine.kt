package com.apexludo.app

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Build

/**
 * Four-bus audio engine:
 *   master -> { sfx, voice, bgm }
 * Implemented with SoundPool for zero-latency effects and a MediaPlayer loop for music.
 * All samples are generated in-house (see tools/gen_assets.py), so the APK ships no
 * third-party audio.
 */
class SoundEngine(ctx: Context) {

    private val app = ctx.applicationContext
    private var pool: SoundPool? = null
    private var music: MediaPlayer? = null

    private val ids = HashMap<String, Int>()
    private var loaded = false

    /** 0 = menu bed, 1 = in-match bed. */
    var trackIndex = 0
        private set
    private val currentTrack: Int
        get() = if (trackIndex == 1) R.raw.bgm_game else R.raw.bgm_menu

    fun switchTrack(index: Int) {
        if (index == trackIndex) return
        trackIndex = index
        try { music?.release() } catch (t: Throwable) { }
        music = null
        startMusic()
    }

    var masterOn = true
    var sfxVolume = 0.85f
    var musicVolume = 0.45f
    var musicOn = true

    // Static R.raw references (not getIdentifier) so R8/resource shrinking can never
    // strip the samples out of a release build.
    private val sfxRes = linkedMapOf(
        "sfx_tap" to R.raw.sfx_tap,
        "sfx_open" to R.raw.sfx_open,
        "sfx_close" to R.raw.sfx_close,
        "sfx_move" to R.raw.sfx_move,
        "sfx_dice_roll" to R.raw.sfx_dice_roll,
        "sfx_dice_land" to R.raw.sfx_dice_land,
        "sfx_capture" to R.raw.sfx_capture,
        "sfx_home" to R.raw.sfx_home,
        "sfx_tick" to R.raw.sfx_tick,
        "sfx_six" to R.raw.sfx_six,
        "sfx_win" to R.raw.sfx_win
    )

    fun init() {
        if (pool != null) return
        try {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            pool = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                SoundPool.Builder().setMaxStreams(8).setAudioAttributes(attrs).build()
            } else {
                @Suppress("DEPRECATION")
                SoundPool(8, AudioManager.STREAM_MUSIC, 0)
            }
            pool?.setOnLoadCompleteListener { _, _, _ -> loaded = true }
            for ((name, resId) in sfxRes) {
                val id = pool?.load(app, resId, 1) ?: 0
                if (id != 0) ids[name] = id
            }
        } catch (t: Throwable) {
            pool = null
        }
    }

    fun play(name: String, volume: Float = 1f, rate: Float = 1f) {
        if (!masterOn || !loaded) return
        val id = ids[name] ?: return
        try {
            pool?.play(id, (sfxVolume * volume).coerceIn(0f, 1f), (sfxVolume * volume).coerceIn(0f, 1f),
                1, 0, rate.coerceIn(0.5f, 2f))
        } catch (t: Throwable) {
            // ignore
        }
    }

    fun tap() = play("sfx_tap", 0.7f)
    fun open() = play("sfx_open", 0.7f)
    fun close() = play("sfx_close", 0.6f)
    fun move() = play("sfx_move", 0.55f, 0.95f + (Math.random().toFloat() * 0.12f))
    fun roll() = play("sfx_dice_roll", 0.9f)
    fun land() = play("sfx_dice_land", 0.9f)
    fun capture() = play("sfx_capture", 1f)
    fun home() = play("sfx_home", 0.9f)
    fun six() = play("sfx_six", 0.85f)
    fun tick() = play("sfx_tick", 0.5f)
    fun win() = play("sfx_win", 1f)

    fun startMusic() {
        if (!musicOn || !masterOn) return
        try {
            if (music == null) {
                val resId = currentTrack
                music = MediaPlayer.create(app, resId)?.apply {
                    isLooping = true
                    setVolume(musicVolume, musicVolume)
                }
            }
            music?.let { if (!it.isPlaying) it.start() }
        } catch (t: Throwable) {
            music = null
        }
    }

    fun pauseMusic() {
        try { music?.let { if (it.isPlaying) it.pause() } } catch (t: Throwable) { }
    }

    fun updateMusicVolume(v: Float) {
        musicVolume = v.coerceIn(0f, 1f)
        try { music?.setVolume(musicVolume, musicVolume) } catch (t: Throwable) { }
    }

    fun release() {
        try { music?.release() } catch (t: Throwable) { }
        music = null
        try { pool?.release() } catch (t: Throwable) { }
        pool = null
        ids.clear()
        loaded = false
    }
}
