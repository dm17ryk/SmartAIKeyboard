package com.github.dm17ryk.smartaikeyboard.ime

import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.LinearLayout
import com.github.dm17ryk.smartaikeyboard.R
import com.github.dm17ryk.smartaikeyboard.layout.KeyboardLoader
import com.github.dm17ryk.smartaikeyboard.ui.DynamicKeyboardView
import com.github.dm17ryk.smartaikeyboard.ui.DynamicKeyboardView.SpecialKey

class LlmKeyboardService : InputMethodService() {

    private var inputView: View? = null
    private lateinit var dyn: DynamicKeyboardView
    private var currentLang: Lang = Lang.EN

    enum class Lang { EN, RU }

    override fun onCreate() {
        super.onCreate()
        dyn = DynamicKeyboardView(this) { ev -> handleKeyEvent(ev) }
    }

    override fun onCreateInputView(): View {
        val view = LayoutInflater.from(this).inflate(R.layout.ime_keyboard, null)
        renderCurrentLayout(view.findViewById(R.id.keyboard_container))
        inputView = view
        return view
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        dyn.isShift = false
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    private fun renderCurrentLayout(container: LinearLayout) {
        val spec = when (currentLang) {
            Lang.EN -> KeyboardLoader.load(this, R.xml.layout_en_qwerty)
            Lang.RU -> KeyboardLoader.load(this, R.xml.layout_ru_jcuken)
        }
        dyn.langLabel = when (currentLang) { Lang.EN -> "EN"; Lang.RU -> "RU" }
        dyn.buildInto(container, spec)
    }

    private fun handleKeyEvent(ev: DynamicKeyboardView.KeyEvent) {
        val ic = currentInputConnection ?: return
        when (ev.special) {
            SpecialKey.SHIFT -> dyn.isShift = !dyn.isShift
            SpecialKey.LANG  -> {
                currentLang = if (currentLang == Lang.EN) Lang.RU else Lang.EN
                dyn.isShift = false
                val container = inputView?.findViewById<LinearLayout>(R.id.keyboard_container) ?: return
                renderCurrentLayout(container)
            }
            SpecialKey.SPACE -> ic.commitText(" ", 1)
            SpecialKey.DELETE -> ic.deleteSurroundingText(1, 0)
            SpecialKey.ENTER -> sendEnter(ic)
            null -> {
                val out = ev.text ?: return
                ic.commitText(out, 1)
                if (dyn.isShift) dyn.isShift = false // одноразовый шифт
            }
        }
    }

    private fun sendEnter(ic: InputConnection) {
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
    }
}
