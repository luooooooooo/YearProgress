package com.years.progress

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import java.util.Calendar

/**
 * 设置项（年度 / 人生两种进度各自独立存储，互不联动）：
 *  - 模式：year（年度进度）/ life（人生进度）
 *  - 点阵颜色：dot_color_year / dot_color_life
 *  - 进度显示方式：progress_mode_year / progress_mode_life
 *  - 人生专属：出生日期、预计寿命、减去睡觉/上班/低质量时间
 * App 与小组件共用同一份偏好。
 */
object YearPrefs {
    const val NAME = "year_progress_settings"

    const val KEY_MODE = "mode"
    const val KEY_DOT_COLOR_YEAR = "dot_color_year"
    const val KEY_DOT_COLOR_LIFE = "dot_color_life"
    const val KEY_PROGRESS_MODE_YEAR = "progress_mode_year"
    const val KEY_PROGRESS_MODE_LIFE = "progress_mode_life"
    const val KEY_BIRTHDAY = "life_birthday"
    const val KEY_EXPECT = "life_expect"
    const val KEY_DED_SLEEP = "life_ded_sleep"
    const val KEY_DED_WORK = "life_ded_work"
    const val KEY_DED_LOWQ = "life_ded_lowq"

    const val MODE_YEAR = "year"
    const val MODE_LIFE = "life"
    const val MODE_ELAPSED = "elapsed"
    const val MODE_REMAINING = "remaining"

    const val DEFAULT_COLOR_YEAR = 0xFFE53935.toInt()
    const val DEFAULT_COLOR_LIFE = 0xFF00BFA5.toInt()
    const val DEFAULT_EXPECT = 80
    const val MAX_EXPECT = 800
    const val LIFE_DOT_TOTAL = 320

    const val ACTION_SETTINGS_CHANGED = "com.years.progress.SETTINGS_CHANGED"

    fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun mode(ctx: Context): String =
        prefs(ctx).getString(KEY_MODE, MODE_YEAR) ?: MODE_YEAR

    fun saveMode(ctx: Context, mode: String) {
        prefs(ctx).edit().putString(KEY_MODE, mode).apply()
    }

    fun dotColor(ctx: Context, mode: String): Int =
        prefs(ctx).getInt(
            if (mode == MODE_LIFE) KEY_DOT_COLOR_LIFE else KEY_DOT_COLOR_YEAR,
            if (mode == MODE_LIFE) DEFAULT_COLOR_LIFE else DEFAULT_COLOR_YEAR
        )

    fun progressMode(ctx: Context, mode: String): String =
        prefs(ctx).getString(
            if (mode == MODE_LIFE) KEY_PROGRESS_MODE_LIFE else KEY_PROGRESS_MODE_YEAR,
            MODE_REMAINING
        ) ?: MODE_REMAINING

    /** 保存当前模式的颜色 + 进度显示方式（按模式独立存储）。 */
    fun save(ctx: Context, mode: String, color: Int, progressMode: String) {
        prefs(ctx).edit()
            .putInt(if (mode == MODE_LIFE) KEY_DOT_COLOR_LIFE else KEY_DOT_COLOR_YEAR, color)
            .putString(if (mode == MODE_LIFE) KEY_PROGRESS_MODE_LIFE else KEY_PROGRESS_MODE_YEAR, progressMode)
            .apply()
    }

    // ===== 人生进度专属 =====

    /** 出生日期（毫秒）。未设置时默认 = 今天。 */
    fun birthday(ctx: Context): Long {
        val saved = prefs(ctx).getLong(KEY_BIRTHDAY, 0L)
        if (saved > 0L) return saved
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        return today.timeInMillis
    }

    fun saveBirthday(ctx: Context, millis: Long) {
        prefs(ctx).edit().putLong(KEY_BIRTHDAY, millis).apply()
    }

    fun expect(ctx: Context): Int =
        prefs(ctx).getInt(KEY_EXPECT, DEFAULT_EXPECT).coerceIn(1, MAX_EXPECT)

    fun saveExpect(ctx: Context, value: Int) {
        prefs(ctx).edit().putInt(KEY_EXPECT, value.coerceIn(1, MAX_EXPECT)).apply()
    }

    fun dedSleep(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_DED_SLEEP, false)
    fun dedWork(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_DED_WORK, false)
    fun dedLowq(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_DED_LOWQ, false)

    fun saveDeduction(ctx: Context, sleep: Boolean, work: Boolean, lowq: Boolean) {
        prefs(ctx).edit()
            .putBoolean(KEY_DED_SLEEP, sleep)
            .putBoolean(KEY_DED_WORK, work)
            .putBoolean(KEY_DED_LOWQ, lowq)
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

/** 预设 12 色：原红色 + 11 种鲜艳色。 */
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
