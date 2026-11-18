package com.xaviourzone.focuslauncher

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** Small helpers so screens can be built in code without XML layouts. */
object Ui {
    const val TEXT = 0xFFF2F2F2.toInt()
    const val SUB = 0xFFA6A8AD.toInt()
    const val CARD = 0xFF1C1E23.toInt()
    const val ACCENT = 0xFF7FD6B5.toInt()

    fun rounded(color: Int, radiusPx: Float, stroke: Int = 0, strokeColor: Int = Color.TRANSPARENT) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radiusPx
            if (stroke > 0) setStroke(stroke, strokeColor)
        }

    fun text(ctx: Context, s: CharSequence, sizeSp: Float = 15f, color: Int = TEXT, bold: Boolean = false) =
        TextView(ctx).apply {
            text = s
            textSize = sizeSp
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    fun pill(ctx: Context, s: CharSequence, bg: Int = 0x33FFFFFF, fg: Int = TEXT, onClick: (View) -> Unit) =
        TextView(ctx).apply {
            text = s
            textSize = 14f
            setTextColor(fg)
            gravity = Gravity.CENTER
            setPadding(ctx.dp(16), ctx.dp(9), ctx.dp(16), ctx.dp(9))
            background = rounded(bg, ctx.dp(20).toFloat())
            isClickable = true
            setOnClickListener(onClick)
        }

    fun vertical(ctx: Context) = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
    fun horizontal(ctx: Context) = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    fun lp(w: Int = ViewGroup.LayoutParams.MATCH_PARENT, h: Int = ViewGroup.LayoutParams.WRAP_CONTENT) =
        LinearLayout.LayoutParams(w, h)

    /** Icon + label tile used on home and in the side panel. */
    fun appTile(ctx: Context, pkg: String, iconDp: Int, showLabel: Boolean, labelColor: Int = TEXT): LinearLayout {
        val box = vertical(ctx).apply { gravity = Gravity.CENTER_HORIZONTAL }
        val iv = ImageView(ctx).apply { setImageDrawable(Apps.icon(ctx, pkg)) }
        box.addView(iv, LinearLayout.LayoutParams(ctx.dp(iconDp), ctx.dp(iconDp)))
        if (showLabel) {
            val tv = text(ctx, Apps.label(ctx, pkg), 12f, labelColor).apply {
                maxLines = 1
                gravity = Gravity.CENTER
                setShadowLayer(4f, 0f, 1f, 0xAA000000.toInt())
            }
            box.addView(tv, lp(ctx.dp(iconDp + 24)).apply { topMargin = ctx.dp(4) })
        }
        return box
    }
}
