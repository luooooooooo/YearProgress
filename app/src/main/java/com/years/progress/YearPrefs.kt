package com.years.progress

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences

/** 设置项：点阵颜色 + 进度显示方式（已过/剩余）。App 与小组件共用同一份偏好。 */
object YearPrefs {
    const val NAME = "year_progress_settings"
    const val KEY_DOT_COLOR = "dot_color"
    const val KEY_PROGRESS_MODE = "progress_mode"
    const val MODE_ELAPSED = "elapsed"
    const val MODE_REMAINING = "remaining"
    const val DEFAULT_COLOR = 0xFFE53935.toInt()

    const val ACTION_SETTINGS_CHANGED = "com.years.progress.SETTINGS_CHANGED"

    fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun dotColor(ctx: Context): Int =
        prefs(ctx).getInt(KEY_DOT_COLOR, DEFAULT_COLOR)

    fun progressMode(ctx: Context): String =
        prefs(ctx).getString(KEY_PROGRESS_MODE, MODE_ELAPSED) ?: MODE_ELAPSED

    fun save(ctx: Context, color: Int, mode: String) {
        prefs(ctx).edit()
            .putInt(KEY_DOT_COLOR, color)
            .putString(KEY_PROGRESS_MODE, mode)
            .apply()
    }

    /** 通知小组件设置已变更，立即按新设置刷新。 */
    fun notifyWidget(ctx: Context) {
        val intent = Intent(ctx, YearProgressWidgetProvider::class.java)
            .setAction(ACTION_SETTINGS_CHANGED)
        ctx.sendBroadcast(intent)
    }
}

/** 一个颜色选项：名称 + 色值。 */
data class ColorOption(val name: String, val color: Int)

/** 预设 12 色：原红色 + 11 种鲜艳色（替换偏淡的莫兰迪色）。 */
val PRESET_COLORS: List<ColorOption> = listOf(
    ColorOption("原红色", 0xFFE53935.toInt()),
    ColorOption("番茄橙", 0xFFF4511E.toInt()),
    ColorOption("琥珀金", 0xFFFFA000.toInt()),
    ColorOption("草绿色", 0xFF43A047.toInt()),
    ColorOption("翡翠青", 0xFF00BFA5.toInt()),
    ColorOption("天蓝色", 0xFF039BE5.toInt()),
    ColorOption("宝蓝色", 0xFF1565C0.toInt()),
    ColorOption("靛蓝色", 0xFF3949AB.toInt()),
    ColorOption("深紫色", 0xFF5E35B1.toInt()),
    ColorOption("紫罗兰", 0xFF8E24AA.toInt()),
    ColorOption("玫粉色", 0xFFEC407A.toInt()),
    ColorOption("咖啡棕", 0xFF6D4C41.toInt())
)
