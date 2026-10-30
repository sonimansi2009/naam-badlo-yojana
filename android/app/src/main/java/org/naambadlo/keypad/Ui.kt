package org.naambadlo.keypad

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Design system: Ambedkar blue, serif type, quiet motion.
 * Everything is built in code, so there are no layout files to keep in sync.
 */
object Ui {
    // Colour
    const val BLUE = 0xFF1446A0.toInt()        // Ambedkar blue
    const val BLUE_DEEP = 0xFF0B2E6B.toInt()   // keyboard panel, header
    const val BLUE_SOFT = 0xFF2C5DBF.toInt()   // chips on the dark panel
    const val BLUE_TINT = 0xFFE8EEFA.toInt()   // soft fill on light surfaces
    const val PAGE = 0xFFF5F7FB.toInt()
    const val SURFACE = 0xFFFFFFFF.toInt()
    const val INK = 0xFF0F1A2E.toInt()
    const val MUTED = 0xFF5A6884.toInt()
    const val LINE = 0xFFD9E1EF.toInt()
    const val ON_BLUE = 0xFFFFFFFF.toInt()
    const val ON_BLUE_MUTED = 0xFFB9C8EA.toInt()
    const val GOOD = 0xFF1E8E5A.toInt()

    // Type
    val SERIF: Typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
    val SERIF_BOLD: Typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)

    fun dp(ctx: Context, v: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), ctx.resources.displayMetrics).toInt()

    fun rounded(color: Int, radiusDp: Int, ctx: Context, strokeColor: Int? = null) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(ctx, radiusDp).toFloat()
        if (strokeColor != null) setStroke(dp(ctx, 1), strokeColor)
    }

    fun label(ctx: Context, text: String, sizeSp: Float = 15f, color: Int = INK, bold: Boolean = false) =
        TextView(ctx).apply {
            this.text = text
            setTextColor(color)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
            typeface = if (bold) SERIF_BOLD else SERIF
            setLineSpacing(0f, 1.18f)
        }

    enum class Style { PRIMARY, OUTLINE, ON_DARK, ON_DARK_PRIMARY }

    /** A full button with a press animation. */
    fun button(ctx: Context, text: String, style: Style = Style.PRIMARY, onClick: () -> Unit) =
        TextView(ctx).apply {
            this.text = text
            typeface = SERIF_BOLD
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(dp(ctx, 18), dp(ctx, 12), dp(ctx, 18), dp(ctx, 12))
            when (style) {
                Style.PRIMARY -> { background = rounded(BLUE, 14, ctx); setTextColor(ON_BLUE) }
                Style.OUTLINE -> { background = rounded(SURFACE, 14, ctx, LINE); setTextColor(BLUE) }
                Style.ON_DARK -> { background = rounded(BLUE_SOFT, 12, ctx); setTextColor(ON_BLUE) }
                Style.ON_DARK_PRIMARY -> { background = rounded(SURFACE, 12, ctx); setTextColor(BLUE_DEEP) }
            }
            isClickable = true
            setOnClickListener { onClick() }
            pressable(this)
        }

    /** A rounded option chip. */
    fun chip(ctx: Context, text: String, selected: Boolean = false, dark: Boolean = false, onClick: () -> Unit) =
        TextView(ctx).apply {
            this.text = text
            typeface = SERIF
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            gravity = Gravity.CENTER
            setPadding(dp(ctx, 15), dp(ctx, 8), dp(ctx, 15), dp(ctx, 8))
            styleChip(this, selected, dark)
            isClickable = true
            setOnClickListener { onClick() }
            pressable(this)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(ctx, 4), dp(ctx, 4), dp(ctx, 4), dp(ctx, 4)) }
        }

    fun styleChip(v: TextView, selected: Boolean, dark: Boolean) {
        val ctx = v.context
        when {
            dark -> { v.background = rounded(BLUE_SOFT, 20, ctx); v.setTextColor(ON_BLUE) }
            selected -> { v.background = rounded(BLUE, 20, ctx); v.setTextColor(ON_BLUE) }
            else -> { v.background = rounded(SURFACE, 20, ctx, LINE); v.setTextColor(INK) }
        }
    }

    /** Scale down slightly while pressed. Does not consume the touch. */
    @SuppressLint("ClickableViewAccessibility")
    fun pressable(v: View) {
        v.setOnTouchListener { view, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN ->
                    view.animate().scaleX(0.96f).scaleY(0.96f).setDuration(90).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    view.animate().scaleX(1f).scaleY(1f).setDuration(160)
                        .setInterpolator(OvershootInterpolator(2f)).start()
            }
            false
        }
    }

    /** Fade and rise into place. */
    fun enter(v: View, delayMs: Long = 0, rise: Int = 10) {
        v.alpha = 0f
        v.translationY = dp(v.context, rise).toFloat()
        v.animate().alpha(1f).translationY(0f).setStartDelay(delayMs).setDuration(260)
            .setInterpolator(DecelerateInterpolator()).start()
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

    /** White card with a hairline border. */
    fun card(ctx: Context) = vertical(ctx, 18).apply {
        background = rounded(SURFACE, 18, ctx, LINE)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 0, 0, dp(ctx, 14)) }
    }

    fun spacer(ctx: Context, hDp: Int) = View(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(ctx, hDp))
    }

    fun fullWidth(v: View, ctx: Context, topDp: Int = 0): View {
        v.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, dp(ctx, topDp), 0, 0) }
        return v
    }
}
