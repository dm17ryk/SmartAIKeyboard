package com.github.dm17ryk.smartaikeyboard.ui

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import com.github.dm17ryk.smartaikeyboard.layout.KeyboardSpec
import com.github.dm17ryk.smartaikeyboard.layout.KeySpec
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
    private val letterButtons = mutableListOf<Button>()
    private val specialButtons = mutableMapOf<SpecialKey, Button>()

    private fun dp(v: Int) = (context.resources.displayMetrics.density * v).toInt()

    fun buildInto(container: LinearLayout, spec: KeyboardSpec) {
        container.removeAllViews()
        root = container
        letterButtons.clear()
        specialButtons.clear()

        val rowTop = dp(6)
        val keyH = dp(48)
        val gap = dp(4)
        val halfGap = gap / 2

        // ------- Row 0: Числовой — всегда 10 колонок -------
        val numbers = spec.rows.first()
        val row0 = LinearLayout(context).apply {
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, keyH
            )
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        repeat(10 - numbers.keys.size) { } // просто на всякий — не должны сюда попадать
        numbers.keys.forEach { k ->
            val btn = styledKey()
            btn.text = displayLabel(k.label)
            btn.setOnClickListener { onKey(KeyEvent(text = k.label)) }
            btn.setOnLongClickListener {
                // у чисел альтернативы нет — можно будет добавить позже
                false
            }
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                leftMargin = halfGap; rightMargin = halfGap
            }
            row0.addView(btn, lp)
            letterButtons.add(btn)
        }
        container.addView(row0)

        // ------- Остальные ряды: язык-зависимые сетки -------
        val isRu = isRuLayout(spec)
        val totalColsRow1 = if (isRu) 11 else 10
        val totalColsRow2 = if (isRu) 11 else 9
        val totalColsRow3 = if (isRu) 11 else 9 // учитывая ⇧ и ⌫

        spec.rows.drop(1).forEachIndexed { idx, row ->
            val targetCols = when (idx) {
                0 -> totalColsRow1
                1 -> totalColsRow2
                else -> totalColsRow3
            }

            val rowView = LinearLayout(context).apply {
                layoutParams = ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, keyH
                ).apply { topMargin = rowTop }
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }

            if (idx == 2) {
                // last letters row: ⇧ | pads | letters | pads | ⌫  (в сумме targetCols)
                val lettersCount = row.keys.size
                val extra = targetCols - lettersCount - 2
                val padL = max(0, extra / 2)
                val padR = max(0, extra - padL)

                addSpecialColumn(rowView, SpecialKey.SHIFT, "⇧", halfGap)

                repeat(padL) { addSpacer(rowView, halfGap) }
                row.keys.forEach { k ->
                    addLetterColumn(rowView, k, halfGap)
                }
                repeat(padR) { addSpacer(rowView, halfGap) }

                addSpecialColumn(rowView, SpecialKey.DELETE, "⌫", halfGap)
            } else {
                // обычный ряд: pads | letters | pads  до targetCols
                val lettersCount = row.keys.size
                val extra = targetCols - lettersCount
                val padL = max(0, extra / 2)
                val padR = max(0, extra - padL)

                repeat(padL) { addSpacer(rowView, halfGap) }
                row.keys.forEach { k ->
                    addLetterColumn(rowView, k, halfGap)
                }
                repeat(padR) { addSpacer(rowView, halfGap) }
            }

            container.addView(rowView)
        }

        // Bottom row: LANG | SPACE | ENTER
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

    // ------- helpers: колонки / кнопки -------

    private fun addLetterColumn(row: LinearLayout, k: KeySpec, halfGap: Int) {
        val btn = styledKey()
        btn.text = displayLabel(k.label)
        btn.setOnClickListener {
            val t = if (isShift) k.label.uppercase() else k.label
            onKey(KeyEvent(text = t))
        }
        btn.setOnLongClickListener {
            // 1) XML lp, берём первое из списка "a,b,c"
            var alt: String? = k.longPress?.split(',')?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }

            // 2) спец‑правило: русская Е → Ё (если lp не задан)
            if (alt == null && (k.label == "е" || k.label == "Е")) {
                alt = if (isShift) "Ё" else "ё"
            }

            // 3) регистр для букв
            alt?.let {
                val out = if (it.length == 1 && it[0].isLetter()) {
                    if (isShift) it.uppercase() else it.lowercase()
                } else it
                onKey(KeyEvent(text = out))
                true
            } ?: false
        }
        val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
            leftMargin = halfGap; rightMargin = halfGap
        }
        row.addView(btn, lp)
        letterButtons.add(btn)
    }

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
        b.minWidth = dp(44) // не ужимается меньше разумного, но ширина = 1 колонка
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

    private fun displayLabel(src: String): String = if (isShift) src.uppercase() else src.lowercase()

    private fun refreshLabels() {
        letterButtons.forEach { btn ->
            val t = btn.text?.toString() ?: ""
            btn.text = displayLabel(t)
        }
        specialButtons[SpecialKey.SHIFT]?.alpha = if (isShift) 1.0f else 0.85f
        specialButtons[SpecialKey.LANG]?.text = langLabel
    }

    private fun isRuLayout(spec: KeyboardSpec): Boolean {
        // простая эвристика: если в первой буквенной строке больше 10 ключей — это RU (11)
        val row1 = spec.rows.getOrNull(1) ?: return false
        return row1.keys.size > 10
    }
}
