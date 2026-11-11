package com.apexludo.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import com.apexludo.core.Board
import com.apexludo.core.GameState
import com.apexludo.core.Move
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The whole game surface is drawn by hand on a single hardware-accelerated Canvas:
 * board, tokens, dice, powder particles, shockwaves, confetti, HUD and ticker.
 * No view hierarchy per cell, no allocation inside onDraw -> stable 60 fps.
 */
class GameView(ctx: Context) : View(ctx) {

    var session: Session? = null
        set(v) { field = v; invalidate() }

    var theme: Theme = Themes.NIGHT
        set(v) { field = v; invalidate() }
    var boardSkin: BoardSkin = BoardSkins.CLASSIC
        set(v) { field = v; invalidate() }
    var tokenSkin: TokenSkin = TokenSkins.PAWN
        set(v) { field = v; invalidate() }

    var particleDensity: Int = 1
    var animScale: Float = 1f
    var batterySaver: Boolean = false
    var showPerf: Boolean = false
    var perf: PerfMonitor = PerfMonitor()

    val particles = ParticleSystem()

    // ---------------------------------------------------------------- geometry
    private var boardLeft = 0f
    private var boardTop = 0f
    private var boardSize = 0f
    private var cellSize = 0f
    private val cellRects = Array(225) { RectF() }
    private val diceRect = RectF()
    private val pauseRect = RectF()
    private var topBarH = 0f
    private var bottomBarH = 0f

    // ---------------------------------------------------------------- paints
    private val pFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val pText = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pShadow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val shaderCache = HashMap<Long, Shader>()
    private val localMatrix = android.graphics.Matrix()
    private val bgPaint = Paint()

    private val tfMedium: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val tfBold: Typeface = Typeface.create("sans-serif", Typeface.BOLD)

    // ---------------------------------------------------------------- animation state
    private class TokenAnim(
        val token: Int,
        val pts: FloatArray,          // x0,y0,x1,y1,...
        var t: Float,
        val dur: Float,
        val onDone: () -> Unit
    )

    private class DiceAnim(var t: Float, val dur: Float, val result: Int, val onDone: () -> Unit)

    private var tokenAnim: TokenAnim? = null
    private var diceAnim: DiceAnim? = null
    private var diceFace = 6
    private var diceSquash = 0f
    private var confettiUntil = 0f
    private var shakeAmp = 0f
    private var shakeEnd = 0f
    private var lastFrameNs = 0L
    private var clock = 0f
    private var tickerScroll = 0f

    var highlighted: List<Int> = emptyList()
        set(v) { field = v; invalidate() }

    /** Tokens currently hidden because their powder animation is still running. */
    private val hidden = HashSet<Int>()

    private var onPauseTap: (() -> Unit)? = null
    private var onDiceTap: (() -> Unit)? = null
    private var onTokenTap: ((Int) -> Unit)? = null

    fun bindInput(
        dice: () -> Unit,
        pause: () -> Unit,
        token: (Int) -> Unit
    ) {
        onDiceTap = dice
        onPauseTap = pause
        onTokenTap = token
    }

