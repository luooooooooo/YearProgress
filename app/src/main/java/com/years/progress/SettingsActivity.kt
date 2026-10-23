package com.years.progress

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * 设置页（年度 / 人生双模式，各设置项独立存储）：
 *  - 进度类型：年度进度 / 人生进度 切换
 *  - 人生专属：出生日期（默认今天）、预计寿命（[当前年龄,800]，手输+滑动）、
 *    减去睡觉/上班/低质量时间 三个开关
 *  - 点阵颜色：12 色（按当前模式独立存储）
 *  - 进度显示：已过 / 剩余（按当前模式独立存储）
 * 选择即自动保存，并广播通知桌面小组件同步刷新。
 */
class SettingsActivity : Activity() {

    private lateinit var modeTypeRow: LinearLayout
    private lateinit var lifeSection: LinearLayout
    private lateinit var birthVal: TextView
    private lateinit var expectVal: TextView
    private lateinit var swSleep: Switch
    private lateinit var swWork: Switch
    private lateinit var swLowq: Switch
    private lateinit var colorGrid: LinearLayout
    private lateinit var colorHint: TextView
    private lateinit var modeRow: LinearLayout
    private lateinit var progressHint: TextView
    private lateinit var previewLine: TextView

    private var mode = YearPrefs.MODE_YEAR
    private var selectedColor = YearPrefs.DEFAULT_COLOR_YEAR
    private var selectedProgressMode = YearPrefs.MODE_REMAINING
    private var birthdayMillis = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        modeTypeRow = findViewById(R.id.modeTypeRow)
        lifeSection = findViewById(R.id.lifeSection)
        birthVal = findViewById(R.id.birthVal)
        expectVal = findViewById(R.id.expectVal)
        swSleep = findViewById(R.id.swSleep)
        swWork = findViewById(R.id.swWork)
        swLowq = findViewById(R.id.swLowq)
        colorGrid = findViewById(R.id.colorGrid)
        colorHint = findViewById(R.id.colorHint)
        modeRow = findViewById(R.id.modeRow)
        progressHint = findViewById(R.id.progressHint)
        previewLine = findViewById(R.id.previewLine)

        mode = YearPrefs.mode(this)
        birthdayMillis = YearPrefs.birthday(this)
        selectedColor = YearPrefs.dotColor(this, mode)
        selectedProgressMode = YearPrefs.progressMode(this, mode)

        swSleep.isChecked = YearPrefs.dedSleep(this)
        swWork.isChecked = YearPrefs.dedWork(this)
        swLowq.isChecked = YearPrefs.dedLowq(this)

