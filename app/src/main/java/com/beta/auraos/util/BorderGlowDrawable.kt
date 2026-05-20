package com.beta.auraos.util

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

class BorderGlowDrawable(
    private val glowColor: Int,
    private val cornerRadiusPx: Float,
) : Drawable() {

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        strokeCap = Paint.Cap.ROUND
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val path = Path()
    private val pathMeasure = PathMeasure()
    private var pathLength = 0f
    private var needsUpdate = true
    private var phase = 0f
    private val posBuffer = FloatArray(2)

    fun setPhase(value: Float) {
        phase = value
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        if (w <= 0 || h <= 0) return

        val inset = borderPaint.strokeWidth / 2f
        val r = minOf(cornerRadiusPx, (w - 2f * inset) / 2f, (h - 2f * inset) / 2f)

        if (needsUpdate) {
            path.rewind()
            path.addRoundRect(inset, inset, w - inset, h - inset, r, r, Path.Direction.CW)
            pathMeasure.setPath(path, false)
            pathLength = pathMeasure.length
            needsUpdate = false
        }

        val baseAlpha = 80
        borderPaint.color = Color.argb(baseAlpha, Color.red(glowColor), Color.green(glowColor), Color.blue(glowColor))
        canvas.drawPath(path, borderPaint)

        val perimeterPos = (phase * pathLength) % pathLength
        val glowRadius = 8f
        val tailDots = 16
        val tailSpacing = 6f

        for (i in tailDots downTo 0) {
            var dist = perimeterPos - i * tailSpacing
            if (dist < 0f) dist += pathLength

            pathMeasure.getPosTan(dist, posBuffer, null)

            val ratio = i.toFloat() / tailDots
            val alpha = (90 * (1f - ratio) + 20).toInt()
            val radius = glowRadius * (0.2f + 0.8f * (1f - ratio))

            glowPaint.color = Color.argb(alpha, Color.red(glowColor), Color.green(glowColor), Color.blue(glowColor))
            canvas.drawCircle(posBuffer[0], posBuffer[1], radius, glowPaint)
        }
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
