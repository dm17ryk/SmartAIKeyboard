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

    fun buildInto(container: LinearLayout, spec: KeyboardSpec) {
        container.removeAllViews()
        root = container
        letterButtons.clear()
        specialButtons.clear()

        // Ряды с буквами
        spec.rows.forEachIndexed { _, row ->
            val rowView = LinearLayout(context).apply {
                layoutParams = ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(48)
                ).apply { topMargin = dp(4) }
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            val weight = 1f / max(row.keys.size, 1)
            row.keys.forEach { key ->
                val btn = styledKey()
                btn.text = key.label
                btn.setOnClickListener {
                    val t = if (isShift) key.label.uppercase() else key.label
                    onKey(KeyEvent(text = t))
                }
                val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                rowView.addView(btn, lp)
                letterButtons.add(btn)
            }
            container.addView(rowView)
        }

        // Нижний служебный ряд
        val bottom = LinearLayout(context).apply {
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(52)
            ).apply { topMargin = dp(6) }
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        fun addSpecial(which: SpecialKey, label: String, weight: Float) {
            val b = styledKey()
            b.text = label
            b.setOnClickListener {
                onKey(KeyEvent(special = which))
            }
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight).apply {
                rightMargin = dp(4)
            }
            bottom.addView(b, lp)
            specialButtons[which] = b
        }

        addSpecial(SpecialKey.SHIFT, "⇧", 1f)
        addSpecial(SpecialKey.LANG, langLabel, 1f)
        addSpecial(SpecialKey.SPACE, "␣", 3f)
        addSpecial(SpecialKey.DELETE, "⌫", 1f)
        addSpecial(SpecialKey.ENTER, "↵", 1f)

        container.addView(bottom)

        refreshLabels()
    }

    private fun styledKey(): Button = Button(context).apply {
        setTextColor(0xFFFFFFFF.toInt())
        setBackgroundColor(0xFF444444.toInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        typeface = Typeface.DEFAULT_BOLD
        isAllCaps = false
        stateListAnimator = null // без лифта на нажатии
    }

    private fun refreshLabels() {
        letterButtons.forEach { btn ->
            val t = btn.text?.toString() ?: ""
            btn.text = if (isShift) t.uppercase() else t.lowercase()
        }
        specialButtons[SpecialKey.SHIFT]?.alpha = if (isShift) 1.0f else 0.8f
    }

    private fun dp(v: Int): Int =
        (context.resources.displayMetrics.density * v).toInt()
}