        buildModeTypeRow()
        buildBirthRow()
        buildExpectRow()
        bindSwitches()
        renderLifeSection()
        buildColorGrid()
        buildModeRow()
        refreshPreview()
    }

    // ===== 进度类型切换 =====

    private fun buildModeTypeRow() {
        modeTypeRow.removeAllViews()
        val density = resources.displayMetrics.density
        val options = listOf(
            Pair(YearPrefs.MODE_YEAR, "年度进度"),
            Pair(YearPrefs.MODE_LIFE, "人生进度")
        )
        for ((m, label) in options) {
            val btn = TextView(this).apply {
                text = label
                textSize = 15f
                gravity = Gravity.CENTER
                val lp = LinearLayout.LayoutParams(0, (48 * density).toInt(), 1f)
                lp.marginEnd = (10 * density).toInt()
                layoutParams = lp
            }
            btn.setOnClickListener {
                mode = m
                YearPrefs.saveMode(this, mode)
                selectedColor = YearPrefs.dotColor(this, mode)
                selectedProgressMode = YearPrefs.progressMode(this, mode)
                birthdayMillis = YearPrefs.birthday(this)
                swSleep.isChecked = YearPrefs.dedSleep(this)
                swWork.isChecked = YearPrefs.dedWork(this)
                swLowq.isChecked = YearPrefs.dedLowq(this)
                buildModeTypeRow()
                renderLifeSection()
                buildColorGrid()
                buildModeRow()
                refreshPreview()
            }
            val selected = m == mode
            val d = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = (12 * density).toInt().toFloat()
                setColor(if (selected) 0xFF202020.toInt() else 0xFFFFFFFF.toInt())
                setStroke((1 * density).toInt(), 0xFFBDBDBD.toInt())
            }
            btn.background = d
            btn.setTextColor(if (selected) Color.WHITE else 0xFF424242.toInt())
            modeTypeRow.addView(btn)
        }
    }

    private fun renderLifeSection() {
        lifeSection.visibility = if (mode == YearPrefs.MODE_LIFE) View.VISIBLE else View.GONE
        updateBirthVal()
        updateExpectVal()
    }

    // ===== 出生日期 =====

    private fun buildBirthRow() {
        val row = findViewById<LinearLayout>(R.id.birthRow)
        row.setOnClickListener { openBirthPicker() }
    }

    private fun openBirthPicker() {
        val c = Calendar.getInstance().apply { timeInMillis = birthdayMillis }
        val now = Calendar.getInstance()
        val density = resources.displayMetrics.density
        val dp = { v: Int -> (v * density).toInt() }

        // 年/月/日三列数字滚轮，年份可直接滑到任意一年
        fun makeCol(title: String): Pair<LinearLayout, NumberPicker> {
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
            }
            col.addView(TextView(this).apply {
                text = title
                textSize = 14f
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(dp(90), dp(34)))
            val np = NumberPicker(this).apply {
                minValue = 1
                maxValue = 31
                wrapSelectorWheel = false
            }
            col.addView(np, LinearLayout.LayoutParams(dp(90), dp(150)))
            return col to np
        }

        val (colY, npYear) = makeCol("年")
        val (colM, npMonth) = makeCol("月")
        val (colD, npDay) = makeCol("日")

        npYear.minValue = 1900
        npYear.maxValue = now.get(Calendar.YEAR)
        npYear.value = c.get(Calendar.YEAR)
        npMonth.value = c.get(Calendar.MONTH) + 1
        npDay.value = c.get(Calendar.DAY_OF_MONTH)

        // 年/月变化时联动当月最大天数，避免 2月30日 这类非法日期
        fun updateDayMax() {
            val cal = Calendar.getInstance().apply {
                clear()
                set(npYear.value, npMonth.value - 1, 1)
            }
            npDay.maxValue = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
            if (npDay.value > npDay.maxValue) npDay.value = npDay.maxValue
        }
        npYear.setOnValueChangedListener { _, _, _ -> updateDayMax() }
        npMonth.setOnValueChangedListener { _, _, _ -> updateDayMax() }
        updateDayMax()

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        row.addView(colY, LinearLayout.LayoutParams(dp(96), LinearLayout.LayoutParams.WRAP_CONTENT))
        row.addView(colM, LinearLayout.LayoutParams(dp(96), LinearLayout.LayoutParams.WRAP_CONTENT))
        row.addView(colD, LinearLayout.LayoutParams(dp(96), LinearLayout.LayoutParams.WRAP_CONTENT))

        AlertDialog.Builder(this)
            .setTitle("选择出生日期")
            .setView(row)
            .setPositiveButton("确定") { _, _ ->
                val chosen = Calendar.getInstance().apply {
                    clear()
                    set(npYear.value, npMonth.value - 1, npDay.value)
                }
                var t = chosen.timeInMillis
                if (t > System.currentTimeMillis()) {
                    // 不允许晚于今天：截断到今天并提示
                    t = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    Toast.makeText(this, "出生日期不能晚于今天，已调整为今天", Toast.LENGTH_SHORT).show()
                }
                birthdayMillis = t
                YearPrefs.saveBirthday(this, birthdayMillis)
                updateBirthVal()
                refreshPreview()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun updateBirthVal() {
        val fmt = SimpleDateFormat("yyyy年M月d日", Locale.getDefault())
        birthVal.text = fmt.format(Calendar.getInstance().apply { timeInMillis = birthdayMillis }.time)
    }

    // ===== 预计寿命（手输 + 滑动，范围 [当前年龄, 800]） =====

    private fun buildExpectRow() {
        val row = findViewById<LinearLayout>(R.id.expectRow)
        row.setOnClickListener { openExpectPicker() }
    }

    private fun openExpectPicker() {
        val age = calcAge(YearPrefs.birthday(this), Calendar.getInstance())
        val minVal = floor(age).toInt().coerceAtLeast(1)
        val cur = YearPrefs.expect(this).coerceIn(minVal, YearPrefs.MAX_EXPECT)

        val density = resources.displayMetrics.density
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((24 * density).toInt(), (16 * density).toInt(), (24 * density).toInt(), 0)
        }

        val input = EditText(this).apply {
            hint = "手输预计寿命（$minVal ~ ${YearPrefs.MAX_EXPECT} 岁）"
            inputType = InputType.TYPE_CLASS_NUMBER
            textSize = 16f
            setText(cur.toString())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        content.addView(input)

        val picker = NumberPicker(this).apply {
            minValue = minVal
            maxValue = YearPrefs.MAX_EXPECT
            value = cur
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        content.addView(picker)

        AlertDialog.Builder(this)
            .setTitle("预计寿命")
            .setMessage("最低为当前年龄 $minVal 岁，最高 800 岁")
            .setView(content)
            .setPositiveButton("确定") { _, _ ->
                val parsed = input.text.toString().trim().toIntOrNull()
                val value = parsed ?: picker.value
                YearPrefs.saveExpect(this, value.coerceIn(minVal, YearPrefs.MAX_EXPECT))
                updateExpectVal()
                refreshPreview()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun updateExpectVal() {
        expectVal.text = "${YearPrefs.expect(this)}岁"
    }

    // ===== 有效时间开关 =====

    private fun bindSwitches() {
        fun saveAll() {
            YearPrefs.saveDeduction(this, swSleep.isChecked, swWork.isChecked, swLowq.isChecked)
            refreshPreview()
            YearPrefs.notifyWidget(this)
        }
        swSleep.setOnCheckedChangeListener { _, _ -> saveAll() }
        swWork.setOnCheckedChangeListener { _, _ -> saveAll() }
        swLowq.setOnCheckedChangeListener { _, _ -> saveAll() }
    }

    // ===== 点阵颜色（按模式独立） =====

    private fun buildColorGrid() {
        colorGrid.removeAllViews()
        val density = resources.displayMetrics.density
        val perRow = 4
        val modeName = if (mode == YearPrefs.MODE_LIFE) "人生进度" else "年度进度"
        colorHint.text = "选择后，App 和桌面小组件的点阵与百分比文字会同步变色（当前为「$modeName」的颜色，两种进度独立调节）"

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
            YearPrefs.save(this, mode, selectedColor, selectedProgressMode)
            YearPrefs.notifyWidget(this)
            buildColorGrid()
            refreshPreview()
        }
        return cell
    }

    // ===== 进度显示（按模式独立） =====

    private fun buildModeRow() {
        modeRow.removeAllViews()
        val density = resources.displayMetrics.density
        val isYear = mode == YearPrefs.MODE_YEAR
        val unit = if (isYear) "天" else "年"
        progressHint.text = if (isYear)
            "选择进度行显示「已过多少天」还是「剩余多少天」（与人生进度独立）"
        else
            "选择进度行显示「已过多少年」还是「剩余多少年」（与年度进度独立）"

        val modes = listOf(
            Pair(YearPrefs.MODE_ELAPSED, "已过多少$unit"),
            Pair(YearPrefs.MODE_REMAINING, "剩余多少$unit")
        )
        for ((pm, label) in modes) {
            val btn = TextView(this).apply {
                text = label
                textSize = 15f
                gravity = Gravity.CENTER
                val lp = LinearLayout.LayoutParams(0, (48 * density).toInt(), 1f)
                lp.marginEnd = (10 * density).toInt()
                layoutParams = lp
            }
            btn.setOnClickListener {
                selectedProgressMode = pm
                YearPrefs.save(this, mode, selectedColor, selectedProgressMode)
                YearPrefs.notifyWidget(this)
                buildModeRow()
                refreshPreview()
            }
            val selected = pm == selectedProgressMode
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

    // ===== 预览 =====

    private fun refreshPreview() {
        val now = Calendar.getInstance()
        previewLine.setTextColor(selectedColor)
        if (mode == YearPrefs.MODE_LIFE) {
            val age = calcAge(birthdayMillis, now)
            val expect = YearPrefs.expect(this)
            // 口径与主界面一致：扣减基于真实剩余（睡1/3、班1/3、低15%，可叠加）；
            // 已过 = 年龄 + 扣减总额；有效剩余 = 剩余 − 扣减之和
            val remain = (expect - age).coerceAtLeast(0.0)
            val ded = (if (swSleep.isChecked) remain / 3.0 else 0.0) +
                    (if (swWork.isChecked) remain / 3.0 else 0.0) +
                    (if (swLowq.isChecked) remain * 0.15 else 0.0)
            val effRemain = (remain - ded).coerceAtLeast(0.0)
            val passed = age + ded
            previewLine.text = when (selectedProgressMode) {
                YearPrefs.MODE_REMAINING ->
                    "预览：进度 剩余 ${String.format(Locale.US, "%.2f", effRemain)} 年 / $expect 年"
                else ->
                    "预览：进度 已过 ${String.format(Locale.US, "%.2f", passed)} 年 / $expect 年"
            }
        } else {
            val day = now.get(Calendar.DAY_OF_YEAR)
            val total = 365
            previewLine.text = when (selectedProgressMode) {
                YearPrefs.MODE_REMAINING -> "预览：进度 剩余 ${total - day} 天 / $total 天"
                else -> "预览：进度 已过 $day 天 / $total 天"
            }
        }
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
