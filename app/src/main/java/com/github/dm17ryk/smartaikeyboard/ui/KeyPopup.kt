package com.github.dm17ryk.smartaikeyboard.ui

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView

/**
 * Управляет двумя режимами:
 * 1) single preview (один крупный символ)
 * 2) multi preview (полоса вариантов, выбор по x-координате)
 */
class KeyPopup(private val context: Context) {

    // ---- один символ ----
    private var singlePopup: PopupWindow? = null
    private var singleTv: TextView? = null

    fun showSingle(anchor: View, text: String) {
        hideAll()
        val tv = TextView(context).apply {
            setPadding(dp(12), dp(8), dp(12), dp(8))
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFFFFFFFF.toInt())
            background = bubbleBg()
            gravity = Gravity.CENTER
            this.text = text
        }
        val pw = PopupWindow(tv, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, false)
        singlePopup = pw
        singleTv = tv
        val loc = locationAbove(anchor)
        pw.showAtLocation(anchor, Gravity.NO_GRAVITY, loc.first, loc.second)
    }

    fun updateSingle(text: String) {
        singleTv?.text = text
    }

    // ---- несколько вариантов ----
    private var multiPopup: PopupWindow? = null
    private var container: LinearLayout? = null
    private var items: List<TextView> = emptyList()
    private var selectedIndex: Int = 0

    fun showMulti(anchor: View, options: List<String>, initialIndex: Int = 0) {
        hideAll()
        selectedIndex = initialIndex.coerceIn(0, options.lastIndex)

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(6), dp(6), dp(6), dp(6))
            background = bubbleBg()
            gravity = Gravity.CENTER
        }
        items = options.mapIndexed { i, s ->
            TextView(context).apply {
                val padH = dp(12)
                val padV = dp(10)
                setPadding(padH, padV, padH, padV)
                textSize = 26f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(0xFFFFFFFF.toInt())
                text = s
                alpha = if (i == selectedIndex) 1f else 0.65f
                row.addView(this)
            }
        }
        container = row
        val pw = PopupWindow(row, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, false)
        multiPopup = pw
        val loc = locationAbove(anchor)
        pw.showAtLocation(anchor, Gravity.NO_GRAVITY, loc.first, loc.second)
    }

    /** Позвать из onTouch(ACTION_MOVE): отдаёт текущий индекс */
    fun selectByTouchX(anchor: View, rawX: Float): Int {
        val cont = container ?: return selectedIndex
        // определим локальную X в пределах контейнера
        val loc = IntArray(2)
        cont.getLocationOnScreen(loc)
        val x = (rawX - loc[0]).coerceIn(0f, cont.width.toFloat())
        // найти ближайший айтем
        var bestIdx = selectedIndex
        var bestDist = Float.MAX_VALUE
        items.forEachIndexed { idx, tv ->
            val cx = tv.left + tv.width / 2f
            val d = kotlin.math.abs(x - cx)
            if (d < bestDist) {
                bestDist = d
                bestIdx = idx
            }
        }
        // визуал
        items.forEachIndexed { idx, tv -> tv.alpha = if (idx == bestIdx) 1f else 0.65f }
        selectedIndex = bestIdx
        return selectedIndex
    }

    fun getSelected(): String? = items.getOrNull(selectedIndex)?.text?.toString()

    fun hideAll() {
        singlePopup?.dismiss(); singlePopup = null; singleTv = null
        multiPopup?.dismiss(); multiPopup = null; container = null; items = emptyList()
    }

    // ---- helpers ----
    private fun bubbleBg(): GradientDrawable = GradientDrawable().apply {
        setColor(0xFF333333.toInt())
        cornerRadius = dp(8).toFloat()
    }
    private fun dp(v: Int) = (context.resources.displayMetrics.density * v).toInt()

    private fun locationAbove(anchor: View): Pair<Int, Int> {
        val loc = IntArray(2); anchor.getLocationOnScreen(loc)
        val x = loc[0] + anchor.width / 2
        val y = loc[1] - dp(60) // чуть выше
        return x to y
    }
}