    // ---------------------------------------------------------------- lifecycle
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutBoard(w.toFloat(), h.toFloat())
    }

    private fun layoutBoard(w: Float, h: Float) {
        val pad = Ui.dpf(context, 10f)
        topBarH = Ui.dpf(context, 54f)
        bottomBarH = Ui.dpf(context, 104f)
        val availW = w - pad * 2
        val availH = h - topBarH - bottomBarH - pad
        boardSize = min(availW, availH)
        boardLeft = (w - boardSize) / 2f
        boardTop = topBarH + (availH - boardSize) / 2f + pad * 0.5f
        cellSize = boardSize / Board.GRID
        for (r in 0 until Board.GRID) {
            for (c in 0 until Board.GRID) {
                val rf = cellRects[r * Board.GRID + c]
                rf.set(
                    boardLeft + c * cellSize,
                    boardTop + r * cellSize,
                    boardLeft + (c + 1) * cellSize,
                    boardTop + (r + 1) * cellSize
                )
            }
        }
        // dice sits bottom-right, pause bottom-left of the bottom bar
        val dSize = min(bottomBarH * 0.78f, Ui.dpf(context, 76f))
        val by = h - bottomBarH / 2f
        diceRect.set(w - pad - dSize, by - dSize / 2f, w - pad, by + dSize / 2f)
        val ps = Ui.dpf(context, 40f)
        pauseRect.set(pad, by - ps / 2f, pad + ps, by + ps / 2f)
    }

    private fun cx(col: Float): Float = boardLeft + (col + 0.5f) * cellSize
    private fun cy(row: Float): Float = boardTop + (row + 0.5f) * cellSize

    // ---------------------------------------------------------------- animation API
    fun animateMove(move: Move, victims: List<Int>, onDone: () -> Unit) {
        val s = session ?: run { onDone(); return }
        val color = s.state.colorOf(move.player)
        val pts = ArrayList<Float>(64)
        if (move.from == 0) {
            val (r, c) = Board.yardSlot(color, move.token % 4)
            pts.add(cx(c)); pts.add(cy(r))
            val cell = Board.cellOf(color, move.to) ?: Board.ringCell(Board.START_INDEX[color])
            pts.add(cx(cell.col.toFloat())); pts.add(cy(cell.row.toFloat()))
        } else {
            for (p in move.from..move.to) {
                val cell = Board.cellOf(color, p) ?: continue
                pts.add(cx(cell.col.toFloat())); pts.add(cy(cell.row.toFloat()))
            }
            if (pts.isEmpty()) {
                val (r, c) = Board.yardSlot(color, move.token % 4)
                pts.add(cx(c)); pts.add(cy(r))
            }
        }
        val steps = max(1, pts.size / 2 - 1)
        val dur = (0.075f * steps + 0.08f) / animScale.coerceAtLeast(0.4f)
        tokenAnim = TokenAnim(move.token, pts.toFloatArray(), 0f, dur, onDone)

        // Captured tokens: hide now, powder-burst when the attacker lands.
        for (v in victims) hidden.add(v)
        if (victims.isNotEmpty()) {
            postDelayed({
                val st = session ?: return@postDelayed
                for (v in victims) {
                    val vc = st.state.colorOf(v / Board.TOKENS_PER_PLAYER)
                    val (vr, vcol) = Board.yardSlot(vc, v % 4)
                    val tx = cx(vcol); val ty = cy(vr)
                    val cell = Board.cellOf(vc, 1)
                    val fromX = if (cell != null) cx(cell.col.toFloat()) else tx
                    val fromY = if (cell != null) cy(cell.row.toFloat()) else ty
                    burstPowder(fromX, fromY, tx, ty, st.themeColor(vc))
                    hidden.remove(v)
                }
            }, (dur * 620).toLong())
        }
        invalidate()
    }

    fun playDice(result: Int, onDone: () -> Unit) {
        diceFace = result
        diceAnim = DiceAnim(0f, 0.62f / animScale.coerceAtLeast(0.4f), result, onDone)
        invalidate()
    }

    fun burstPowder(x0: Float, y0: Float, x1: Float, y1: Float, color: Int) {
        val base = when (particleDensity) { 0 -> 10; 2 -> 30; else -> 20 }
        val n = (base * perf.adaptiveQuality).toInt().coerceAtLeast(4)
        val steps = 14
        for (i in 0 until steps) {
            val t = i / steps.toFloat()
            val arc = sin(t * PI.toFloat()) * (abs(x1 - x0) + abs(y1 - y0)) * 0.22f
            val x = x0 + (x1 - x0) * t
            val y = y0 + (y1 - y0) * t - arc
            particles.emitPowder(x, y, color, max(1, n / steps), cellSize * 0.16f, 90f)
        }
        particles.shockwave(x1, y1, color, cellSize * 1.5f, 0.55f)
        shakeAmp = cellSize * 0.22f
        shakeEnd = clock + 0.22f
    }

    fun celebrate(colors: IntArray) {
        particles.emitConfetti(width.toFloat(), height.toFloat(), colors, 120)
        confettiUntil = clock + 2.6f
    }

    fun pulseAt(x: Float, y: Float, color: Int) {
        particles.shockwave(x, y, color, cellSize * 1.1f, 0.45f)
    }

    fun stopAnimations() {
        tokenAnim = null
        diceAnim = null
        hidden.clear()
        particles.clear()
    }

    // ---------------------------------------------------------------- frame
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (boardSize <= 0f) {
            layoutBoard(width.toFloat(), height.toFloat())
            if (boardSize <= 0f) return
        }
        val nowNs = System.nanoTime()
        var dt = if (lastFrameNs == 0L) 0.016f else (nowNs - lastFrameNs) / 1e9f
        lastFrameNs = nowNs
        dt = dt.coerceIn(0f, 0.05f)

        perf.onFrame(nowNs)
        clock += dt
        tickerScroll += dt * 46f

        val s = session
        val st = s?.state

        // animations
        tokenAnim?.let { a ->
            a.t += dt / a.dur
            if (a.t >= 1f) {
                a.t = 1f
                tokenAnim = null
                a.onDone()
            }
        }
        diceAnim?.let { d ->
            d.t += dt / d.dur
            if (d.t >= 1f) {
                d.t = 1f
                diceAnim = null
                diceSquash = 0f
                d.onDone()
            } else {
                diceSquash = sin(d.t * PI.toFloat()) * 0.35f
            }
        }
        particles.update(dt)
        if (confettiUntil > clock && (Math.random() < 0.25)) {
            particles.emitConfetti(width.toFloat(), height.toFloat(),
                intArrayOf(theme.accent, theme.playerColors[0], theme.playerColors[1], theme.playerColors[2]), 8)
        }

        // background
        bgPaint.shader = LinearGradient(
            0f, 0f, 0f, height.toFloat(), theme.bgTop, theme.bgBottom, Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        bgPaint.shader = null

        // camera shake
        var shakeX = 0f
        var shakeY = 0f
        if (shakeEnd > clock) {
            val k = (shakeEnd - clock) / 0.22f
            shakeX = (Math.random().toFloat() - 0.5f) * shakeAmp * k
            shakeY = (Math.random().toFloat() - 0.5f) * shakeAmp * k
            canvas.save()
            canvas.translate(shakeX, shakeY)
        }

        drawBoard(canvas, st)
        if (st != null) drawTokens(canvas, st)
        particles.draw(canvas)
        if (shakeEnd > clock) canvas.restore()

        drawTopBar(canvas, st)
        drawBottomBar(canvas, st)

        if (showPerf) drawPerf(canvas)

        val animating = tokenAnim != null || diceAnim != null ||
            particles.activeCount() > 0 || shakeEnd > clock || confettiUntil > clock
        val ph = s?.phase
        if (animating) {
            // Battery saver hard-caps the render loop at ~30 fps without dropping frames.
            if (batterySaver) postInvalidateDelayed(33) else postInvalidateOnAnimation()
        } else if (st == null) {
            postInvalidateDelayed(250)
        } else if (ph == Session.Phase.AWAIT_ROLL || ph == Session.Phase.AWAIT_MOVE) {
            postInvalidateDelayed(if (batterySaver) 60 else 33)
        } else {
            postInvalidateDelayed(80)
        }
    }

    // ---------------------------------------------------------------- board
    private fun drawBoard(canvas: Canvas, st: GameState?) {
        val radius = cellSize * 0.9f
        // plate
        pShadow.color = Ui.withAlpha(Color.BLACK, if (theme.id == "day") 30 else 120)
        val plate = RectF(boardLeft - cellSize * 0.25f, boardTop - cellSize * 0.25f,
            boardLeft + boardSize + cellSize * 0.25f, boardTop + boardSize + cellSize * 0.25f)
        canvas.drawRoundRect(plate, radius * 1.6f, radius * 1.6f, pShadow)
        pFill.color = theme.boardBase
        canvas.drawRoundRect(plate, radius * 1.6f, radius * 1.6f, pFill)
        pStroke.color = theme.boardEdge
        pStroke.strokeWidth = Ui.dpf(context, 1.4f)
        canvas.drawRoundRect(plate, radius * 1.6f, radius * 1.6f, pStroke)

        // yards
        for (color in 0 until 4) {
            val o = Board.yardOrigin(color)
            val r = RectF(
                boardLeft + o.col * cellSize + cellSize * 0.14f,
                boardTop + o.row * cellSize + cellSize * 0.14f,
                boardLeft + (o.col + 6) * cellSize - cellSize * 0.14f,
                boardTop + (o.row + 6) * cellSize - cellSize * 0.14f
            )
            val c = st?.let { theme.colorFor(color) } ?: theme.playerColors[color]
            pFill.color = Ui.withAlpha(c, 30)
            canvas.drawRoundRect(r, radius * 1.4f, radius * 1.4f, pFill)
            pStroke.color = Ui.withAlpha(c, 130)
            pStroke.strokeWidth = Ui.dpf(context, 1.6f)
            canvas.drawRoundRect(r, radius * 1.4f, radius * 1.4f, pStroke)

            // inner slot plate
            val inner = RectF(r.left + cellSize * 0.5f, r.top + cellSize * 0.5f,
                r.right - cellSize * 0.5f, r.bottom - cellSize * 0.5f)
            pFill.color = Ui.withAlpha(theme.boardBase, 170)
            canvas.drawRoundRect(inner, radius, radius, pFill)

            for (t in 0 until 4) {
                val (sr, sc) = Board.yardSlot(color, t)
                pFill.color = Ui.withAlpha(c, 26)
                canvas.drawCircle(cx(sc), cy(sr), cellSize * 0.42f, pFill)
                pStroke.color = Ui.withAlpha(c, 90)
                pStroke.strokeWidth = Ui.dpf(context, 1.1f)
                canvas.drawCircle(cx(sc), cy(sr), cellSize * 0.42f, pStroke)
            }
        }

        // ring cells
        val ring = Board.ring()
        for (i in ring.indices) {
            val cell = ring[i]
            val rect = cellRects[cell.row * Board.GRID + cell.col]
            val isStart = Board.START_INDEX.contains(i)
            val isStar = Board.STAR_INDEX.contains(i)
            val startColor = if (isStart) theme.playerColors[Board.START_INDEX.indexOf(i)] else 0
            drawCell(canvas, rect, if (isStart) Ui.withAlpha(startColor, 150) else theme.cellFill)
            if (isStar) drawStar(canvas, rect, theme.safeTint, theme.textDim)
        }

        // home columns
        for (color in 0 until 4) {
            val c = theme.playerColors[color]
            for (p in 52..56) {
                val cell = Board.cellOf(color, p) ?: continue
                val rect = cellRects[cell.row * Board.GRID + cell.col]
                drawCell(canvas, rect, Ui.withAlpha(c, 120))
            }
        }

        // centre home
        val cl = boardLeft + 6 * cellSize
        val ct = boardTop + 6 * cellSize
        val cr = cl + 3 * cellSize
        val cb = ct + 3 * cellSize
        val midX = (cl + cr) / 2f
        val midY = (ct + cb) / 2f
        pFill.color = Ui.withAlpha(theme.boardBase, 255)
        canvas.drawRoundRect(RectF(cl + cellSize * 0.06f, ct + cellSize * 0.06f,
            cr - cellSize * 0.06f, cb - cellSize * 0.06f), radius * 0.6f, radius * 0.6f, pFill)
        val tri = Path()
        // four triangles from the centre square edges to the middle
        for (color in 0 until 4) {
            val c = theme.playerColors[color]
            tri.reset()
            when (color) {
                0 -> { tri.moveTo(cl, ct); tri.lineTo(cl, cb); tri.lineTo(midX, midY) }
                1 -> { tri.moveTo(cl, ct); tri.lineTo(cr, ct); tri.lineTo(midX, midY) }
                2 -> { tri.moveTo(cr, ct); tri.lineTo(cr, cb); tri.lineTo(midX, midY) }
                else -> { tri.moveTo(cl, cb); tri.lineTo(cr, cb); tri.lineTo(midX, midY) }
            }
            tri.close()
            pFill.color = Ui.withAlpha(c, 200)
            canvas.drawPath(tri, pFill)
        }
        pFill.color = Ui.withAlpha(theme.boardBase, 235)
        canvas.drawCircle(midX, midY, cellSize * 0.52f, pFill)
        pStroke.color = Ui.withAlpha(theme.accent, 180)
        pStroke.strokeWidth = Ui.dpf(context, 1.2f)
        canvas.drawCircle(midX, midY, cellSize * 0.52f, pStroke)

        // safe-cell glyphs for start cells
        for (color in 0 until 4) {
            val idx = Board.START_INDEX[color]
            val cell = Board.ringCell(idx)
            val rect = cellRects[cell.row * Board.GRID + cell.col]
            pStroke.color = Ui.withAlpha(Color.WHITE, 120)
            pStroke.strokeWidth = Ui.dpf(context, 1.3f)
            canvas.drawCircle(rect.centerX(), rect.centerY(), rect.width() * 0.26f, pStroke)
        }
    }

    private fun drawCell(canvas: Canvas, rect: RectF, fill: Int) {
        val inset = cellSize * boardSkin.inset * 0.5f
        val r = RectF(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset)
        val rad = cellSize * boardSkin.rounded * 0.5f
        pFill.color = fill
        canvas.drawRoundRect(r, rad, rad, pFill)
        pStroke.color = Ui.withAlpha(theme.cellEdge, 150)
        pStroke.strokeWidth = Ui.dpf(context, 0.8f)
        canvas.drawRoundRect(r, rad, rad, pStroke)
    }

    private fun drawStar(canvas: Canvas, rect: RectF, fill: Int, color: Int) {
        val inset = cellSize * 0.22f
        val r = RectF(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset)
        pFill.color = Ui.withAlpha(color, 46)
        canvas.drawRoundRect(r, cellSize * 0.18f, cellSize * 0.18f, pFill)
        val cxp = rect.centerX()
        val cyp = rect.centerY()
        val outer = cellSize * 0.20f
        val inner = outer * 0.46f
        path.reset()
        for (i in 0 until 10) {
            val ang = (-PI / 2 + i * PI / 5).toFloat()
            val rr = if (i % 2 == 0) outer else inner
            val x = cxp + cos(ang) * rr
            val y = cyp + sin(ang) * rr
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        pFill.color = Ui.withAlpha(color, 200)
        canvas.drawPath(path, pFill)
    }

    // ---------------------------------------------------------------- tokens
    private fun tokenPos(st: GameState, token: Int, out: FloatArray) {
        val player = token / Board.TOKENS_PER_PLAYER
        val color = st.colorOf(player)
        val pr = st.positions[token]
        when {
            pr == 0 -> {
                val (r, c) = Board.yardSlot(color, token % 4)
                out[0] = cx(c); out[1] = cy(r)
            }
            pr == Board.HOME_PROGRESS -> {
                val midX = boardLeft + 7.5f * cellSize
                val midY = boardTop + 7.5f * cellSize
                val off = cellSize * 0.20f
                val i = token % 4
                out[0] = midX + ((i % 2) * 2 - 1) * off * 0.55f
                out[1] = midY + ((i / 2) * 2 - 1) * off * 0.55f
            }
            else -> {
                val cell = Board.cellOf(color, pr)
                if (cell != null) {
                    out[0] = cx(cell.col.toFloat()); out[1] = cy(cell.row.toFloat())
                } else {
                    val (r, c) = Board.yardSlot(color, token % 4)
                    out[0] = cx(c); out[1] = cy(r)
                }
            }
        }
        // fan out tokens sharing a ring cell so none is hidden
        if (pr in 1..51) {
            val idx = Board.ringIndexOf(color, pr)
            var same = 0
            var total = 0
            for (t in st.positions.indices) {
                val pp = st.positions[t]
                if (pp !in 1..51) continue
                val pc = st.colorOf(t / Board.TOKENS_PER_PLAYER)
                if (Board.ringIndexOf(pc, pp) != idx) continue
                total++
                if (t < token) same++
            }
            if (total > 1) {
                val ang = (-PI / 2 + 2 * PI * same / total).toFloat()
                out[0] += cos(ang) * cellSize * 0.16f
                out[1] += sin(ang) * cellSize * 0.16f
            }
        }
    }

    private val tmpPos = FloatArray(2)

    private fun drawTokens(canvas: Canvas, st: GameState) {
        val a = tokenAnim
        val animPts = a?.pts
        for (token in st.positions.indices) {
            if (hidden.contains(token)) continue
            var x: Float
            var y: Float
            var lift = 0f
            if (a != null && a.token == token && animPts != null) {
                val n = animPts.size / 2
                val tt = easeInOutCubic(a.t.coerceIn(0f, 1f))
                val fpos = tt * (n - 1)
                val i0 = fpos.toInt().coerceIn(0, n - 1)
                val i1 = min(i0 + 1, n - 1)
                val frac = fpos - i0
                x = animPts[i0 * 2] + (animPts[i1 * 2] - animPts[i0 * 2]) * frac
                y = animPts[i0 * 2 + 1] + (animPts[i1 * 2 + 1] - animPts[i0 * 2 + 1]) * frac
                lift = sin(frac * PI.toFloat()) * cellSize * 0.55f
            } else {
                tokenPos(st, token, tmpPos)
                x = tmpPos[0]; y = tmpPos[1]
            }
            val player = token / Board.TOKENS_PER_PLAYER
            val color = theme.playerColors[st.colorOf(player) % theme.playerColors.size]
            val isHi = highlighted.contains(token)
            val baseR = cellSize * 0.31f
            val r = if (isHi) baseR * (1f + 0.06f * sin(clock * 7f)) else baseR

            // glow for selectable tokens
            if (isHi) {
                pFill.color = Ui.withAlpha(color, (70 + 40 * sin(clock * 7f)).toInt().coerceIn(20, 140))
                canvas.drawCircle(x, y - lift, r * 1.55f, pFill)
                pStroke.color = Ui.withAlpha(color, 220)
                pStroke.strokeWidth = Ui.dpf(context, 1.6f)
                canvas.drawCircle(x, y - lift, r * 1.35f, pStroke)
            }

            // ground shadow (stays on the board while the token lifts)
            pFill.color = Ui.withAlpha(Color.BLACK, (70 - (lift / cellSize * 60).toInt()).coerceIn(10, 80))
            canvas.drawOval(
                RectF(x - r * 0.85f, y + r * 0.55f, x + r * 0.85f, y + r * 1.05f), pFill
            )
            drawToken(canvas, x, y - lift, r, color, tokenSkin)
        }
        drawPathPreview(canvas, st)
    }

    /** Interactive path visualiser: dotted trail from a selectable token to its landing cell. */
    private fun drawPathPreview(canvas: Canvas, st: GameState) {
        val s = session ?: return
        if (s.phase != Session.Phase.AWAIT_MOVE || !s.isHuman(st.turn)) return
        if (s.legalMoves.isEmpty()) return
        for (m in s.legalMoves) {
            if (!highlighted.contains(m.token)) continue
            val color = theme.playerColors[st.colorOf(m.player) % theme.playerColors.size]
            val from = if (m.from == 0) 1 else m.from
            val span = (m.to - from).coerceAtLeast(1)
            val dots = span.coerceAtMost(9)
            pFill.color = Ui.withAlpha(color, 150)
            for (i in 1..dots) {
                val pr = from + (span.toFloat() * i / (dots + 1)).toInt()
                val cell = Board.cellOf(st.colorOf(m.player), pr) ?: continue
                val rr = cellSize * (0.055f + 0.035f * (i.toFloat() / dots))
                canvas.drawCircle(cx(cell.col.toFloat()), cy(cell.row.toFloat()), rr, pFill)
            }
        }
    }

    private fun drawToken(canvas: Canvas, x: Float, y: Float, r: Float, color: Int, skin: TokenSkin) {
        val key = (color.toLong() shl 20) or r.toLong()
        val shader = shaderCache.getOrPut(key) {
            RadialGradient(
                -r * 0.30f, -r * 0.35f, r * 1.55f,
                intArrayOf(Ui.lighten(color, 0.55f), color, Ui.darken(color, 0.35f)),
                floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
            )
        }
        localMatrix.setTranslate(x, y)
        shader.setLocalMatrix(localMatrix)
        pFill.shader = shader
        when (skin.id) {
            "gem" -> {
                path.reset()
                for (i in 0 until 6) {
                    val ang = (-PI / 2 + i * PI / 3).toFloat()
                    val px = x + cos(ang) * r
                    val py = y + sin(ang) * r
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close()
                canvas.drawPath(path, pFill)
            }
            "car" -> {
                val rr = RectF(x - r, y - r * 0.72f, x + r, y + r * 0.72f)
                canvas.drawRoundRect(rr, r * 0.45f, r * 0.45f, pFill)
                canvas.drawCircle(x - r * 0.55f, y + r * 0.62f, r * 0.22f, pFill)
                canvas.drawCircle(x + r * 0.55f, y + r * 0.62f, r * 0.22f, pFill)
            }
            "dragon" -> {
                path.reset()
                path.moveTo(x, y - r * 1.15f)
                path.lineTo(x + r, y + r * 0.55f)
                path.lineTo(x, y + r * 0.15f)
                path.lineTo(x - r, y + r * 0.55f)
                path.close()
                canvas.drawPath(path, pFill)
            }
            else -> {
                // classic pawn: body circle + head + collar
                val headR = r * 0.52f
                canvas.drawCircle(x, y + r * 0.30f, r * 0.92f, pFill)
                canvas.drawCircle(x, y - r * 0.55f, headR, pFill)
            }
        }
        pFill.shader = null
        // rim + specular
        pStroke.color = Ui.withAlpha(Color.WHITE, 130)
        pStroke.strokeWidth = Ui.dpf(context, 1.2f)
        if (skin.id == "gem") {
            canvas.drawCircle(x, y, r * 0.98f, pStroke)
        } else {
            canvas.drawCircle(x, y + (if (skin.id == "pawn") r * 0.30f else 0f), r * 0.92f, pStroke)
        }
        pFill.color = Ui.withAlpha(Color.WHITE, 150)
        canvas.drawCircle(x - r * 0.30f, y - r * (if (skin.id == "pawn") 0.72f else 0.35f), r * 0.18f, pFill)
    }

    // ---------------------------------------------------------------- HUD
    private fun drawTopBar(canvas: Canvas, st: GameState?) {
        val s = session ?: return
        val pad = Ui.dpf(context, 12f)
        val barY = Ui.dpf(context, 16f)
        val h = Ui.dpf(context, 40f)

        if (st == null) return
        val p = s.currentPlayer()
        val color = theme.playerColors[st.colorOf(p) % theme.playerColors.size]
        val name = s.playerName(p)

        // player chip
        val chipW = min(width - pad * 2 - pauseRect.width() - pad, Ui.dpf(context, 240f))
        val chip = RectF(pad, barY, pad + chipW, barY + h)
        pFill.color = Ui.withAlpha(theme.panel, 235)
        canvas.drawRoundRect(chip, h / 2f, h / 2f, pFill)
        pStroke.color = Ui.withAlpha(color, 190)
        pStroke.strokeWidth = Ui.dpf(context, 1.6f)
        canvas.drawRoundRect(chip, h / 2f, h / 2f, pStroke)

        pFill.color = color
        canvas.drawCircle(chip.left + h * 0.55f, chip.centerY(), h * 0.24f, pFill)

        pText.color = theme.text
        pText.textSize = Ui.sp(context, 14.5f)
        pText.typeface = tfMedium
        pText.textAlign = Paint.Align.LEFT
        canvas.drawText(name, chip.left + h * 0.95f, chip.centerY() + pText.textSize * 0.36f, pText)

        // turn timer bar inside the chip
        val total = s.turnTimerSeconds
        if (total > 0 && s.phase == Session.Phase.AWAIT_ROLL) {
            val frac = s.timerFraction().coerceIn(0f, 1f)
            val barR = RectF(chip.left + h * 0.95f, chip.bottom - Ui.dpf(context, 9f),
                chip.right - Ui.dpf(context, 10f), chip.bottom - Ui.dpf(context, 5f))
            pFill.color = Ui.withAlpha(theme.textDim, 60)
            canvas.drawRoundRect(barR, barR.height() / 2f, barR.height() / 2f, pFill)
            val fillR = RectF(barR.left, barR.top, barR.left + barR.width() * frac, barR.bottom)
            pFill.color = if (frac < 0.25f) Color.rgb(230, 90, 90) else Ui.withAlpha(color, 230)
            canvas.drawRoundRect(fillR, barR.height() / 2f, barR.height() / 2f, pFill)
        }

        // pause button
        pFill.color = Ui.withAlpha(theme.panel, 235)
        canvas.drawRoundRect(pauseRect, pauseRect.height() * 0.3f, pauseRect.height() * 0.3f, pFill)
        pStroke.color = Ui.withAlpha(theme.panelEdge, 220)
        pStroke.strokeWidth = Ui.dpf(context, 1.2f)
        canvas.drawRoundRect(pauseRect, pauseRect.height() * 0.3f, pauseRect.height() * 0.3f, pStroke)
        pFill.color = theme.text
        val bw = pauseRect.width() * 0.13f
        val bh = pauseRect.height() * 0.42f
        canvas.drawRoundRect(RectF(pauseRect.centerX() - bw * 1.9f, pauseRect.centerY() - bh / 2,
            pauseRect.centerX() - bw * 0.5f, pauseRect.centerY() + bh / 2), bw * 0.5f, bw * 0.5f, pFill)
        canvas.drawRoundRect(RectF(pauseRect.centerX() + bw * 0.5f, pauseRect.centerY() - bh / 2,
            pauseRect.centerX() + bw * 1.9f, pauseRect.centerY() + bh / 2), bw * 0.5f, bw * 0.5f, pFill)
    }

    private fun drawBottomBar(canvas: Canvas, st: GameState?) {
        val s = session ?: return
        val pad = Ui.dpf(context, 12f)
        val cyBar = height - bottomBarH / 2f

        // status text + ticker
        pText.textAlign = Paint.Align.LEFT
        pText.typeface = tfMedium
        pText.textSize = Ui.sp(context, 13.5f)
        pText.color = theme.text
        val status = s.statusText()
        canvas.drawText(status, pad, cyBar - Ui.dpf(context, 14f), pText)

        pText.typeface = Typeface.DEFAULT
        pText.textSize = Ui.sp(context, 11.5f)
        pText.color = Ui.withAlpha(theme.textDim, 220)
        canvas.drawText(s.tickerText(), pad, cyBar + Ui.dpf(context, 14f), pText)

        // dice
        val isMyTurn = s.canRoll()
        val rollGlow = if (isMyTurn) (0.5f + 0.5f * sin(clock * 4f)) else 0f
        pShadow.color = Ui.withAlpha(Color.BLACK, 110)
        canvas.drawRoundRect(RectF(diceRect.left, diceRect.top + Ui.dpf(context, 4f),
            diceRect.right, diceRect.bottom + Ui.dpf(context, 4f)),
            diceRect.width() * 0.24f, diceRect.width() * 0.24f, pShadow)
        if (isMyTurn) {
            pFill.color = Ui.withAlpha(theme.accent, (30 + 40 * rollGlow).toInt())
            canvas.drawRoundRect(diceRect, diceRect.width() * 0.3f, diceRect.width() * 0.3f, pFill)
        }
        pFill.color = Color.rgb(248, 250, 255)
        val dRad = diceRect.width() * 0.24f
        canvas.drawRoundRect(diceRect, dRad, dRad, pFill)
        pStroke.color = if (isMyTurn) theme.accent else Ui.withAlpha(theme.textDim, 120)
        pStroke.strokeWidth = Ui.dpf(context, if (isMyTurn) 2.2f else 1.2f)
        canvas.drawRoundRect(diceRect, dRad, dRad, pStroke)

        // dice face (with tumble while rolling)
        val a = diceAnim
        if (a != null) {
            canvas.save()
            canvas.rotate(a.t * 540f, diceRect.centerX(), diceRect.centerY())
            canvas.scale(1f + diceSquash * 0.2f, 1f - diceSquash * 0.3f, diceRect.centerX(), diceRect.centerY())
            val shown = 1 + ((a.t * 24).toInt() % 6)
            drawPips(canvas, if (a.t > 0.85f) a.result else shown)
            canvas.restore()
        } else {
            drawPips(canvas, diceFace)
        }
    }

    private fun drawPips(canvas: Canvas, face: Int) {
        val cxp = diceRect.centerX()
        val cyp = diceRect.centerY()
        val r = diceRect.width() * 0.075f
        val gap = diceRect.width() * 0.19f
        pFill.color = Color.rgb(24, 30, 44)
        fun pip(dx: Float, dy: Float) = canvas.drawCircle(cxp + dx * gap, cyp + dy * gap, r, pFill)
        when (face) {
            1 -> pip(0f, 0f)
            2 -> { pip(-1f, -1f); pip(1f, 1f) }
            3 -> { pip(-1f, -1f); pip(0f, 0f); pip(1f, 1f) }
            4 -> { pip(-1f, -1f); pip(1f, -1f); pip(-1f, 1f); pip(1f, 1f) }
            5 -> { pip(-1f, -1f); pip(1f, -1f); pip(0f, 0f); pip(-1f, 1f); pip(1f, 1f) }
            6 -> { pip(-1f, -1f); pip(1f, -1f); pip(-1f, 0f); pip(1f, 0f); pip(-1f, 1f); pip(1f, 1f) }
        }
    }

    private fun drawPerf(canvas: Canvas) {
        pText.textAlign = Paint.Align.LEFT
        pText.typeface = Typeface.MONOSPACE
        pText.textSize = Ui.sp(context, 11f)
        pText.color = if (perf.fps >= 55) Color.rgb(120, 240, 170) else Color.rgb(255, 180, 90)
        val txt = "FPS ${perf.fps}  ${perf.frameMs}ms  p${particles.activeCount()}"
        canvas.drawText(txt, boardLeft, boardTop - Ui.dpf(context, 6f), pText)
        pText.typeface = tfMedium
    }

    // ---------------------------------------------------------------- input
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val s = session ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val x = event.x
                val y = event.y
                if (pauseRect.contains(x, y)) {
                    onPauseTap?.invoke()
                    return true
                }
                if (diceRect.contains(x, y) || isNearDice(x, y)) {
                    onDiceTap?.invoke()
                    return true
                }
                // token hit test
                val st = s.state
                var best = -1
                var bestD = Float.MAX_VALUE
                for (t in highlighted) {
                    if (t !in st.positions.indices) continue
                    tokenPos(st, t, tmpPos)
                    val dx = tmpPos[0] - x
                    val dy = tmpPos[1] - y
                    val d = sqrt(dx * dx + dy * dy)
                    if (d < cellSize * 0.85f && d < bestD) { bestD = d; best = t }
                }
                if (best >= 0) {
                    onTokenTap?.invoke(best)
                    return true
                }
            }
        }
        return true
    }

    private fun isNearDice(x: Float, y: Float): Boolean {
        val grow = cellSize * 0.4f
        return x > diceRect.left - grow && x < diceRect.right + grow &&
            y > diceRect.top - grow && y < diceRect.bottom + grow
    }

    // ---------------------------------------------------------------- easing
    fun easeInOutCubic(t: Float): Float =
        if (t < 0.5f) 4f * t * t * t else 1f - Math.pow((-2f * t + 2f).toDouble(), 3.0).toFloat() / 2f

    fun easeOutBack(t: Float): Float {
        val c1 = 1.70158f
        val c3 = c1 + 1f
        return 1f + c3 * Math.pow((t - 1).toDouble(), 3.0).toFloat() + c1 * Math.pow((t - 1).toDouble(), 2.0).toFloat()
    }

    fun resetClock() {
        lastFrameNs = 0L
        clock = 0f
    }
}
