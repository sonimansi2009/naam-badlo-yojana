package org.naambadlo.keypad

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.provider.Settings
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator

/**
 * The seal, with a slow iridescent sheen (cyan, violet, mint: a chameleon's colours)
 * that sweeps across the line art only, then rests, on a loop.
 * Respects the phone's "remove animations" setting.
 */
class SheenImageView(ctx: Context, resId: Int) : View(ctx) {
    private val bmp: Bitmap = BitmapFactory.decodeResource(resources, resId)
    private val src = Rect(0, 0, bmp.width, bmp.height)
    private val dst = RectF()
    private val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
    }
    private val shaderMatrix = Matrix()
    private var progress = -1f
    private var animator: ValueAnimator? = null

    private val animationsOff: Boolean
        get() = runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        dst.set(0f, 0f, w.toFloat(), h.toFloat())
        val c = intArrayOf(0x00FFFFFF, 0x0080D8FF, 0xE680D8FF.toInt(), 0xE6B48CFF.toInt(), 0xE67DFFB2.toInt(), 0x007DFFB2, 0x00FFFFFF)
        val pos = floatArrayOf(0f, 0.30f, 0.42f, 0.5f, 0.58f, 0.70f, 1f)
        sheenPaint.shader = LinearGradient(0f, 0f, w.toFloat(), h * 0.55f, c, pos, Shader.TileMode.CLAMP)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (animationsOff) return
        animator = ValueAnimator.ofFloat(-1.2f, 1.2f, 1.2f).apply {   // sweep, then a quiet pause
            duration = 6500
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            startDelay = 600
            addUpdateListener { progress = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel(); animator = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val layer = canvas.saveLayer(dst, null)
        canvas.drawBitmap(bmp, src, dst, bmpPaint)
        if (progress > -1.15f && progress < 1.15f) {
            shaderMatrix.setTranslate(progress * width, progress * height * 0.55f)
            sheenPaint.shader?.setLocalMatrix(shaderMatrix)
            canvas.drawRect(dst, sheenPaint)
        }
        canvas.restoreToCount(layer)
    }
}
