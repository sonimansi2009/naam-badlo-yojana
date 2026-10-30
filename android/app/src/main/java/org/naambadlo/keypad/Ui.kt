package org.naambadlo.keypad

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView

/** Small helpers so the UI can be built in code without layout files. */
object Ui {
    const val BG = 0xFF17181B.toInt()
    const val PANEL = 0xFF24262B.toInt()
    const val CHIP = 0xFF33363D.toInt()
    const val ACCENT = 0xFFF2994A.toInt()
    const val TEXT = Color.WHITE
    const val MUTED = 0xFFA6A9B0.toInt()

    fun dp(ctx: Context, v: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), ctx.resources.displayMetrics).toInt()

    fun rounded(color: Int, radiusDp: Int, ctx: Context) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(ctx, radiusDp).toFloat()
    }

    fun label(ctx: Context, text: String, sizeSp: Float = 14f, color: Int = TEXT, bold: Boolean = false) =
        TextView(ctx).apply {
            this.text = text
            setTextColor(color)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    fun chip(ctx: Context, text: String, accent: Boolean = false, onClick: () -> Unit) =
        TextView(ctx).apply {
            this.text = text
            setTextColor(if (accent) Color.BLACK else TEXT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            gravity = Gravity.CENTER
            setPadding(dp(ctx, 14), dp(ctx, 8), dp(ctx, 14), dp(ctx, 8))
            background = rounded(if (accent) ACCENT else CHIP, 18, ctx)
            isClickable = true
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(ctx, 4), dp(ctx, 4), dp(ctx, 4), dp(ctx, 4)) }
        }

    /** A horizontally scrolling row; returns (scroll view, inner row). */
    fun chipRow(ctx: Context): Pair<HorizontalScrollView, LinearLayout> {
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        val scroll = HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        }
        return scroll to row
    }

    fun vertical(ctx: Context, padDp: Int = 0) = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        val p = dp(ctx, padDp)
        setPadding(p, p, p, p)
    }

    fun spacer(ctx: Context, hDp: Int) = View(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(ctx, hDp))
    }
}
