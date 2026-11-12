package com.apexludo.app

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/**
 * A complete hand-drawn icon set. Every glyph is built from primitives on the canvas,
 * so it stays razor sharp at any density and costs zero bytes in the APK.
 * Coordinates are normalised to a -1..1 box around (cx, cy).
 */
object Icons {

    const val PLAY = 0
    const val USERS = 1
    const val SLIDERS = 2
    const val ROBOT = 3
    const val CHART = 4
    const val GEAR = 5
    const val BOOK = 6
    const val PALETTE = 7
    const val DICE = 8
    const val TROPHY = 9
    const val COIN = 10
    const val SHOP = 11
    const val EYE = 12
    const val CHAT = 13
    const val BACK = 14
    const val CLOSE = 15
    const val CHECK = 16
    const val STAR = 17
    const val SHIELD = 18
    const val CROWN = 19
    const val ROCKET = 20
    const val CAT = 21
    const val PAUSE = 22

    private val rect = RectF()

    fun draw(
        canvas: Canvas,
        kind: Int,
        cx: Float,
        cy: Float,
        size: Float,
        color: Int,
        fill: Paint,
        stroke: Paint,
        path: Path
    ) {
        fill.color = color
        fill.style = Paint.Style.FILL
        stroke.color = color
        stroke.style = Paint.Style.STROKE
        stroke.strokeWidth = size * 0.13f
        stroke.strokeCap = Paint.Cap.ROUND
        stroke.strokeJoin = Paint.Join.ROUND
        val s = size * 0.5f
        fun x(v: Float) = cx + v * s
        fun y(v: Float) = cy + v * s

        when (kind) {
            PLAY -> {
                path.reset()
                path.moveTo(x(-0.45f), y(-0.72f))
                path.lineTo(x(0.75f), y(0f))
                path.lineTo(x(-0.45f), y(0.72f))
                path.close()
                canvas.drawPath(path, fill)
            }
            USERS -> {
                canvas.drawCircle(x(-0.30f), y(-0.32f), s * 0.34f, fill)
                canvas.drawCircle(x(0.42f), y(-0.22f), s * 0.26f, fill)
                rect.set(x(-0.95f), y(0.10f), x(0.32f), y(0.95f))
                canvas.drawArc(rect, 180f, 180f, true, fill)
                rect.set(x(0.12f), y(0.24f), x(0.95f), y(0.92f))
                canvas.drawArc(rect, 180f, 180f, true, fill)
            }
            SLIDERS -> {
                for (i in 0 until 3) {
                    val yy = y(-0.55f + i * 0.55f)
                    canvas.drawLine(x(-0.85f), yy, x(0.85f), yy, stroke)
                    val knob = when (i) { 0 -> -0.35f; 1 -> 0.30f; else -> -0.05f }
                    canvas.drawCircle(x(knob), yy, s * 0.19f, fill)
                }
            }
            ROBOT -> {
                rect.set(x(-0.62f), y(-0.48f), x(0.62f), y(0.55f))
                canvas.drawRoundRect(rect, s * 0.22f, s * 0.22f, fill)
                fill.color = 0xFF0B0E17.toInt()
                canvas.drawCircle(x(-0.26f), y(-0.10f), s * 0.13f, fill)
                canvas.drawCircle(x(0.26f), y(-0.10f), s * 0.13f, fill)
                rect.set(x(-0.26f), y(0.18f), x(0.26f), y(0.32f))
                canvas.drawRoundRect(rect, s * 0.07f, s * 0.07f, fill)
                fill.color = color
                canvas.drawLine(x(0f), y(-0.48f), x(0f), y(-0.75f), stroke)
                canvas.drawCircle(x(0f), y(-0.80f), s * 0.10f, fill)
            }
            CHART -> {
                val heights = floatArrayOf(0.35f, 0.72f, 0.52f, 0.90f)
                for (i in heights.indices) {
                    val left = x(-0.80f + i * 0.44f)
                    rect.set(left, y(0.80f - heights[i]), left + s * 0.30f, y(0.80f))
                    canvas.drawRoundRect(rect, s * 0.07f, s * 0.07f, fill)
                }
                canvas.drawLine(x(-0.88f), y(0.86f), x(0.88f), y(0.86f), stroke)
            }
            GEAR -> {
                val teeth = 8
                path.reset()
                for (i in 0 until teeth * 2) {
                    val a = (i * Math.PI / teeth).toFloat()
                    val r = if (i % 2 == 0) 0.88f else 0.62f
                    val px = x(Math.cos(a.toDouble()).toFloat() * r)
                    val py = y(Math.sin(a.toDouble()).toFloat() * r)
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close()
                canvas.drawPath(path, fill)
                fill.color = 0xFF0B0E17.toInt()
                canvas.drawCircle(cx, cy, s * 0.26f, fill)
                fill.color = color
            }
            BOOK -> {
                rect.set(x(-0.78f), y(-0.70f), x(-0.02f), y(0.70f))
                canvas.drawRoundRect(rect, s * 0.08f, s * 0.08f, fill)
                rect.set(x(0.02f), y(-0.70f), x(0.78f), y(0.70f))
                canvas.drawRoundRect(rect, s * 0.08f, s * 0.08f, fill)
                canvas.drawLine(cx, y(-0.66f), cx, y(0.66f), stroke)
            }
            PALETTE -> {
                canvas.drawCircle(cx, cy, s * 0.82f, fill)
                fill.color = 0xFF0B0E17.toInt()
                canvas.drawCircle(x(0.30f), y(0.42f), s * 0.20f, fill)
                fill.color = color
                val dots = floatArrayOf(-0.34f, -0.30f, 0.02f, -0.44f, 0.34f, -0.24f)
                var i = 0
                while (i < dots.size) {
                    fill.color = 0xFF0B0E17.toInt()
                    canvas.drawCircle(x(dots[i]), y(dots[i + 1]), s * 0.13f, fill)
                    i += 2
                }
                fill.color = color
            }
            DICE -> {
                rect.set(x(-0.72f), y(-0.72f), x(0.72f), y(0.72f))
                canvas.drawRoundRect(rect, s * 0.22f, s * 0.22f, fill)
                fill.color = 0xFF0B0E17.toInt()
                for ((px, py) in listOf(-0.34f to -0.34f, 0.34f to -0.34f, 0f to 0f,
                    0.34f to 0.34f, -0.34f to 0.34f)) {
                    canvas.drawCircle(x(px), y(py), s * 0.11f, fill)
                }
                fill.color = color
            }
            TROPHY -> {
                path.reset()
                path.moveTo(x(-0.52f), y(-0.68f))
                path.lineTo(x(0.52f), y(-0.68f))
                path.lineTo(x(0.34f), y(0.10f))
                path.lineTo(x(-0.34f), y(0.10f))
                path.close()
                canvas.drawPath(path, fill)
                rect.set(x(-0.95f), y(-0.62f), x(-0.30f), y(0.02f))
                canvas.drawArc(rect, 90f, 180f, false, stroke)
                rect.set(x(0.30f), y(-0.62f), x(0.95f), y(0.02f))
                canvas.drawArc(rect, 270f, 180f, false, stroke)
                canvas.drawLine(x(0f), y(0.10f), x(0f), y(0.48f), stroke)
                rect.set(x(-0.42f), y(0.48f), x(0.42f), y(0.78f))
                canvas.drawRoundRect(rect, s * 0.08f, s * 0.08f, fill)
            }
            COIN -> {
                canvas.drawCircle(cx, cy, s * 0.80f, fill)
                fill.color = 0xFF0B0E17.toInt()
                canvas.drawCircle(cx, cy, s * 0.52f, fill)
                fill.color = color
                canvas.drawCircle(cx, cy, s * 0.34f, fill)
            }
            SHOP -> {
                rect.set(x(-0.85f), y(-0.18f), x(0.85f), y(0.85f))
                canvas.drawRoundRect(rect, s * 0.10f, s * 0.10f, fill)
                path.reset()
                path.moveTo(x(-0.85f), y(-0.18f))
                path.lineTo(x(-0.55f), y(-0.72f))
                path.lineTo(x(0.55f), y(-0.72f))
                path.lineTo(x(0.85f), y(-0.18f))
                path.close()
                canvas.drawPath(path, fill)
                fill.color = 0xFF0B0E17.toInt()
                rect.set(x(-0.18f), y(0.18f), x(0.18f), y(0.85f))
                canvas.drawRoundRect(rect, s * 0.06f, s * 0.06f, fill)
                fill.color = color
            }
            EYE -> {
                path.reset()
                path.moveTo(x(-0.92f), y(0f))
                path.quadTo(x(0f), y(-0.82f), x(0.92f), y(0f))
                path.quadTo(x(0f), y(0.82f), x(-0.92f), y(0f))
                path.close()
                canvas.drawPath(path, stroke)
                canvas.drawCircle(cx, cy, s * 0.26f, fill)
            }
            CHAT -> {
                rect.set(x(-0.85f), y(-0.72f), x(0.85f), y(0.38f))
                canvas.drawRoundRect(rect, s * 0.26f, s * 0.26f, fill)
                path.reset()
                path.moveTo(x(-0.30f), y(0.34f))
                path.lineTo(x(-0.30f), y(0.80f))
                path.lineTo(x(0.16f), y(0.34f))
                path.close()
                canvas.drawPath(path, fill)
                fill.color = 0xFF0B0E17.toInt()
                for (i in -1..1) canvas.drawCircle(x(i * 0.36f), y(-0.17f), s * 0.10f, fill)
                fill.color = color
            }
            BACK -> {
                canvas.drawLine(x(0.62f), y(0f), x(-0.55f), y(0f), stroke)
                canvas.drawLine(x(-0.55f), y(0f), x(-0.02f), y(-0.52f), stroke)
                canvas.drawLine(x(-0.55f), y(0f), x(-0.02f), y(0.52f), stroke)
            }
            CLOSE -> {
                canvas.drawLine(x(-0.55f), y(-0.55f), x(0.55f), y(0.55f), stroke)
                canvas.drawLine(x(0.55f), y(-0.55f), x(-0.55f), y(0.55f), stroke)
            }
            CHECK -> {
                canvas.drawLine(x(-0.62f), y(0.02f), x(-0.14f), y(0.52f), stroke)
                canvas.drawLine(x(-0.14f), y(0.52f), x(0.66f), y(-0.55f), stroke)
            }
            STAR -> {
                path.reset()
                for (i in 0 until 10) {
                    val a = (-Math.PI / 2 + i * Math.PI / 5).toFloat()
                    val r = if (i % 2 == 0) 0.88f else 0.38f
                    val px = x(Math.cos(a.toDouble()).toFloat() * r)
                    val py = y(Math.sin(a.toDouble()).toFloat() * r)
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close()
                canvas.drawPath(path, fill)
            }
            SHIELD -> {
                path.reset()
                path.moveTo(cx, y(-0.88f))
                path.lineTo(x(0.72f), y(-0.55f))
                path.lineTo(x(0.72f), y(0.10f))
                path.quadTo(x(0.72f), y(0.62f), cx, y(0.90f))
                path.quadTo(x(-0.72f), y(0.62f), x(-0.72f), y(0.10f))
                path.lineTo(x(-0.72f), y(-0.55f))
                path.close()
                canvas.drawPath(path, fill)
            }
            CROWN -> {
                path.reset()
                path.moveTo(x(-0.85f), y(0.62f))
                path.lineTo(x(-0.85f), y(-0.30f))
                path.lineTo(x(-0.42f), y(0.10f))
                path.lineTo(x(0f), y(-0.62f))
                path.lineTo(x(0.42f), y(0.10f))
                path.lineTo(x(0.85f), y(-0.30f))
                path.lineTo(x(0.85f), y(0.62f))
                path.close()
                canvas.drawPath(path, fill)
            }
            ROCKET -> {
                path.reset()
                path.moveTo(cx, y(-0.90f))
                path.quadTo(x(0.52f), y(-0.10f), x(0.34f), y(0.55f))
                path.lineTo(x(-0.34f), y(0.55f))
                path.quadTo(x(-0.52f), y(-0.10f), cx, y(-0.90f))
                path.close()
                canvas.drawPath(path, fill)
                fill.color = 0xFF0B0E17.toInt()
                canvas.drawCircle(cx, y(-0.12f), s * 0.17f, fill)
                fill.color = color
                path.reset()
                path.moveTo(x(-0.30f), y(0.55f))
                path.lineTo(x(-0.62f), y(0.88f))
                path.lineTo(x(-0.10f), y(0.72f))
                path.close()
                canvas.drawPath(path, fill)
                path.reset()
                path.moveTo(x(0.30f), y(0.55f))
                path.lineTo(x(0.62f), y(0.88f))
                path.lineTo(x(0.10f), y(0.72f))
                path.close()
                canvas.drawPath(path, fill)
            }
            CAT -> {
                path.reset()
                path.moveTo(x(-0.70f), y(-0.10f))
                path.lineTo(x(-0.62f), y(-0.82f))
                path.lineTo(x(-0.12f), y(-0.42f))
                path.lineTo(x(0.12f), y(-0.42f))
                path.lineTo(x(0.62f), y(-0.82f))
                path.lineTo(x(0.70f), y(-0.10f))
                path.quadTo(x(0.62f), y(0.78f), cx, y(0.82f))
                path.quadTo(x(-0.62f), y(0.78f), x(-0.70f), y(-0.10f))
                path.close()
                canvas.drawPath(path, fill)
                fill.color = 0xFF0B0E17.toInt()
                canvas.drawCircle(x(-0.26f), y(-0.02f), s * 0.11f, fill)
                canvas.drawCircle(x(0.26f), y(-0.02f), s * 0.11f, fill)
                fill.color = color
            }
            PAUSE -> {
                rect.set(x(-0.52f), y(-0.62f), x(-0.14f), y(0.62f))
                canvas.drawRoundRect(rect, s * 0.08f, s * 0.08f, fill)
                rect.set(x(0.14f), y(-0.62f), x(0.52f), y(0.62f))
                canvas.drawRoundRect(rect, s * 0.08f, s * 0.08f, fill)
            }
        }
        fill.style = Paint.Style.FILL
    }
}

/** Player avatar: a coloured disc with a glyph, plus a cosmetic title. */
data class Avatar(val id: String, val nameRes: Int, val color: Int, val icon: Int)

object Avatars {
    val ALL = listOf(
        Avatar("crown", R.string.av_crown, 0xFFE2B85C.toInt(), Icons.CROWN),
        Avatar("rocket", R.string.av_rocket, 0xFF3E7BEA.toInt(), Icons.ROCKET),
        Avatar("cat", R.string.av_cat, 0xFFE8434F.toInt(), Icons.CAT),
        Avatar("robot", R.string.av_robot, 0xFF35C07A.toInt(), Icons.ROBOT),
        Avatar("star", R.string.av_star, 0xFFF2B233.toInt(), Icons.STAR),
        Avatar("shield", R.string.av_shield, 0xFF3FE0C8.toInt(), Icons.SHIELD)
    )

    fun byId(id: String?): Avatar = ALL.firstOrNull { it.id == id } ?: ALL[0]
}

object Titles {
    val ALL = listOf(
        R.string.title_rookie, R.string.title_captain, R.string.title_sniper,
        R.string.title_blocker, R.string.title_lucky, R.string.title_apex
    )
}
