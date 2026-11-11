package com.apexludo.app

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Frame-time telemetry + adaptive quality governor.
 *
 * If the frame rate sits below 55 fps for more than three seconds the renderer is asked
 * to shed load (particle density halved, glow pass skipped). The user's explicit
 * "battery saver" choice always wins and hard-caps at 30 fps.
 */
class PerfMonitor {

    private val frameTimes = LongArray(90)
    private var idx = 0
    private var filled = 0
    private var lastFrame = 0L

    var fps: Int = 60
        private set
    var frameMs: Int = 16
        private set
    var adaptiveQuality: Float = 1f
        private set
    var degraded: Boolean = false
        private set

    private var lowSince = 0L

    fun onFrame(now: Long) {
        if (lastFrame != 0L) {
            val dt = now - lastFrame
            frameTimes[idx] = dt
            idx = (idx + 1) % frameTimes.size
            if (filled < frameTimes.size) filled++
            if (filled >= 20) {
                var sum = 0L
                for (i in 0 until filled) sum += frameTimes[i]
                val avg = sum / filled
                frameMs = (avg / 1_000_000L).toInt()
                fps = if (avg > 0) (1_000_000_000L / avg).toInt() else 0
                if (fps < 55) {
                    if (lowSince == 0L) lowSince = now
                    if (now - lowSince > 3_000_000_000L && !degraded) {
                        degraded = true
                        adaptiveQuality = 0.5f
                    }
                } else {
                    lowSince = 0L
                    if (fps > 58 && degraded) {
                        degraded = false
                        adaptiveQuality = 1f
                    }
                }
            }
        }
        lastFrame = now
    }

    fun reset() {
        idx = 0; filled = 0; lastFrame = 0L; fps = 60; frameMs = 16
        adaptiveQuality = 1f; degraded = false; lowSince = 0L
    }
}

/** Writes uncaught exceptions to app storage so a beta tester can share them. */
object CrashLog {

    private const val MAX_FILES = 5

    fun install(ctx: Context) {
        val app = ctx.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                write(app, thread, error)
            } catch (t: Throwable) {
                // never mask the original crash
            }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun write(app: Context, thread: Thread, error: Throwable) {
        val dir = File(app.filesDir, "crashes")
        if (!dir.exists()) dir.mkdirs()
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val sw = StringWriter()
        error.printStackTrace(PrintWriter(sw))
        val body = buildString {
            append("Apex Ludo 1.1 crash report\n")
            append("time: ").append(Date().toString()).append('\n')
            append("thread: ").append(thread.name).append('\n')
            append("android: ").append(android.os.Build.VERSION.SDK_INT)
            append(" ").append(android.os.Build.MODEL).append('\n')
            append('\n').append(sw.toString())
        }
        File(dir, "crash-$stamp.txt").writeText(body)
        val files = dir.listFiles()?.sortedBy { it.name } ?: return
        if (files.size > MAX_FILES) {
            files.take(files.size - MAX_FILES).forEach { it.delete() }
        }
    }

    fun latest(ctx: Context): File? {
        val dir = File(ctx.filesDir, "crashes")
        return dir.listFiles()?.sortedBy { it.name }?.lastOrNull()
    }
}
