package com.years.progress

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 年度 / 人生 双模式进度追踪器 —— 单界面。
 * 每秒根据系统当前时间实时重算：
 *  - 年度：已过百分比 / 天数 / 截止时间（跨零点自动 +1，跨年换年份）
 *  - 人生：年龄（10 位小数）/ 已过百分比（按有效寿命）/ 320 季点 / 剩余或已过年数
 * 两种模式的点阵颜色与进度显示方式在设置页独立调节，互不联动。
 */
class MainActivity : Activity() {

    private lateinit var tvTitle: TextView
    private lateinit var tvPercent: TextView
    private lateinit var dotView: ProgressDotView
    private lateinit var tvProgress: TextView
    private lateinit var tvCutoff: TextView

    private val handler = Handler(Looper.getMainLooper())

    /** 每秒刷新一次。 */
    private val ticker = object : Runnable {
        override fun run() {
            update()
            handler.postDelayed(this, 1000L)
        }
    }

    /** 监听日期切换 / 系统时间变更，强制立即刷新，保证凌晨自动加一天。 */
    private val timeChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            update()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvTitle = findViewById(R.id.tvYear)
        tvPercent = findViewById(R.id.tvPercent)
        dotView = findViewById(R.id.dotView)
        tvProgress = findViewById(R.id.tvProgress)
        tvCutoff = findViewById(R.id.tvCutoff)

        // 点击标题 → 进入设置页
        tvTitle.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // 注册日期切换 / 时间变更广播
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIME_TICK)
        }
        registerReceiver(timeChangeReceiver, filter)

        update()
        handler.post(ticker)
    }

    override fun onResume() {
        super.onResume()
        update()
        handler.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacksAndMessages(null)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        runCatching { unregisterReceiver(timeChangeReceiver) }
    }

    private fun update() {
        val now = Calendar.getInstance()
        if (YearPrefs.mode(this) == YearPrefs.MODE_LIFE) {
            updateLife(now)
        } else {
            updateYear(now)
        }
    }

    /** 年度进度：2026年 / 已过百分比 / 365点阵 / 进度行 / 截止时间。 */
    private fun updateYear(now: Calendar) {
        val year = now.get(Calendar.YEAR)
        val dayOfYear = now.get(Calendar.DAY_OF_YEAR)
        val daysInYear = now.getActualMaximum(Calendar.DAY_OF_YEAR)

        val secondsToday = now.get(Calendar.HOUR_OF_DAY) * 3600L +
                now.get(Calendar.MINUTE) * 60L +
                now.get(Calendar.SECOND)
        val fraction = secondsToday / 86400.0
        val elapsedDays = (dayOfYear - 1) + fraction
        val percent = elapsedDays / daysInYear * 100.0

        tvTitle.text = "${year}年"
        tvTitle.textSize = 46f

        val color = YearPrefs.dotColor(this, YearPrefs.MODE_YEAR)
        val mode = YearPrefs.progressMode(this, YearPrefs.MODE_YEAR)

        tvPercent.text = "已过 ${String.format(Locale.US, "%.8f", percent)}%"
        tvPercent.setTextColor(color)

        dotView.setProgress(dayOfYear, daysInYear)
        dotView.setFilledColor(color)

        tvProgress.text = when (mode) {
            YearPrefs.MODE_REMAINING -> "进度 剩余 ${daysInYear - dayOfYear} 天 / $daysInYear 天"
            else -> "进度 已过 $dayOfYear 天 / $daysInYear 天"
        }

        val sdf = SimpleDateFormat("yyyy.MM.dd HH:mm:ss", Locale.getDefault())
        tvCutoff.text = "截止 ${sdf.format(now.time)}"
    }

    /** 人生进度：年龄(10位小数) / 已过百分比(按有效寿命,封顶100%) / 320季点 / 进度行 / 截止时间。
     *  有效寿命 = 预期寿命 − 扣减量之和；扣减量按用户口径：
     *  减去睡觉 = 已过年龄 × 1/3；减去上班 = 剩余时间(预期−年龄) × 1/3；
     *  减去低质量 = 剩余时间 × 15%。 */
    private fun updateLife(now: Calendar) {
        val age = calcAge(YearPrefs.birthday(this), now)
        val expect = YearPrefs.expect(this)
        val remainRaw = expect - age
        val ded = (if (YearPrefs.dedSleep(this)) age / 3.0 else 0.0) +
                (if (YearPrefs.dedWork(this)) remainRaw / 3.0 else 0.0) +
                (if (YearPrefs.dedLowq(this)) remainRaw * 0.15 else 0.0)
        val eff = (expect - ded).coerceAtLeast(0.0001)
        val pct = min(age / eff * 100.0, 100.0)
        val filled = min((age / eff * YearPrefs.LIFE_DOT_TOTAL).roundToInt(), YearPrefs.LIFE_DOT_TOTAL)
        val remain = (eff - age).coerceAtLeast(0.0)

        tvTitle.text = String.format(Locale.US, "%.10f岁", age)
        tvTitle.textSize = 28f

        val color = YearPrefs.dotColor(this, YearPrefs.MODE_LIFE)
        val mode = YearPrefs.progressMode(this, YearPrefs.MODE_LIFE)

        tvPercent.text = String.format(Locale.US, "已过 %.5f%%", pct)
        tvPercent.setTextColor(color)

        dotView.setProgress(filled, YearPrefs.LIFE_DOT_TOTAL)
        dotView.setFilledColor(color)

        tvProgress.text = when (mode) {
            YearPrefs.MODE_REMAINING -> "进度 剩余 ${String.format(Locale.US, "%.2f", remain)} 年 / $expect 年"
            else -> "进度 已过 ${String.format(Locale.US, "%.2f", age)} 年 / $expect 年"
        }

        val sdf = SimpleDateFormat("yyyy.MM.dd HH:mm:ss", Locale.getDefault())
        tvCutoff.text = "截止 ${sdf.format(now.time)}"
    }

    /** 年龄 = 完整年数 + 距最近一次生日经过的时间 / 该生日所在年的实际天数。 */
    private fun calcAge(birthMillis: Long, now: Calendar): Double {
        val b = Calendar.getInstance().apply { timeInMillis = birthMillis }
        val bY = b.get(Calendar.YEAR)
        val bM = b.get(Calendar.MONTH)
        val bD = b.get(Calendar.DAY_OF_MONTH)
        val nowY = now.get(Calendar.YEAR)

        val thisYearBD = Calendar.getInstance().apply {
            clear()
            set(nowY, bM, bD)
        }
        val lastBD: Calendar
        val ageYears: Int
        if (!now.before(thisYearBD)) {
            lastBD = thisYearBD
            ageYears = nowY - bY
        } else {
            lastBD = Calendar.getInstance().apply {
                clear()
                set(nowY - 1, bM, bD)
            }
            ageYears = nowY - bY - 1
        }
        val daysInYear = lastBD.getActualMaximum(Calendar.DAY_OF_YEAR)
        val fraction = (now.timeInMillis - lastBD.timeInMillis) / (daysInYear * 86400000.0)
        return ageYears + fraction
    }
}
