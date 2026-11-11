package com.apexludo.app

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView

/**
 * Small programmatic UI kit: every panel, button and row in the app is built from these
 * helpers so the "Windows-style" crisp borders + frosted panels stay consistent and no
 * layout XML can break at runtime.
 */
object Ui {

    fun dp(ctx: Context, v: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, ctx.resources.displayMetrics).toInt()

    fun dpf(ctx: Context, v: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, ctx.resources.displayMetrics)

    fun sp(ctx: Context, v: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, ctx.resources.displayMetrics)

    private fun rounded(fill: Int, stroke: Int, radius: Float, strokeWidth: Int = 2): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(fill)
            if (strokeWidth > 0) setStroke(strokeWidth, stroke)
        }

    private fun ripple(fill: Int, stroke: Int, radius: Float, rippleColor: Int, strokeWidth: Int = 2): RippleDrawable {
        val content = rounded(fill, stroke, radius, strokeWidth)
        return RippleDrawable(ColorStateList.valueOf(rippleColor), content, null)
    }

    fun panel(ctx: Context, theme: Theme, radiusDp: Float = 18f): LinearLayout {
        val ll = LinearLayout(ctx)
        ll.orientation = LinearLayout.VERTICAL
        ll.background = rounded(withAlpha(theme.panel, 246), theme.panelEdge, dpf(ctx, radiusDp), dp(ctx, 1.2f))
        val pad = dp(ctx, 18f)
        ll.setPadding(pad, pad, pad, pad)
        ll.elevation = dpf(ctx, 8f)
        return ll
    }

    fun title(ctx: Context, theme: Theme, text: String, size: Float = 21f): TextView {
        val tv = TextView(ctx)
        tv.text = text
        tv.setTextColor(theme.text)
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        tv.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        tv.setPadding(0, dp(ctx, 2f), 0, dp(ctx, 6f))
        return tv
    }

    fun body(ctx: Context, theme: Theme, text: String, size: Float = 14f, dim: Boolean = false): TextView {
        val tv = TextView(ctx)
        tv.text = text
        tv.setTextColor(if (dim) theme.textDim else theme.text)
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        tv.setLineSpacing(dpf(ctx, 3f), 1f)
        tv.setPadding(0, dp(ctx, 2f), 0, dp(ctx, 6f))
        return tv
    }

    fun label(ctx: Context, theme: Theme, text: String): TextView {
        val tv = body(ctx, theme, text, 14.5f)
        tv.setTextColor(theme.text)
        return tv
    }

    fun value(ctx: Context, theme: Theme, text: String): TextView {
        val tv = body(ctx, theme, text, 13.5f, dim = true)
        tv.gravity = Gravity.END or Gravity.CENTER_VERTICAL
        return tv
    }

    fun button(
        ctx: Context,
        theme: Theme,
        text: String,
        primary: Boolean = false,
        onClick: () -> Unit
    ): TextView {
        val tv = TextView(ctx)
        tv.text = text
        tv.gravity = Gravity.CENTER
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        tv.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        tv.setTextColor(if (primary) Color.rgb(8, 14, 20) else theme.text)
        val fill = if (primary) theme.accent else withAlpha(theme.cellFill, 255)
        val stroke = if (primary) theme.accent else theme.cellEdge
        tv.background = ripple(fill, stroke, dpf(ctx, 12f), withAlpha(theme.accent, 70), dp(ctx, 1.2f))
        val ph = dp(ctx, 13f); val pv = dp(ctx, 13f)
        tv.setPadding(ph, pv, ph, pv)
        tv.isClickable = true
        tv.isFocusable = true
        tv.setOnClickListener {
            it.animate().scaleX(0.96f).scaleY(0.96f).setDuration(70).withEndAction {
                it.animate().scaleX(1f).scaleY(1f).setDuration(90).start()
            }.start()
            onClick()
        }
        return tv
    }

    fun chip(ctx: Context, theme: Theme, text: String, accentColor: Int): TextView {
        val tv = TextView(ctx)
        tv.text = text
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        tv.setTextColor(theme.text)
        tv.gravity = Gravity.CENTER
        tv.setPadding(dp(ctx, 10f), dp(ctx, 5f), dp(ctx, 10f), dp(ctx, 5f))
        tv.background = rounded(withAlpha(accentColor, 46), withAlpha(accentColor, 160), dpf(ctx, 20f), dp(ctx, 1f))
        return tv
    }

    fun row(ctx: Context): LinearLayout {
        val ll = LinearLayout(ctx)
        ll.orientation = LinearLayout.HORIZONTAL
        ll.gravity = Gravity.CENTER_VERTICAL
        return ll
    }

    fun column(ctx: Context): LinearLayout {
        val ll = LinearLayout(ctx)
        ll.orientation = LinearLayout.VERTICAL
        return ll
    }

    fun spacer(ctx: Context, heightDp: Float): View {
        val v = View(ctx)
        v.layoutParams = LinearLayout.LayoutParams(1, dp(ctx, heightDp))
        return v
    }

    fun space(ctx: Context, weight: Float = 1f): View {
        val v = View(ctx)
        v.layoutParams = LinearLayout.LayoutParams(0, 1, weight)
        return v
    }

    fun switchRow(
        ctx: Context,
        theme: Theme,
        text: String,
        checked: Boolean,
        onChange: (Boolean) -> Unit
    ): LinearLayout {
        val ll = row(ctx)
        ll.setPadding(0, dp(ctx, 7f), 0, dp(ctx, 7f))
        val tv = label(ctx, theme, text)
        tv.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        val sw = Switch(ctx)
        sw.isChecked = checked
        sw.thumbTintList = ColorStateList.valueOf(theme.accent)
        sw.trackTintList = ColorStateList.valueOf(withAlpha(theme.textDim, 90))
        sw.setOnCheckedChangeListener { _, isOn -> onChange(isOn) }
        ll.addView(tv)
        ll.addView(sw)
        return ll
    }

    fun sliderRow(
        ctx: Context,
        theme: Theme,
        text: String,
        value: Int,
        min: Int,
        max: Int,
        onChange: (Int) -> Unit
    ): LinearLayout {
        val col = column(ctx)
        col.setPadding(0, dp(ctx, 7f), 0, dp(ctx, 7f))
        val head = row(ctx)
        val tv = label(ctx, theme, text)
        tv.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        val v = value(ctx, theme, value.toString())
        head.addView(tv)
        head.addView(v)
        col.addView(head)
        val sb = SeekBar(ctx)
        sb.max = (max - min).coerceAtLeast(1)
        sb.progress = (value - min).coerceIn(0, sb.max)
        sb.progressTintList = ColorStateList.valueOf(theme.accent)
        sb.thumbTintList = ColorStateList.valueOf(theme.accent)
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                val real = min + progress
                v.text = real.toString()
                if (fromUser) onChange(real)
            }
            override fun onStartTrackingTouch(bar: SeekBar?) {}
            override fun onStopTrackingTouch(bar: SeekBar?) {}
        })
        col.addView(sb)
        return col
    }

    fun scroll(ctx: Context): ScrollView {
        val sv = ScrollView(ctx)
        sv.isFillViewport = true
        sv.overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        return sv
    }

    fun divider(ctx: Context, theme: Theme): View {
        val v = View(ctx)
        v.setBackgroundColor(withAlpha(theme.panelEdge, 140))
        v.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 1f))
        return v
    }

    fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

    fun lighten(color: Int, amount: Float): Int {
        val r = (Color.red(color) + (255 - Color.red(color)) * amount).toInt().coerceIn(0, 255)
        val g = (Color.green(color) + (255 - Color.green(color)) * amount).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) + (255 - Color.blue(color)) * amount).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }

    fun darken(color: Int, amount: Float): Int {
        val r = (Color.red(color) * (1f - amount)).toInt().coerceIn(0, 255)
        val g = (Color.green(color) * (1f - amount)).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) * (1f - amount)).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }
}
