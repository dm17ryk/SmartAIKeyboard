package com.github.dm17ryk.smartaikeyboard.ime

import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.Button
import com.github.dm17ryk.smartaikeyboard.R

class LlmKeyboardService : InputMethodService() {

    private var inputView: View? = null

    override fun onCreateInputView(): View {
        // Инфлейтим разметку клавиатуры-заглушки
        val view = LayoutInflater.from(this).inflate(R.layout.ime_keyboard, null)

        val btnSpace = view.findViewById<Button>(R.id.btn_space)
        val btnDel   = view.findViewById<Button>(R.id.btn_delete)
        val btnEnter = view.findViewById<Button>(R.id.btn_enter)

        btnSpace.setOnClickListener {
            currentInputConnection?.commitText(" ", 1)
        }
        btnDel.setOnClickListener {
            // Удаляем символ слева от курсора
            currentInputConnection?.deleteSurroundingText(1, 0)
        }
        btnEnter.setOnClickListener {
            sendEnter(currentInputConnection)
        }

        inputView = view
        return view
    }

    private fun sendEnter(ic: InputConnection?) {
        if (ic == null) return
        // Пробуем отправить "нажатие Enter"
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        // Здесь позже будем настраивать режимы, язык и candidate bar
    }

    override fun onEvaluateFullscreenMode(): Boolean {
        // Не переходим в полноэкранный режим (полезно для ландшафта/чатов)
        return false
    }
}
