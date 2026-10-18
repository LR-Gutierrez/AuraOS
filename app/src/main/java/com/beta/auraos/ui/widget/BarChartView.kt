package com.beta.auraos.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.res.ResourcesCompat
import com.beta.auraos.R
import com.google.android.material.color.MaterialColors

/**
 * Revenue bars (Parqueo·Control Analíticas): flat bars with 2dp top corners, the selected one
 * in amber, the rest in the control border color, a hairline and mono labels underneath.
 * Tapping a bar selects it.
 */
class BarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var onBarSelected: ((Int) -> Unit)? = null

    private var values: List<Double> = emptyList()
    private var labels: List<String> = emptyList()
    private var selected = -1

    private val density = resources.displayMetrics.density
    private val chartHeight = 160 * density
    private val labelGap = 8 * density
    private val radius = 2 * density

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hairlinePaint = Paint().apply { strokeWidth = density }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 11 * resources.displayMetrics.scaledDensity
        textAlign = Paint.Align.CENTER
        typeface = ResourcesCompat.getFont(context, R.font.aura_mono)
    }
    private val accent = MaterialColors.getColor(this, R.attr.accentCustom)
    private val idle = MaterialColors.getColor(this, R.attr.borderControlCustom)
    private val path = Path()
    private val rect = RectF()

    init {
        hairlinePaint.color = MaterialColors.getColor(this, R.attr.dividerColorCustom)
        labelPaint.color = MaterialColors.getColor(this, R.attr.textMutedCustom)
        isClickable = true
    }

    fun setData(values: List<Double>, labels: List<String>, selected: Int) {
        this.values = values
        this.labels = labels
        this.selected = selected
        invalidate()
    }

    private fun gap(): Float = (if (values.size > 12) 2 else 6) * density

    private fun barWidth(): Float {
        val n = values.size.coerceAtLeast(1)
        return (width - gap() * (n - 1)) / n
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val h = chartHeight + labelGap + labelPaint.textSize + 4 * density
        setMeasuredDimension(
            getDefaultSize(suggestedMinimumWidth, widthMeasureSpec),
            resolveSize(h.toInt(), heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        if (values.isEmpty()) return
        val max = values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
        val w = barWidth()
        val g = gap()
        values.forEachIndexed { i, v ->
            val h = (v / max * chartHeight).toFloat().coerceAtLeast(chartHeight * 0.03f)
            val left = i * (w + g)
            rect.set(left, chartHeight - h, left + w, chartHeight)
            path.reset()
            path.addRoundRect(rect, floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f), Path.Direction.CW)
            barPaint.color = if (i == selected) accent else idle
            canvas.drawPath(path, barPaint)
        }
        canvas.drawLine(0f, chartHeight + density / 2, width.toFloat(), chartHeight + density / 2, hairlinePaint)
        val baseline = chartHeight + labelGap - labelPaint.ascent()
        labels.forEachIndexed { i, label ->
            if (label.isNotEmpty()) canvas.drawText(label, i * (w + g) + w / 2, baseline, labelPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP && values.isNotEmpty()) {
            val index = (event.x / (barWidth() + gap())).toInt().coerceIn(0, values.size - 1)
            if (index != selected) {
                selected = index
                invalidate()
                onBarSelected?.invoke(index)
            }
            performClick()
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()
}
