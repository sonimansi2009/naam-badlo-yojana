package org.naambadlo.keypad

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.MetricAffectingSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Design system: soft periwinkle ground, big white cards, black active pills,
 * a butter-yellow call to action with a round black arrow.
 * Type: Instrument Sans, with Instrument Serif Italic for the one accent word.
 */
object Ui {
    // Colour
    const val BG = 0xFFCAD3EB.toInt()          // periwinkle page
    const val BG_TOP = 0xFFD8DFF1.toInt()      // lighter top of the page gradient
    const val SURFACE = 0xFFFFFFFF.toInt()
    const val SOFT = 0xFFE8ECF6.toInt()        // inactive pills, inner panels
    const val SOFT_DEEP = 0xFFD9E0F0.toInt()   // keyboard panel
    const val INK = 0xFF101217.toInt()
    const val MUTED = 0xFF6A7184.toInt()
    const val LINE = 0xFFDDE3F0.toInt()
    const val BLACK = 0xFF121316.toInt()
    const val BUTTER = 0xFFF7D47F.toInt()      // call to action
    const val BLUE = 0xFF1A4FD6.toInt()        // Ambedkar blue, used sparingly
    const val GOOD = 0xFF13925F.toInt()
    const val WHITE = 0xFFFFFFFF.toInt()

    // Type
    private var appCtx: Context? = null
    fun init(ctx: Context) { appCtx = ctx.applicationContext }
    private fun font(file: String, style: Int): Typeface =
        appCtx?.let { runCatching { Typeface.createFromAsset(it.assets, "fonts/$file") }.getOrNull() }
            ?: Typeface.create(Typeface.SANS_SERIF, style)
    val SANS: Typeface by lazy { font("isans_regular.ttf", Typeface.NORMAL) }
    val SANS_MEDIUM: Typeface by lazy { font("isans_medium.ttf", Typeface.NORMAL) }
    val SANS_SEMI: Typeface by lazy { font("isans_semibold.ttf", Typeface.BOLD) }
    val SERIF_ITALIC: Typeface by lazy {
        appCtx?.let { runCatching { Typeface.createFromAsset(it.assets, "fonts/iserif_italic.ttf") }.getOrNull() }
            ?: Typeface.create(Typeface.SERIF, Typeface.ITALIC)
    }

    enum class W { REG, MED, SEMI }
    private fun tf(w: W) = when (w) { W.REG -> SANS; W.MED -> SANS_MEDIUM; W.SEMI -> SANS_SEMI }

