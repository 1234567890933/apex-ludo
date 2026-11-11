package com.apexludo.app

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.cos
import kotlin.math.sin

/**
 * "Powder Reincarnation" particle system plus shockwave ripples and victory confetti.
 * Everything is pre-allocated into a fixed pool: no allocation happens while drawing,
 * which is what keeps the frame budget stable during capture storms.
 */
class ParticleSystem(private val max: Int = 720) {

    private val px = FloatArray(max)
    private val py = FloatArray(max)
    private val vx = FloatArray(max)
    private val vy = FloatArray(max)
    private val life = FloatArray(max)
    private val age = FloatArray(max)
    private val size = FloatArray(max)
    private val color = IntArray(max)
    private val kind = IntArray(max)   // 0 = powder, 1 = confetti
    private val alive = BooleanArray(max)
    private var cursor = 0

    // shockwaves
    private val swX = FloatArray(24)
    private val swY = FloatArray(24)
    private val swAge = FloatArray(24)
    private val swDur = FloatArray(24)
    private val swRadius = FloatArray(24)
    private val swColor = IntArray(24)
    private val swAlive = BooleanArray(24)
    private var swCursor = 0

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun clear() {
        for (i in 0 until max) alive[i] = false
        for (i in 0 until swAlive.size) swAlive[i] = false
    }

    fun activeCount(): Int {
        var n = 0
        for (i in 0 until max) if (alive[i]) n++
        return n
    }

    fun emitPowder(x: Float, y: Float, c: Int, count: Int, spread: Float, speed: Float = 190f) {
        var n = count
        var guard = 0
        while (n > 0 && guard < max * 2) {
            guard++
            val i = cursor
            cursor = (cursor + 1) % max
            if (alive[i]) continue
            val a = Math.random().toFloat() * 6.2831853f
            val s = speed * (0.35f + Math.random().toFloat() * 0.9f)
            px[i] = x + (Math.random().toFloat() - 0.5f) * spread
            py[i] = y + (Math.random().toFloat() - 0.5f) * spread
            vx[i] = cos(a) * s * 0.45f
            vy[i] = sin(a) * s * 0.45f - speed * 0.5f
            life[i] = 0.55f + Math.random().toFloat() * 0.75f
            age[i] = 0f
            size[i] = 1.6f + Math.random().toFloat() * 3.4f
            color[i] = c
            kind[i] = 0
            alive[i] = true
            n--
        }
    }

    fun emitConfetti(w: Float, h: Float, colors: IntArray, count: Int) {
        var n = count
        var guard = 0
        while (n > 0 && guard < max * 2) {
            guard++
            val i = cursor
            cursor = (cursor + 1) % max
            if (alive[i]) continue
            px[i] = Math.random().toFloat() * w
            py[i] = -20f - Math.random().toFloat() * h * 0.3f
            vx[i] = (Math.random().toFloat() - 0.5f) * 120f
            vy[i] = 120f + Math.random().toFloat() * 260f
            life[i] = 2.4f + Math.random().toFloat() * 1.8f
            age[i] = 0f
            size[i] = 3f + Math.random().toFloat() * 4f
            color[i] = colors[(Math.random() * colors.size).toInt().coerceIn(0, colors.size - 1)]
            kind[i] = 1
            alive[i] = true
            n--
        }
    }

    fun shockwave(x: Float, y: Float, c: Int, radius: Float, dur: Float = 0.5f) {
        val i = swCursor
        swCursor = (swCursor + 1) % swAlive.size
        swX[i] = x; swY[i] = y; swAge[i] = 0f; swDur[i] = dur
        swRadius[i] = radius; swColor[i] = c; swAlive[i] = true
    }

    fun update(dt: Float) {
        if (dt <= 0f) return
        val d = dt.coerceAtMost(0.05f)
        for (i in 0 until max) {
            if (!alive[i]) continue
            age[i] += d
            if (age[i] >= life[i]) { alive[i] = false; continue }
            px[i] += vx[i] * d
            py[i] += vy[i] * d
            if (kind[i] == 0) {
                vy[i] += 260f * d          // gravity
                vx[i] *= (1f - 1.6f * d)   // drag
                vy[i] *= (1f - 0.9f * d)
                // turbulence -> smoke-like curl
                vx[i] += sin(py[i] * 0.05f + age[i] * 6f) * 22f * d
            } else {
                vy[i] += 180f * d
                vx[i] += sin(age[i] * 3f + i) * 30f * d
            }
        }
        for (i in 0 until swAlive.size) {
            if (!swAlive[i]) continue
            swAge[i] += d
            if (swAge[i] >= swDur[i]) swAlive[i] = false
        }
    }

    fun draw(canvas: Canvas) {
        // shockwaves first (behind powder)
        for (i in 0 until swAlive.size) {
            if (!swAlive[i]) continue
            val t = (swAge[i] / swDur[i]).coerceIn(0f, 1f)
            val r = swRadius[i] * (0.25f + t * 0.95f)
            val alpha = ((1f - t) * 190).toInt().coerceIn(0, 255)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = (6f * (1f - t) + 1.5f)
            paint.color = Color.argb(alpha, Color.red(swColor[i]), Color.green(swColor[i]), Color.blue(swColor[i]))
            canvas.drawCircle(swX[i], swY[i], r, paint)
            paint.strokeWidth = 1.5f
            paint.color = Color.argb((alpha * 0.5f).toInt(), Color.red(swColor[i]), Color.green(swColor[i]), Color.blue(swColor[i]))
            canvas.drawCircle(swX[i], swY[i], r * 0.72f, paint)
        }
        paint.style = Paint.Style.FILL
        for (i in 0 until max) {
            if (!alive[i]) continue
            val t = (age[i] / life[i]).coerceIn(0f, 1f)
            val alpha = ((1f - t * t) * 235).toInt().coerceIn(0, 255)
            paint.color = Color.argb(alpha, Color.red(color[i]), Color.green(color[i]), Color.blue(color[i]))
            val r = if (kind[i] == 0) size[i] * (1f - t * 0.45f) else size[i]
            canvas.drawCircle(px[i], py[i], r, paint)
        }
    }
}
