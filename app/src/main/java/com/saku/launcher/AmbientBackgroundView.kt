package com.saku.launcher

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.SystemClock
import android.provider.Settings
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator

class AmbientBackgroundView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var accent = Color.parseColor("#FFC857")
    private var secondary = Color.parseColor("#66D9E8")
    private var phase = 0.5f
    private var animator: ValueAnimator? = null
    private var animatorStarted = false
    private var motionAllowed = true
    private var lastFrame = 0L

    init {
        val scale = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        )
        motionAllowed = scale > 0f
        if (motionAllowed) {
            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = (14_000 / scale.coerceAtLeast(0.25f)).toLong()
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                interpolator = LinearInterpolator()
                addUpdateListener { value ->
                    phase = value.animatedValue as Float
                    val now = SystemClock.uptimeMillis()
                    // 20 fps cukup untuk gradient yang bergerak lambat; lebih hemat baterai.
                    if (now - lastFrame >= 50) {
                        lastFrame = now
                        invalidate()
                    }
                }
            }
        }
    }

    fun setPalette(newAccent: Int, newSecondary: Int) {
        accent = newAccent
        secondary = newSecondary
        invalidate()
    }

    fun onHostResume(powerSave: Boolean) {
        val shouldAnimate = motionAllowed && !powerSave && animator != null
        if (!shouldAnimate) {
            if (animatorStarted) animator?.pause()
            invalidate()
            return
        }
        if (animatorStarted) {
            animator?.resume()
        } else {
            animator?.start()
            animatorStarted = true
        }
    }

    fun onHostPause() {
        if (animatorStarted) animator?.pause()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.parseColor("#071618"))

        val w = width.toFloat()
        val h = height.toFloat()
        val drift = (phase - 0.5f) * 2f

        drawBlob(canvas, w * (0.18f + 0.06f * drift), h * (0.12f + 0.05f * -drift), w * 0.85f, withAlpha(accent, 0.40f))
        drawBlob(canvas, w * (0.85f + 0.05f * -drift), h * (0.28f + 0.07f * drift), w * 0.78f, withAlpha(secondary, 0.32f))
        drawBlob(canvas, w * (0.48f + 0.08f * drift), h * (0.95f + 0.04f * -drift), w * 0.92f, withAlpha(accent, 0.18f))

        paint.shader = null
        paint.color = Color.parseColor("#99071416")
        canvas.drawRect(0f, h * 0.55f, w, h, paint)
    }

    private fun drawBlob(canvas: Canvas, cx: Float, cy: Float, radius: Float, color: Int) {
        paint.shader = RadialGradient(cx, cy, radius, color, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        paint.color = Color.WHITE
        canvas.drawPaint(paint)
    }

    private fun withAlpha(color: Int, alpha: Float): Int {
        val a = (Color.alpha(color) * alpha).toInt().coerceIn(0, 255)
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animatorStarted = false
        super.onDetachedFromWindow()
    }
}