    fun dp(ctx: Context, v: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), ctx.resources.displayMetrics).toInt()

    fun rounded(color: Int, radiusDp: Int, ctx: Context, strokeColor: Int? = null) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(ctx, radiusDp).toFloat()
        if (strokeColor != null) setStroke(dp(ctx, 1), strokeColor)
    }

    fun oval(color: Int) = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }

    fun pageBackground() = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(BG_TOP, BG))

    fun label(ctx: Context, text: CharSequence, sizeSp: Float = 15f, color: Int = INK, w: W = W.REG) =
        TextView(ctx).apply {
            this.text = text
            setTextColor(color)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
            typeface = tf(w)
            setLineSpacing(0f, 1.2f)
        }

    /** Big headline. Words wrapped in *stars* are set in Instrument Serif Italic. */
    fun headline(ctx: Context, text: String, sizeSp: Float, color: Int = INK) =
        label(ctx, styled(text), sizeSp, color, W.MED).apply {
            setLineSpacing(0f, 1.0f)
            letterSpacing = -0.025f
            includeFontPadding = false
        }

    fun styled(text: String): CharSequence {
        val sb = SpannableStringBuilder()
        var italic = false
        for (part in text.split('*')) {
            val start = sb.length
            sb.append(part)
            if (italic && part.isNotEmpty()) sb.setSpan(FontSpan(SERIF_ITALIC, 1.08f), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            italic = !italic
        }
        return sb
    }

    /** Swaps the typeface for part of a TextView (works on every Android version). */
    class FontSpan(private val face: Typeface, private val scale: Float = 1f) : MetricAffectingSpan() {
        override fun updateDrawState(tp: TextPaint) = apply(tp)
        override fun updateMeasureState(tp: TextPaint) = apply(tp)
        private fun apply(p: Paint) { p.typeface = face; p.textSize = p.textSize * scale }
    }

    fun icon(ctx: Context, res: Int, tint: Int, sizeDp: Int = 22) = ImageView(ctx).apply {
        setImageResource(res)
        imageTintList = ColorStateList.valueOf(tint)
        layoutParams = LinearLayout.LayoutParams(dp(ctx, sizeDp), dp(ctx, sizeDp))
    }

    /** Round icon button, like the bell in the reference. */
    fun iconCircle(ctx: Context, res: Int, bg: Int = SURFACE, tint: Int = INK, sizeDp: Int = 44, onClick: (() -> Unit)? = null) =
        FrameLayout(ctx).apply {
            background = oval(bg)
            layoutParams = LinearLayout.LayoutParams(dp(ctx, sizeDp), dp(ctx, sizeDp))
            val i = icon(ctx, res, tint, (sizeDp * 0.45f).toInt())
            addView(i, FrameLayout.LayoutParams(i.layoutParams.width, i.layoutParams.height, Gravity.CENTER))
            if (onClick != null) { isClickable = true; setOnClickListener { onClick() }; pressable(this) }
        }

    /** Tab pill: black when active, soft grey-blue when not. */
    fun pill(ctx: Context, text: String, active: Boolean, onClick: () -> Unit) = TextView(ctx).apply {
        this.text = text
        typeface = SANS_MEDIUM
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        gravity = Gravity.CENTER
        setPadding(dp(ctx, 18), dp(ctx, 10), dp(ctx, 18), dp(ctx, 10))
        stylePill(this, active)
        isClickable = true
        setOnClickListener { onClick() }
        pressable(this)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 0, dp(ctx, 8), 0) }
    }

    fun stylePill(v: TextView, active: Boolean) {
        v.background = rounded(if (active) BLACK else SOFT, 22, v.context)
        v.setTextColor(if (active) WHITE else INK)
    }

    /** Small outlined fact tag, like "3 Beds". */
    fun tag(ctx: Context, text: String) = TextView(ctx).apply {
        this.text = text
        typeface = SANS
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
        setTextColor(INK)
        setPadding(dp(ctx, 12), dp(ctx, 6), dp(ctx, 12), dp(ctx, 6))
        background = rounded(SURFACE, 16, ctx, LINE)
    }

    /** Butter-yellow call to action with a round black arrow. */
    fun cta(ctx: Context, text: String, iconRes: Int = R.drawable.ic_arrow, onClick: () -> Unit) =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(BUTTER, 28, ctx)
            setPadding(dp(ctx, 20), dp(ctx, 6), dp(ctx, 6), dp(ctx, 6))
            elevation = dp(ctx, 2).toFloat()
            val t = label(ctx, text, 15f, INK, W.SEMI).apply { maxLines = 1 }
            addView(t)
            val circle = iconCircle(ctx, iconRes, BLACK, WHITE, 36)
            (circle.layoutParams as LinearLayout.LayoutParams).setMargins(dp(ctx, 12), 0, 0, 0)
            addView(circle)
            isClickable = true
            setOnClickListener { onClick() }
            pressable(this)
        }

    /** Black pill button. */
    fun darkButton(ctx: Context, text: String, onClick: () -> Unit) = label(ctx, text, 15f, WHITE, W.SEMI).apply {
        gravity = Gravity.CENTER
        setPadding(dp(ctx, 20), dp(ctx, 13), dp(ctx, 20), dp(ctx, 13))
        background = rounded(BLACK, 26, ctx)
        isClickable = true
        setOnClickListener { onClick() }
        pressable(this)
    }

    /** White pill button with a hairline. */
    fun lightButton(ctx: Context, text: String, onClick: () -> Unit) = label(ctx, text, 15f, INK, W.MED).apply {
        gravity = Gravity.CENTER
        setPadding(dp(ctx, 20), dp(ctx, 13), dp(ctx, 20), dp(ctx, 13))
        background = rounded(SURFACE, 26, ctx, LINE)
        isClickable = true
        setOnClickListener { onClick() }
        pressable(this)
    }

    /** A code-name chip. */
    fun chip(ctx: Context, text: String, selected: Boolean = false, onClick: () -> Unit) = TextView(ctx).apply {
        this.text = text
        typeface = SANS_MEDIUM
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        gravity = Gravity.CENTER
        setPadding(dp(ctx, 16), dp(ctx, 9), dp(ctx, 16), dp(ctx, 9))
        styleChip(this, selected)
        isClickable = true
        setOnClickListener { onClick() }
        pressable(this)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(dp(ctx, 3), dp(ctx, 3), dp(ctx, 3), dp(ctx, 3)) }
    }

    fun styleChip(v: TextView, selected: Boolean) {
        v.background = rounded(if (selected) BLACK else SURFACE, 22, v.context, if (selected) null else LINE)
        v.setTextColor(if (selected) WHITE else INK)
    }

    /** Big white card. */
    fun card(ctx: Context, padDp: Int = 18) = vertical(ctx, padDp).apply {
        background = rounded(SURFACE, 30, ctx)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 0, 0, dp(ctx, 14)) }
    }

    /** Soft inner panel inside a card (where the reference has a photo). */
    fun panel(ctx: Context) = vertical(ctx, 16).apply {
        background = rounded(SOFT, 24, ctx)
    }

    /** Scale down slightly while pressed. Does not consume the touch. */
    @SuppressLint("ClickableViewAccessibility")
    fun pressable(v: View) {
        v.setOnTouchListener { view, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN ->
                    view.animate().scaleX(0.96f).scaleY(0.96f).setDuration(90).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    view.animate().scaleX(1f).scaleY(1f).setDuration(180)
                        .setInterpolator(OvershootInterpolator(2f)).start()
            }
            false
        }
    }

    /** Fade and rise into place. */
    fun enter(v: View, delayMs: Long = 0, rise: Int = 12) {
        v.alpha = 0f
        v.translationY = dp(v.context, rise).toFloat()
        v.animate().alpha(1f).translationY(0f).setStartDelay(delayMs).setDuration(320)
            .setInterpolator(DecelerateInterpolator(1.6f)).start()
    }

    fun chipRow(ctx: Context): Pair<HorizontalScrollView, LinearLayout> {
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        val scroll = HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            addView(row)
        }
        return scroll to row
    }

    fun vertical(ctx: Context, padDp: Int = 0) = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        val p = dp(ctx, padDp)
        setPadding(p, p, p, p)
    }

    fun horizontal(ctx: Context) = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    fun spacer(ctx: Context, hDp: Int) = View(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(ctx, hDp))
    }

    fun grow(v: View): View {
        v.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        return v
    }

    fun fullWidth(v: View, ctx: Context, topDp: Int = 0): View {
        v.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, dp(ctx, topDp), 0, 0) }
        return v
    }
}

