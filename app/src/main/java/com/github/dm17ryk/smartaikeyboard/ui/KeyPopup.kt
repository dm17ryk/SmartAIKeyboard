package com.github.dm17ryk.smartaikeyboard.ui

import android.content.Context
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.View.MeasureSpec
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import kotlin.math.max
import kotlin.math.min

class KeyPopup(private val context: Context) {

    private fun dp(v: Int) = (context.resources.displayMetrics.density * v).toInt()
    private fun bubbleBg() = GradientDrawable().apply {
        cornerRadius = dp(8).toFloat()
        setColor(0xFF333333.toInt())
    }

    // ---------- SINGLE ----------
    private var singlePopup: PopupWindow? = null
    private var singleTv: TextView? = null

    fun showSingle(anchor: View, text: String) {
        hideAll()

        val tv = TextView(context).apply {
            this.text = text
            gravity = Gravity.CENTER
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFFFFFFFF.toInt())
            background = bubbleBg()
        }

        // ширина = ширина клавиши, высота = 2 * высоты клавиши (двухэтажный)
        val w = anchor.width
        val h = anchor.height * 2

        val pw = PopupWindow(tv, w, h, false).apply {
            isClippingEnabled = false          // позволяем выходить за пределы окна IME
            isOutsideTouchable = false
            elevation = dp(4).toFloat()
        }
        singlePopup = pw
        singleTv = tv

        // позиционируем ровно над клавишей (через showAsDropDown с отрицательным yOff)
        val xOff = (anchor.width - w) / 2
        val yOff = -(anchor.height + h)
        pw.showAsDropDown(anchor, xOff, yOff)
    }

    fun updateSingle(text: String) { singleTv?.text = text }

    // ---------- MULTI ----------
    private var multiPopup: PopupWindow? = null
    private var optionsRow: LinearLayout? = null
    private var optionViews: List<TextView> = emptyList()
    private var selectedIndex = 0

    fun showMulti(anchor: View, options: List<String>, initialIndex: Int = 0) {
        hideAll()
        selectedIndex = initialIndex.coerceIn(0, options.lastIndex)

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(6), dp(8), dp(6))
            background = bubbleBg()
        }
        optionViews = options.mapIndexed { i, s ->
            TextView(context).apply {
                text = s
                textSize = 26f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(0xFFFFFFFF.toInt())
                val padH = dp(12); val padV = dp(10)
                setPadding(padH, padV, padH, padV)
                alpha = if (i == selectedIndex) 1f else 0.65f
            }.also { row.addView(it) }
        }
        optionsRow = row

        // замеряем контент, ширину не ограничиваем искусственно
        row.measure(
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        )
        val screen = Rect().also { anchor.getWindowVisibleDisplayFrame(it) }
        val maxW = screen.width() - dp(16) // небольшой отступ от краёв
        val w = min(max(row.measuredWidth, anchor.width), maxW)
        val h = (anchor.height * 1.6f).toInt().coerceAtLeast(anchor.height)

        val pw = PopupWindow(row, w, h, false).apply {
            isClippingEnabled = false
            isOutsideTouchable = false
            elevation = dp(4).toFloat()
        }
        multiPopup = pw

        val xOff = (anchor.width - w) / 2
        val yOff = -(anchor.height + h)
        pw.showAsDropDown(anchor, xOff, yOff)
    }

    /** дергаем в MOVE: подсветит ближайший айтем и вернёт индекс */
    fun selectByTouchX(anchor: View, rawX: Float): Int {
        val row = optionsRow ?: return selectedIndex
        val loc = IntArray(2); row.getLocationOnScreen(loc)
        val xLocal = (rawX - loc[0]).coerceIn(0f, row.width.toFloat())

        var best = selectedIndex
        var bestDist = Float.MAX_VALUE
        optionViews.forEachIndexed { idx, tv ->
            val cx = tv.left + tv.width / 2f
            val d = kotlin.math.abs(cx - xLocal)
            if (d < bestDist) { bestDist = d; best = idx }
        }
        optionViews.forEachIndexed { idx, tv -> tv.alpha = if (idx == best) 1f else 0.65f }
        selectedIndex = best
        return best
    }

    fun getSelected(): String? = optionViews.getOrNull(selectedIndex)?.text?.toString()

    fun hideAll() {
        singlePopup?.dismiss(); singlePopup = null; singleTv = null
        multiPopup?.dismiss(); multiPopup = null; optionsRow = null; optionViews = emptyList()
    }
}
