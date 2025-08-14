package com.github.dm17ryk.smartaikeyboard.ui

import android.content.Context
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView

class KeyPopup(private val context: Context) {

    // --- single preview ---
    private var singlePopup: PopupWindow? = null
    private var singleTv: TextView? = null

    /** Пузырь по ширине = ширина клавиши, по высоте = 2 * высота клавиши, ровно над ней */
    fun showSingle(anchor: View, text: String) {
        hideAll()

        val tv = TextView(context).apply {
            setPadding(dp(8), dp(4), dp(8), dp(4))
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFFFFFFFF.toInt())
            background = bubbleBg()
            gravity = Gravity.CENTER
            this.text = text
        }

        val w = anchor.width
        val h = anchor.height * 2
        val pw = PopupWindow(tv, w, h, false)
        singlePopup = pw
        singleTv = tv

        val (x, y) = topCenteredOverAnchor(anchor, w, h)
        pw.showAtLocation(anchor, Gravity.NO_GRAVITY, x, y)
    }

    fun updateSingle(text: String) { singleTv?.text = text }

    // --- multi options ---
    private var multiPopup: PopupWindow? = null
    private var container: LinearLayout? = null
    private var items: List<TextView> = emptyList()
    private var selectedIndex: Int = 0

    /** Полоса вариантов. Высота кратна высоте клавиши (чуть меньше single), позиция — над клавишей. */
    fun showMulti(anchor: View, options: List<String>, initialIndex: Int = 0) {
        hideAll()
        selectedIndex = initialIndex.coerceIn(0, options.lastIndex)

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
            background = bubbleBg()
            gravity = Gravity.CENTER
        }
        items = options.mapIndexed { i, s ->
            TextView(context).apply {
                val padH = dp(12); val padV = dp(10)
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

        val w = (options.size * (anchor.width * 0.8f)).toInt().coerceAtLeast(anchor.width)
        val h = (anchor.height * 1.6f).toInt()
        val pw = PopupWindow(row, w, h, false)
        multiPopup = pw

        val (x, y) = topCenteredOverAnchor(anchor, w, h)
        pw.showAtLocation(anchor, Gravity.NO_GRAVITY, x, y)
    }

    /** Позвать при MOVE: обновляет подсветку и возвращает индекс выбранного */
    fun selectByTouchX(anchor: View, rawX: Float): Int {
        val cont = container ?: return selectedIndex
        val loc = IntArray(2); cont.getLocationOnScreen(loc)
        val x = (rawX - loc[0]).coerceIn(0f, cont.width.toFloat())
        var best = selectedIndex; var bestDist = Float.MAX_VALUE
        items.forEachIndexed { idx, tv ->
            val cx = tv.left + tv.width / 2f
            val d = kotlin.math.abs(x - cx)
            if (d < bestDist) { bestDist = d; best = idx }
        }
        items.forEachIndexed { idx, tv -> tv.alpha = if (idx == best) 1f else 0.65f }
        selectedIndex = best
        return best
    }

    fun getSelected(): String? = items.getOrNull(selectedIndex)?.text?.toString()

    fun hideAll() {
        singlePopup?.dismiss(); singlePopup = null; singleTv = null
        multiPopup?.dismiss(); multiPopup = null; container = null; items = emptyList()
    }

    // --- helpers ---
    private fun bubbleBg(): GradientDrawable = GradientDrawable().apply {
        setColor(0xFF333333.toInt()); cornerRadius = dp(8).toFloat()
    }
    private fun dp(v: Int) = (context.resources.displayMetrics.density * v).toInt()

    /** Вычисляем координаты так, чтобы попап был над якорем и не выходил за экран. */
    private fun topCenteredOverAnchor(anchor: View, popupW: Int, popupH: Int): Pair<Int, Int> {
        val loc = IntArray(2); anchor.getLocationOnScreen(loc)
        val anchorLeft = loc[0]; val anchorTop = loc[1]
        var x = anchorLeft + (anchor.width - popupW) / 2
        var y = anchorTop - popupH // верхняя грань попапа ровно над клавишей

        // за рамки экрана — поджать
        val vis = Rect()
        anchor.getWindowVisibleDisplayFrame(vis)
        if (x < vis.left) x = vis.left
        if (x + popupW > vis.right) x = vis.right - popupW
        if (y < vis.top) y = anchorTop - anchor.height // крайний случай — прижать к верхней границе клавиши
        return x to y
    }
}
