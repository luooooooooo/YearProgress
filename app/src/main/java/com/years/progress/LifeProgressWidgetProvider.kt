package com.years.progress

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.widget.RemoteViews
import java.util.Calendar
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 「人生进度」桌面小组件（4×2 长方形卡片）。
 * 数据只来自 App 的「人生进度」设置：年龄 / 已过百分比 / 320 点阵（每季度 1 点）/ 进度行。
 * 颜色、进度显示方式、扣减开关均跟随人生设置，不随 App 模式切换变化。
 * 点击小组件打开 App 并自动切换到人生进度。
 */
class LifeProgressWidgetProvider : AppWidgetProvider() {

    companion object {
        /** 320 点 = 80 年 × 每季度 1 点；32 列 × 10 行长条网格（正圆相切）。 */
        const val DOTS_COLS = 32
        const val DOTS_ROWS = 10
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            updateWidget(context, appWidgetManager, id)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action
        if (action == Intent.ACTION_DATE_CHANGED || action == Intent.ACTION_TIME_CHANGED ||
            action == YearPrefs.ACTION_SETTINGS_CHANGED
        ) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, LifeProgressWidgetProvider::class.java))
            onUpdate(context, manager, ids)
        }
    }

    private fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_life_progress)
        val now = Calendar.getInstance()

        val age = YearPrefs.calcAge(YearPrefs.birthday(context), now)
        val expect = YearPrefs.expect(context)
        // 扣减口径与 App 主界面一致：剩余寿命 = 预期−年龄；扣减 = 剩余×比例；有效剩余 = 剩余−扣减之和
        val remain = (expect - age).coerceAtLeast(0.0)
        val ded = (if (YearPrefs.dedSleep(context)) remain / 3.0 else 0.0) +
                (if (YearPrefs.dedWork(context)) remain / 3.0 else 0.0) +
                (if (YearPrefs.dedLowq(context)) remain * 0.15 else 0.0)
        val effRemain = (remain - ded).coerceAtLeast(0.0)
        val effTotal = (age + effRemain).coerceAtLeast(0.0001)
        val pct = min(age / effTotal * 100.0, 100.0)
        val filled = min((age / effTotal * YearPrefs.LIFE_DOT_TOTAL).roundToInt(), YearPrefs.LIFE_DOT_TOTAL)

        views.setTextViewText(R.id.wYear, String.format(Locale.US, "%.2f岁", age))
        views.setTextViewText(R.id.wPercent, "${pct.toInt()}%")

        // 人生设置：点阵颜色（含百分比文字同色）、进度显示方式（已过/剩余）
        val color = YearPrefs.dotColor(context, YearPrefs.MODE_LIFE)
        val mode = YearPrefs.progressMode(context, YearPrefs.MODE_LIFE)
        val daysText = when (mode) {
            YearPrefs.MODE_REMAINING ->
                "剩余${String.format(Locale.US, "%.2f", effRemain)}年"
            else ->
                "已过${String.format(Locale.US, "%.2f", age)}年"
        }
        views.setTextViewText(R.id.wDays, daysText)
        views.setTextColor(R.id.wPercent, color)

        // 按小组件实际尺寸计算点阵区域比例，位图同比例生成正圆点（fitCenter 无拉伸）
        val opts = manager.getAppWidgetOptions(widgetId)
        val minW = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        val minH = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
        val innerW = minW - 24
        val innerH = minH - 24
        var areaAspect = 1.4f
        if (innerW > 0 && innerH > 0) {
            val a = innerW * 1.4f / 2.0f / innerH
            if (a in 1.0f..1.8f) areaAspect = a
        }
        android.util.Log.d("LifeWidget", "opt minW=$minW minH=$minH areaAspect=$areaAspect")

        views.setImageViewBitmap(R.id.wDots, buildDotsBitmap(filled, areaAspect, color))

        // 点击小组件 → 打开 App 并自动切换到人生进度
        val openIntent = Intent(context, MainActivity::class.java)
            .putExtra(YearPrefs.EXTRA_OPEN_MODE, YearPrefs.MODE_LIFE)
        val pi = PendingIntent.getActivity(
            context, 1, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, pi)

        manager.updateAppWidget(widgetId, views)
    }

    /** 把 320 个点画成位图：实心 = 已过（按有效寿命），灰色空心 = 剩余。
     *  32 列 × 10 行横向长条网格；位图宽高比 = 点阵显示区域宽高比，fitCenter 显示时圆点保持正圆。
     *  点径 = 列宽的一半（横向相邻点相切，点阵饱满）。位图体积控制在 1MB 以内。 */
    private fun buildDotsBitmap(filled: Int, areaAspect: Float, filledColor: Int): Bitmap {
        val cols = DOTS_COLS
        val rows = DOTS_ROWS
        val cellW = 16f
        // 位图宽高比 = cols*cellW / (rows*cellH) = areaAspect => cellH 由此推出
        val cellH = cellW * cols / (rows * areaAspect)
        val w = (cols * cellW).toInt()
        val h = (rows * cellH).toInt()
        val dotR = cellW * 0.5f

        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val filledPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = filledColor }
        val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE0E0E0.toInt() }

        var idx = 0
        outer@ for (r in 0 until rows) {
            for (c in 0 until cols) {
                if (idx >= YearPrefs.LIFE_DOT_TOTAL) break@outer
                val cx = c * cellW + cellW / 2f
                val cy = r * cellH + cellH / 2f
                canvas.drawCircle(cx, cy, dotR, if (idx < filled) filledPaint else emptyPaint)
                idx++
            }
        }
        return bmp
    }
}
