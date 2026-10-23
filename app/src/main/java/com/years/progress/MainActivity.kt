package com.years.progress

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 年度进度追踪器 —— 单界面。
 * 每秒根据系统当前时间实时重算“已过百分比 / 天数 / 截止时间”，
 * 因此在跨过凌晨 0 点时自动跳到下一天（进度自动 +1）。
 */
class MainActivity : Activity() {

    private lateinit var tvYear: TextView
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

        tvYear = findViewById(R.id.tvYear)
        tvPercent = findViewById(R.id.tvPercent)
        dotView = findViewById(R.id.dotView)
        tvProgress = findViewById(R.id.tvProgress)
        tvCutoff = findViewById(R.id.tvCutoff)

        // 点击年份 → 进入设置页
        tvYear.setOnClickListener {
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
        val year = now.get(Calendar.YEAR)
        val dayOfYear = now.get(Calendar.DAY_OF_YEAR)
        // 平年 365，闰年 366
        val daysInYear = now.getActualMaximum(Calendar.DAY_OF_YEAR)

        // 今天已走过的时刻占全天比例
        val secondsToday = now.get(Calendar.HOUR_OF_DAY) * 3600L +
                now.get(Calendar.MINUTE) * 60L +
                now.get(Calendar.SECOND)
        val fraction = secondsToday / 86400.0

        // 已过时间 = (今天是今年的第几天 - 1) + 今天已走的比例
        val elapsedDays = (dayOfYear - 1) + fraction
        val percent = elapsedDays / daysInYear * 100.0

        tvYear.text = "${year}年"

        // 设置项：点阵颜色 + 进度显示方式
        val color = YearPrefs.dotColor(this)
        val mode = YearPrefs.progressMode(this)

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
}
