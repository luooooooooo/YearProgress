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
import kotlin.math.ceil

/**
 * 「365 DOTS」桌面小组件（桌面 / 负一屏均可添加）。
 * 点阵 + 年份 + 百分比 + 天数统计，全部由系统当前时间实时推导：
 * 跨过 0 点自动加一天，跨年自动把年份换成新一年。
 */
class YearProgressWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            updateWidget(context, appWidgetManager, id)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // 日期切换 / 系统时间变更时立即刷新，保证凌晨自动加一天
        val action = intent.action
        if (action == Intent.ACTION_DATE_CHANGED || action == Intent.ACTION_TIME_CHANGED ||
            action == YearPrefs.ACTION_SETTINGS_CHANGED
        ) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, YearProgressWidgetProvider::class.java))
            onUpdate(context, manager, ids)
        }
    }

    private fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_year_progress)

        val now = Calendar.getInstance()
        val year = now.get(Calendar.YEAR)
        val dayOfYear = now.get(Calendar.DAY_OF_YEAR)
        val total = 365

        val secondsToday = now.get(Calendar.HOUR_OF_DAY) * 3600L +
                now.get(Calendar.MINUTE) * 60L +
                now.get(Calendar.SECOND)
        val fraction = secondsToday / 86400.0
        val elapsed = (dayOfYear - 1) + fraction
        val percent = (elapsed / total * 100.0).toInt().coerceIn(0, 100)

        views.setTextViewText(R.id.wYear, year.toString())
        views.setTextViewText(R.id.wPercent, "${percent}%")

        // 设置项：点阵颜色（含百分比文字同色）、进度显示方式（已过/剩余）；小组件展示年度进度
        val color = YearPrefs.dotColor(context, YearPrefs.MODE_YEAR)
        val mode = YearPrefs.progressMode(context, YearPrefs.MODE_YEAR)
        val daysText = when (mode) {
            YearPrefs.MODE_REMAINING -> "剩余 ${total - dayOfYear}/$total"
            else -> "已过 $dayOfYear/$total"
        }
        views.setTextViewText(R.id.wDays, daysText)
        views.setTextColor(R.id.wPercent, color)

        // 按小组件实际尺寸计算点阵区域比例，位图同比例生成正圆点（fitCenter 无拉伸）
        val opts = manager.getAppWidgetOptions(widgetId)
        val minW = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        val minH = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
        val innerW = minW - 24
        val innerH = minH - 24
        var areaAspect = 1.34f
        if (innerW > 0 && innerH > 0) {
            // 4x2 卡片点阵区域宽高比约 1.4；过宽/过窄的取值视为无效，回退默认
            val a = innerW * 1.4f / 2.0f / innerH
            if (a in 1.0f..1.8f) areaAspect = a
        }
        android.util.Log.d("YearWidget", "opt minW=$minW minH=$minH areaAspect=$areaAspect")

        views.setImageViewBitmap(R.id.wDots, buildDotsBitmap(dayOfYear, total, areaAspect, color))

        // 点击小组件打开应用
        val openIntent = Intent(context, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, pi)

        manager.updateAppWidget(widgetId, views)
    }

    /** 把 365 个点画成位图：红色实心 = 已过天数，灰色空心 = 剩余天数。
     *  31 列 × 12 行横向长条网格；位图宽高比 = 点阵显示区域宽高比，
     *  配合 fitCenter 显示时圆点保持正圆、不被拉伸。
     *  点径 = 列宽（横向相邻点几乎相接），行距按区域比例自然拉开。
     *  位图像素控制小，避免超 Binder 1MB 传输上限导致 "Can't load widget"。 */
    private fun buildDotsBitmap(filled: Int, total: Int, areaAspect: Float, filledColor: Int): Bitmap {
        val cols = 31
        val rows = 12
        val cellW = 16f
        // 位图宽高比 = cols*cellW / (rows*cellH) = areaAspect => cellH 由此推出
        val cellH = cellW * cols / (rows * areaAspect)
        val w = (cols * cellW).toInt()
        val h = (rows * cellH).toInt()
        // 点径 = 列宽的一半（横向相邻点相切），点阵更饱满
        val dotR = cellW * 0.5f

        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val filledPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = filledColor }
        val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE0E0E0.toInt() }

        var idx = 0
        outer@ for (r in 0 until rows) {
            for (c in 0 until cols) {
                if (idx >= total) break@outer
                val cx = c * cellW + cellW / 2f
                val cy = r * cellH + cellH / 2f
                canvas.drawCircle(cx, cy, dotR, if (idx < filled) filledPaint else emptyPaint)
                idx++
            }
        }
        return bmp
    }
}
