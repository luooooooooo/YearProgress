package com.years.progress

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Calendar

/**
 * 设置页：11 种点阵颜色（10 莫兰迪 + 原红色）、进度显示方式（已过/剩余）。
 * 选择即自动保存，并广播通知桌面小组件同步刷新。
 */
class SettingsActivity : Activity() {

    private lateinit var colorGrid: LinearLayout
    private lateinit var modeRow: LinearLayout
    private lateinit var previewLine: TextView

    private var selectedColor = YearPrefs.DEFAULT_COLOR
    private var selectedMode = YearPrefs.MODE_ELAPSED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        colorGrid = findViewById(R.id.colorGrid)
        modeRow = findViewById(R.id.modeRow)
        previewLine = findViewById(R.id.previewLine)

        selectedColor = YearPrefs.dotColor(this)
        selectedMode = YearPrefs.progressMode(this)

        buildColorGrid()
        buildModeRow()
        refreshPreview()
    }

    /** 每行 4 个颜色圆点，自动换行。 */
    private fun buildColorGrid() {
        colorGrid.removeAllViews()
        val density = resources.displayMetrics.density
        val perRow = 4

        for (i in PRESET_COLORS.indices step perRow) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            val end = (i + perRow).coerceAtMost(PRESET_COLORS.size)
            for (j in i until end) {
                row.addView(buildColorCell(PRESET_COLORS[j], density))
            }
            // 补足一行空位，保持对齐
            for (k in end until i + perRow) {
                val spacer = View(this)
                spacer.layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
                row.addView(spacer)
            }
            colorGrid.addView(row)
        }
    }

    private fun buildColorCell(option: ColorOption, density: Float): View {
        val cell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val circleSize = (44 * density).toInt()
        val circle = View(this)
        val lp = LinearLayout.LayoutParams(circleSize, circleSize)
        cell.addView(circle, lp)

        val name = TextView(this).apply {
            text = option.name
            textSize = 12f
            setTextColor(0xFF616161.toInt())
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val nameLp = name.layoutParams as LinearLayout.LayoutParams
        nameLp.topMargin = (6 * density).toInt()
        cell.addView(name)

        fun refreshCircle() {
            val d = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(option.color)
            }
            if (option.color == selectedColor) {
                d.setStroke((3 * density).toInt(), 0xFF202020.toInt())
            }
            circle.background = d
        }
        refreshCircle()

        cell.setOnClickListener {
            selectedColor = option.color
            YearPrefs.save(this@SettingsActivity, selectedColor, selectedMode)
            YearPrefs.notifyWidget(this@SettingsActivity)
            buildColorGrid()
            refreshPreview()
        }
        return cell
    }

    /** 两个选择按钮：已过多少天 / 剩余多少天。 */
    private fun buildModeRow() {
        modeRow.removeAllViews()
        val density = resources.displayMetrics.density
        val modes = listOf(
            Triple(YearPrefs.MODE_ELAPSED, "已过多少天", "进度 已过"),
            Triple(YearPrefs.MODE_REMAINING, "剩余多少天", "进度 剩余")
        )
        for ((mode, label, _) in modes) {
            val btn = TextView(this).apply {
                text = label
                textSize = 15f
                gravity = Gravity.CENTER
                val lp = LinearLayout.LayoutParams(0, (48 * density).toInt(), 1f)
                lp.marginEnd = (10 * density).toInt()
                layoutParams = lp
            }
            btn.setOnClickListener {
                selectedMode = mode
                YearPrefs.save(this@SettingsActivity, selectedColor, selectedMode)
                YearPrefs.notifyWidget(this@SettingsActivity)
                buildModeRow()
                refreshPreview()
            }
            val selected = mode == selectedMode
            val d = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = (12 * density).toInt().toFloat()
                setColor(if (selected) 0xFF202020.toInt() else 0xFFFFFFFF.toInt())
                setStroke((1 * density).toInt(), 0xFFBDBDBD.toInt())
            }
            btn.background = d
            btn.setTextColor(if (selected) Color.WHITE else 0xFF424242.toInt())
            modeRow.addView(btn)
        }
    }

    /** 设置页底部实时预览当前选择的效果。 */
    private fun refreshPreview() {
        val now = Calendar.getInstance()
        val day = now.get(Calendar.DAY_OF_YEAR)
        val total = 365
        val text = when (selectedMode) {
            YearPrefs.MODE_REMAINING -> "进度 剩余 ${total - day} 天 / $total 天"
            else -> "进度 已过 $day 天 / $total 天"
        }
        previewLine.text = text
        previewLine.setTextColor(selectedColor)
    }
}
