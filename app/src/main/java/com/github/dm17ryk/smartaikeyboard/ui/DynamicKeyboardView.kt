package com.github.dm17ryk.smartaikeyboard.ui

import android.content.Context
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.*
import android.widget.*
import com.github.dm17ryk.smartaikeyboard.layout.KeySpec
import com.github.dm17ryk.smartaikeyboard.layout.KeyboardSpec
import kotlin.math.max

class DynamicKeyboardView(
    private val context: Context,
    private val onKey: (KeyEvent) -> Unit
) {
    enum class SpecialKey { SHIFT, LANG, SPACE, DELETE, ENTER }
    data class KeyEvent(val text: String? = null, val special: SpecialKey? = null)

    var isShift = false
        set(value) { field = value; refreshLabels() }

    var langLabel: String = "EN"

    private var root: LinearLayout? = null

    private data class LetterViews(
        val root: FrameLayout,
        val mainLabel: TextView,
        val altLabel: TextView?,
        val base: String,            // исходный символ
        val altBase: String?,        // alt-хинт для показа (может быть null)
        val spec: KeySpec            // ссылка на KeySpec для доступа к lp списку
    )
    private val letterViews = mutableListOf<LetterViews>()
    private val specialButtons = mutableMapOf<SpecialKey, Button>()

    private val popup = KeyPopup(context)
    private val handler = Handler(Looper.getMainLooper())

    private fun dp(v: Int) = (context.resources.displayMetrics.density * v).toInt()

    // ---------- Public API ----------
    fun buildInto(container: LinearLayout, spec: KeyboardSpec) {
        // ... (НИЖЕ полная реализация render — как в предыдущем шаге, укороченная здесь
        container.removeAllViews()
        root = container
        letterViews.clear()
        specialButtons.clear()

        val rowTop = dp(6)
        val keyH = dp(48)
        val gap = dp(4)
        val halfGap = gap / 2

        // Row0 numbers (10)
        val numbers = spec.rows.first()
        val row0 = LinearLayout(context).apply {
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, keyH
            )
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        numbers.keys.forEach { k ->
            val cell = makeKeyCell(k, isRu = false) // alt не показываем у чисел
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                leftMargin = halfGap; rightMargin = halfGap
            }
            row0.addView(cell.root, lp)
            letterViews.add(cell)
        }
        container.addView(row0)

        // Language-specific rows
        val isRu = isRuLayout(spec)
        val totalColsRow1 = if (isRu) 11 else 10
        val totalColsRow2 = if (isRu) 11 else 9
        val totalColsRow3 = if (isRu) 11 else 9

        spec.rows.drop(1).forEachIndexed { idx, row ->
            val targetCols = when (idx) { 0 -> totalColsRow1; 1 -> totalColsRow2; else -> totalColsRow3 }
            val rowView = LinearLayout(context).apply {
                layoutParams = ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, keyH
                ).apply { topMargin = rowTop }
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }

            if (idx == 2) {
                val lettersCount = row.keys.size
                val extra = targetCols - lettersCount - 2
                val padL = max(0, extra / 2)
                val padR = max(0, extra - padL)

                addSpecialColumn(rowView, SpecialKey.SHIFT, "⇧", halfGap)
                repeat(padL) { addSpacer(rowView, halfGap) }
                row.keys.forEach { k ->
                    val cell = makeKeyCell(k, isRu)
                    val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                        leftMargin = halfGap; rightMargin = halfGap
                    }
                    rowView.addView(cell.root, lp)
                    letterViews.add(cell)
                }
                repeat(padR) { addSpacer(rowView, halfGap) }
                addSpecialColumn(rowView, SpecialKey.DELETE, "⌫", halfGap)
            } else {
                val lettersCount = row.keys.size
                val extra = targetCols - lettersCount
                val padL = max(0, extra / 2)
                val padR = max(0, extra - padL)

                repeat(padL) { addSpacer(rowView, halfGap) }
                row.keys.forEach { k ->
                    val cell = makeKeyCell(k, isRu)
                    val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                        leftMargin = halfGap; rightMargin = halfGap
                    }
                    rowView.addView(cell.root, lp)
                    letterViews.add(cell)
                }
                repeat(padR) { addSpacer(rowView, halfGap) }
            }
            container.addView(rowView)
        }

        // bottom row
        val bottom = LinearLayout(context).apply {
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)
            ).apply { topMargin = dp(8) }
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        addSpecialFixed(bottom, SpecialKey.LANG, langLabel, fixedDp = 64, leftM = halfGap, rightM = halfGap)
        addSpecialWeighted(bottom, SpecialKey.SPACE, "␣", 1f, leftM = halfGap, rightM = halfGap)
        addSpecialFixed(bottom, SpecialKey.ENTER, "↵", fixedDp = 64, leftM = halfGap, rightM = halfGap)
        container.addView(bottom)

        refreshLabels()
    }

    // ---------- cells ----------

    private fun makeKeyCell(k: KeySpec, isRu: Boolean): LetterViews {
        val altHint = computeAltHintForDisplay(k, isRu)

        val buttonBg = styledButtonBg()
        val cell = FrameLayout(context)
        val tvMain = TextView(context).apply {
            setTextColor(0xFFFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = Typeface.DEFAULT_BOLD
            text = k.label
            gravity = Gravity.CENTER
        }
        val tvAlt = altHint?.let {
            TextView(context).apply {
                setTextColor(0xFF4FC3F7.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                typeface = Typeface.DEFAULT_BOLD
                text = it
            }
        }

        cell.addView(buttonBg, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))
        cell.addView(tvMain, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))
        tvAlt?.let {
            val lpAlt = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                val m = dp(4)
                setMargins(m, m, m, m)
            }
            cell.addView(it, lpAlt)
        }

        // touch logic: preview + long press menu
        val longPressDelay = 300L
        var longTriggered = false
        var options: List<String> = emptyList()

        val longPressRunnable = Runnable {
            longTriggered = true
            // собрать варианты
            options = buildOptions(k, isRu)
            if (options.isEmpty()) {
                // нет альтернатив — оставим single
                return@Runnable
            }
            popup.showMulti(cell, options, 0)
        }

        buttonBg.setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    longTriggered = false
                    popup.showSingle(cell, outputFor(k.label))
                    handler.postDelayed(longPressRunnable, longPressDelay)
                }
                MotionEvent.ACTION_MOVE -> {
                    if (longTriggered && options.isNotEmpty()) {
                        popup.selectByTouchX(cell, ev.rawX)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(longPressRunnable)
                    if (longTriggered && options.isNotEmpty()) {
                        // commit выбранный вариант
                        popup.getSelected()?.let { onKey(KeyEvent(text = it)) }
                    } else {
                        // короткое нажатие -> базовая буква
                        onKey(KeyEvent(text = outputFor(k.label)))
                    }
                    popup.hideAll()
                }
            }
            true
        }

        return LetterViews(cell, tvMain, tvAlt, base = k.label, altBase = altHint, spec = k)
    }

    private fun buildOptions(k: KeySpec, isRu: Boolean): List<String> {
        // порядок для RU "е": сначала ё/Ё, затем lp, если есть
        val opts = mutableListOf<String>()
        if (isRu && (k.label == "е" || k.label == "Е")) {
            opts += if (isShift) "Ё" else "ё"
        }
        // lp может быть списком через запятую
        k.longPress?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.let { opts.addAll(it) }
        if (isRu && (k.label == "ь" || k.label == "Ь") && !opts.contains(if (isShift) "Ъ" else "ъ")) {
            // добавим ъ/Ъ если его нет в lp
            opts += if (isShift) "Ъ" else "ъ"
        }
        return opts
    }

    // --- misc render helpers (как в твоей текущей версии) ---

    private fun addSpacer(row: LinearLayout, halfGap: Int) {
        val v = View(context)
        val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
            leftMargin = halfGap; rightMargin = halfGap
        }
        row.addView(v, lp)
    }

    private fun addSpecialColumn(row: LinearLayout, which: SpecialKey, label: String, halfGap: Int) {
        val b = styledKey().apply {
            text = label
            setOnClickListener { onKey(KeyEvent(special = which)) }
        }
        b.minWidth = dp(44)
        val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
            leftMargin = halfGap; rightMargin = halfGap
        }
        row.addView(b, lp)
        specialButtons[which] = b
    }

    private fun addSpecialFixed(parent: LinearLayout, which: SpecialKey, label: String, fixedDp: Int, leftM: Int, rightM: Int) {
        val b = styledKey().apply {
            text = label
            setOnClickListener { onKey(KeyEvent(special = which)) }
        }
        val lp = LinearLayout.LayoutParams(dp(fixedDp), ViewGroup.LayoutParams.MATCH_PARENT).apply {
            leftMargin = leftM; rightMargin = rightM
        }
        parent.addView(b, lp)
        specialButtons[which] = b
    }

    private fun addSpecialWeighted(parent: LinearLayout, which: SpecialKey, label: String, weight: Float, leftM: Int, rightM: Int) {
        val b = styledKey().apply {
            text = label
            setOnClickListener { onKey(KeyEvent(special = which)) }
        }
        val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight).apply {
            leftMargin = leftM; rightMargin = rightM
        }
        parent.addView(b, lp)
        specialButtons[which] = b
    }

    private fun styledKey(): Button = Button(context).apply {
        setTextColor(0xFFFFFFFF.toInt())
        setBackgroundColor(0xFF444444.toInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        typeface = Typeface.DEFAULT_BOLD
        isAllCaps = false
        stateListAnimator = null
        setPadding(0, 0, 0, 0)
    }

    private fun styledButtonBg(): Button = Button(context).apply {
        text = ""
        setBackgroundColor(0xFF444444.toInt())
        stateListAnimator = null
        setPadding(0, 0, 0, 0)
    }

    private fun displayMain(text: String): String = if (isShift) text.uppercase() else text.lowercase()

    private fun refreshLabels() {
        letterViews.forEach { v ->
            v.mainLabel.text = displayMain(v.base)
            v.altLabel?.let { altTv ->
                val baseAlt = v.altBase
                if (!baseAlt.isNullOrEmpty() && baseAlt.length == 1 && baseAlt[0].isLetter()) {
                    altTv.text = displayMain(baseAlt)
                }
            }
        }
        specialButtons[SpecialKey.SHIFT]?.alpha = if (isShift) 1.0f else 0.85f
        specialButtons[SpecialKey.LANG]?.text = langLabel
    }

    private fun isRuLayout(spec: KeyboardSpec): Boolean {
        val row1 = spec.rows.getOrNull(1) ?: return false
        return row1.keys.size > 10
    }

    private fun computeAltHintForDisplay(k: KeySpec, isRu: Boolean): String? {
        val explicit = k.longPress?.split(',')?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
        if (!explicit.isNullOrEmpty()) return explicit
        if (isRu) {
            return when (k.label) {
                "е", "Е" -> "ё"
                "ь", "Ь" -> "ъ"
                else -> null
            }
        }
        return null
    }

    private fun outputFor(base: String): String = if (isShift) base.uppercase() else base
}
