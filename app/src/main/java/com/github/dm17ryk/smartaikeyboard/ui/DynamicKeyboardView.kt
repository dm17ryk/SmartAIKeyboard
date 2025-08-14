package com.github.dm17ryk.smartaikeyboard.ui

import android.content.Context
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.github.dm17ryk.smartaikeyboard.layout.KeySpec
import com.github.dm17ryk.smartaikeyboard.layout.KeyboardSpec
import kotlin.math.max

/**
 * Рендер клавиатуры:
 * - Ровные колонки внутри каждого ряда.
 * - Верхний числовой ряд всегда 10 колонок.
 * - Последний буквенный ряд: ⇧ | центр букв | ⌫ (⇧ и ⌫ занимают по 1 колонке).
 * - На каждой буквенной клавише отображается маленький alt-хинт (первый элемент из lp в XML).
 * - Тап: показываем single-превью над клавишей и печатаем основную букву.
 * - Long-press: всплывает полоса вариантов из lp; выбор — горизонтальным движением, коммит по отпусканию.
 */
class DynamicKeyboardView(
    private val context: Context,
    private val onKey: (KeyEvent) -> Unit
) {

    enum class SpecialKey { SHIFT, LANG, SPACE, DELETE, ENTER }
    data class KeyEvent(val text: String? = null, val special: SpecialKey? = null)

    /** Состояние Shift (caps one-shot). Управляет регистром основной метки и вывода. */
    var isShift: Boolean = false
        set(value) {
            field = value
            refreshLabels()
        }

    /** Надпись на кнопке смены языка в нижнем ряду. */
    var langLabel: String = "EN"

    private var root: LinearLayout? = null

    /** Храним ссылки на текстовые метки, чтобы быстро обновлять их при смене Shift. */
    private data class LetterViews(
        val root: FrameLayout,
        val mainLabel: TextView,    // большая метка по центру
        val altLabel: TextView?,    // маленькая метка вверху-справа (может быть null)
        val base: String,           // исходная буква из XML (label)
        val altBase: String?,       // первый элемент из lp (как отображаемый хинт)
        val spec: KeySpec           // сам KeySpec (нужен для набора опций lp)
    )

    private val letterViews = mutableListOf<LetterViews>()
    private val specialButtons = mutableMapOf<SpecialKey, Button>()

    // Превью/меню вариантов
    private val popup = KeyPopup(context)
    private val handler = Handler(Looper.getMainLooper())

    // ---- utils ----
    private fun dp(v: Int) = (context.resources.displayMetrics.density * v).toInt()

    // =====================================================================
    // Public API
    // =====================================================================

    /**
     * Построить вью клавиатуры внутри данного контейнера по спецификации.
     */
    fun buildInto(container: LinearLayout, spec: KeyboardSpec) {
        container.removeAllViews()
        root = container
        letterViews.clear()
        specialButtons.clear()

        val rowTop = dp(6)
        val keyH = dp(48)
        val gap = dp(4)
        val halfGap = gap / 2

        // ---------------- Row 0: числовой (всегда 10 колонок) ----------------
        val numbers = spec.rows.firstOrNull()
        if (numbers != null) {
            val row0 = LinearLayout(context).apply {
                layoutParams = ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, keyH
                )
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            // На цифрах alt-хинт не рисуем и long-press не даём (при необходимости — добавим позже)
            numbers.keys.forEach { k ->
                val cell = makeKeyCell(
                    label = k.label,
                    alt = null,
                    onTap = { onKey(KeyEvent(text = k.label)) },
                    onLong = { null } // нет меню
                )
                val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                    leftMargin = halfGap; rightMargin = halfGap
                }
                row0.addView(cell.root, lp)
                letterViews.add(cell)
            }
            container.addView(row0)
        }

        // ---------------- Буквенные ряды ----------------
        // Для выравнивания задаём "целевое" число колонок на ряд: EN → 10/9/9, RU → 11/11/11
        val isRu = isRuLayout(spec)
        val totalColsRow1 = if (isRu) 11 else 10
        val totalColsRow2 = if (isRu) 11 else 9
        val totalColsRow3 = if (isRu) 11 else 9 // (⇧ + letters + ⌫) суммарно

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
                // Последний буквенный ряд: ⇧ | pad | letters | pad | ⌫  (в сумме targetCols)
                val lettersCount = row.keys.size
                val extra = targetCols - lettersCount - 2
                val padL = max(0, extra / 2)
                val padR = max(0, extra - padL)

                addSpecialColumn(rowView, SpecialKey.SHIFT, "⇧", halfGap)

                repeat(padL) { addSpacer(rowView, halfGap) }
                row.keys.forEach { k ->
                    val cell = makeKeyCell(
                        label = k.label,
                        alt = hintFrom(k),
                        onTap = { onKey(KeyEvent(text = outputFor(k.label))) },
                        onLong = {
                            val opts = optionsFrom(k)
                            if (opts.isEmpty()) null else opts
                        },
                        keySpec = k
                    )
                    val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                        leftMargin = halfGap; rightMargin = halfGap
                    }
                    rowView.addView(cell.root, lp)
                    letterViews.add(cell)
                }
                repeat(padR) { addSpacer(rowView, halfGap) }

                addSpecialColumn(rowView, SpecialKey.DELETE, "⌫", halfGap)
            } else {
                // Обычная буквенная строка: pad | letters | pad  (до targetCols)
                val lettersCount = row.keys.size
                val extra = targetCols - lettersCount
                val padL = max(0, extra / 2)
                val padR = max(0, extra - padL)

                repeat(padL) { addSpacer(rowView, halfGap) }
                row.keys.forEach { k ->
                    val cell = makeKeyCell(
                        label = k.label,
                        alt = hintFrom(k),
                        onTap = { onKey(KeyEvent(text = outputFor(k.label))) },
                        onLong = {
                            val opts = optionsFrom(k)
                            if (opts.isEmpty()) null else opts
                        },
                        keySpec = k
                    )
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

        // ---------------- Нижний служебный ряд ----------------
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

    // =====================================================================
    // Построение ячеек
    // =====================================================================

    /**
     * Создать буквенную/символьную ячейку с фоном‑кнопкой, основной меткой и alt‑хинтом.
     *
     * @param label   основной символ (из XML)
     * @param alt     маленький хинт (первый элемент lp) — может быть null
     * @param onTap   действие по короткому нажатию (обычно вставка label)
     * @param onLong  возвращает список опций для long‑press меню или null/пусто, если меню не нужно
     * @param keySpec исходный KeySpec (храним для обновления меток)
     */
    private fun makeKeyCell(
        label: String,
        alt: String?,
        onTap: () -> Unit,
        onLong: (() -> List<String>?)?,
        keySpec: KeySpec? = null
    ): LetterViews {
        // фон-кнопка (даёт ripple/нажатие)
        val buttonBg = styledButtonBg()
        val cell = FrameLayout(context)

        // основная метка
        val tvMain = TextView(context).apply {
            setTextColor(0xFFFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = Typeface.DEFAULT_BOLD
            text = label
            gravity = Gravity.CENTER
        }

        // alt‑подсказка (верх‑право), синим
        val tvAlt = alt?.let {
            TextView(context).apply {
                setTextColor(0xFF4FC3F7.toInt()) // голубой как в референсе
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                typeface = Typeface.DEFAULT_BOLD
                text = it
            }
        }

        // иерархия
        cell.addView(
            buttonBg,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        cell.addView(
            tvMain,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        tvAlt?.let {
            val lpAlt = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                val m = dp(4); setMargins(m, m, m, m)
            }
            cell.addView(it, lpAlt)
        }

        // touch‑логика: single preview + возможное переключение в multi
        val longPressDelay = 300L
        var longTriggered = false
        var options: List<String> = emptyList()

        val longPressRunnable = Runnable {
            val opts = onLong?.invoke()
            if (opts.isNullOrEmpty()) return@Runnable
            longTriggered = true
            options = opts
            popup.showMulti(cell, options, 0)
        }

        buttonBg.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    longTriggered = false
                    popup.showSingle(cell, outputFor(label))
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
                        popup.getSelected()?.let { onKey(KeyEvent(text = applyShiftIfLetter(it))) }
                    } else {
                        onTap()
                    }
                    popup.hideAll()
                }
            }
            true
        }

        return LetterViews(
            root = cell,
            mainLabel = tvMain,
            altLabel = tvAlt,
            base = label,
            altBase = alt,
            spec = keySpec ?: KeySpec(label)
        )
    }

    // =====================================================================
    // Вспомогательные строители рядов / кнопок
    // =====================================================================

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
        b.minWidth = dp(44) // спец-клавиши не становятся уж слишком узкими
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

    /** Фон‑кнопка без текста — используется внутри LetterViews как кликабельный слой. */
    private fun styledButtonBg(): Button = Button(context).apply {
        text = ""
        setBackgroundColor(0xFF444444.toInt())
        stateListAnimator = null
        setPadding(0, 0, 0, 0)
    }

    // =====================================================================
    // Лейблы и вывод с учётом Shift
    // =====================================================================

    private fun displayMain(text: String): String =
        if (isShift) text.uppercase() else text.lowercase()

    private fun applyShiftIfLetter(s: String): String {
        // Если строка состоит из одной буквы — поднимаем/опускаем регистр по Shift.
        return if (s.length == 1 && s[0].isLetter()) {
            if (isShift) s.uppercase() else s.lowercase()
        } else s
    }

    /** Обновить метки на всех буквенных клавишах (регистр + alt, если он буквенный). */
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

    // =====================================================================
    // Разбор XML-атрибутов lp (универсально для всех языков)
    // =====================================================================

    /** Маленький хинт на клавише — это первый элемент списка lp, если он задан. */
    private fun hintFrom(k: KeySpec): String? =
        k.longPress
            ?.split(',')
            ?.map { it.trim() }
            ?.firstOrNull()
            ?.takeIf { it.isNotEmpty() }

    /** Полный список опций для long‑press — все элементы lp, если заданы. */
    private fun optionsFrom(k: KeySpec): List<String> =
        k.longPress
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()

    // =====================================================================
    // Эвристики компоновки
    // =====================================================================

    /** Простейшая эвристика распознать RU: первая буквенная строка содержит >10 клавиш (11). */
    private fun isRuLayout(spec: KeyboardSpec): Boolean {
        val row1 = spec.rows.getOrNull(1) ?: return false
        return row1.keys.size > 10
    }

    /** Вычислить итоговый вывод для основной буквы с учётом Shift. */
    private fun outputFor(base: String): String =
        if (isShift) base.uppercase() else base
}
