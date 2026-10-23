package com.years.progress

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.View.MeasureSpec
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * 自绘“进度点阵”：红色实心点表示已经过的天数，浅灰空心点表示剩余天数。
 * 365（或闰年 366）个点自动按屏幕宽度排成网格。
 */
class ProgressDotView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var filled = 0
    private var total = 365

    private val filledPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE53935.toInt() }
    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE0E0E0.toInt() }

    fun setProgress(filled: Int, total: Int) {
        this.filled = filled.coerceIn(0, total)
        this.total = total.coerceAtLeast(1)
        requestLayout()
        invalidate()
    }

    /** 设置已过天数的点颜色（设置页可改），并重绘。 */
    fun setFilledColor(color: Int) {
        filledPaint.color = color
        invalidate()
    }

    private fun dotSizePx(): Float = 12f * resources.displayMetrics.density

    private fun gapPx(): Float = 8f * resources.displayMetrics.density

    private fun gridStepPx(): Float = dotSizePx() + gapPx()

    /** 根据可用宽度算出点阵网格应占的高度，保证 wrap_content 不被撑满。 */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val pad = 6f * resources.displayMetrics.density
        val availW = max(0f, MeasureSpec.getSize(widthMeasureSpec) - pad * 2)
        val cols = max(1, (availW / gridStepPx()).toInt())
        val rows = ceil(total.toDouble() / cols).toInt()
        val wantedH = (rows * gridStepPx() - gapPx()).toInt()

        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val finalH = when (heightMode) {
            MeasureSpec.EXACTLY -> MeasureSpec.getSize(heightMeasureSpec)
            MeasureSpec.AT_MOST -> min(wantedH, MeasureSpec.getSize(heightMeasureSpec))
            else -> wantedH
        }
        setMeasuredDimension(
            resolveSize(MeasureSpec.getSize(widthMeasureSpec), widthMeasureSpec),
            max(finalH, 0)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return

        val density = resources.displayMetrics.density
        val dotSize = dotSizePx()
        val gap = gapPx()
        val pad = 6f * density
        val step = dotSize + gap

        val cols = ((width - pad * 2) / step).toInt().coerceAtLeast(1)
        val rows = ceil(total.toDouble() / cols).toInt()

        // 上下垂直居中
        val totalH = rows * step - gap
        val top = ((height - totalH) / 2f).coerceAtLeast(0f)

        var idx = 0
        outer@ for (r in 0 until rows) {
            for (c in 0 until cols) {
                if (idx >= total) break@outer
                val cx = pad + c * step + dotSize / 2f
                val cy = top + r * step + dotSize / 2f
                val paint = if (idx < filled) filledPaint else emptyPaint
                canvas.drawCircle(cx, cy, dotSize / 2f, paint)
                idx++
            }
        }
    }
}