/** Lays children out left to right and wraps to the next line (for tags). */
class FlowLayout(ctx: Context, private val gapDp: Int = 6) : ViewGroup(ctx) {
    private val gap get() = Ui.dp(context, gapDp)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val maxW = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        var x = 0; var y = 0; var rowH = 0
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            if (c.visibility == GONE) continue
            measureChild(c, MeasureSpec.makeMeasureSpec(maxW, MeasureSpec.AT_MOST), heightMeasureSpec)
            if (x > 0 && x + c.measuredWidth > maxW) { x = 0; y += rowH + gap; rowH = 0 }
            x += c.measuredWidth + gap
            rowH = maxOf(rowH, c.measuredHeight)
        }
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), y + rowH + paddingTop + paddingBottom)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val maxW = r - l - paddingLeft - paddingRight
        var x = 0; var y = 0; var rowH = 0
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            if (c.visibility == GONE) continue
            if (x > 0 && x + c.measuredWidth > maxW) { x = 0; y += rowH + gap; rowH = 0 }
            c.layout(paddingLeft + x, paddingTop + y, paddingLeft + x + c.measuredWidth, paddingTop + y + c.measuredHeight)
            x += c.measuredWidth + gap
            rowH = maxOf(rowH, c.measuredHeight)
        }
    }

    override fun generateDefaultLayoutParams() = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
}
