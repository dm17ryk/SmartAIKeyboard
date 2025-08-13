package com.github.dm17ryk.smartaikeyboard.ime

import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.github.dm17ryk.smartaikeyboard.R
import com.github.dm17ryk.smartaikeyboard.engine.SuggestionEngine

class LlmKeyboardService : InputMethodService() {

    private var inputView: View? = null
    private lateinit var engine: SuggestionEngine

    // UI refs
    private var candidateScroll: View? = null
    private var candidateContainer: LinearLayout? = null
    private var undoChip: TextView? = null

    override fun onCreate() {
        super.onCreate()
        engine = SuggestionEngine()
    }

    override fun onCreateInputView(): View {
        val view = LayoutInflater.from(this).inflate(R.layout.ime_keyboard, null)

        // Кнопки
        val btnSpace = view.findViewById<Button>(R.id.btn_space)
        val btnDel   = view.findViewById<Button>(R.id.btn_delete)
        val btnEnter = view.findViewById<Button>(R.id.btn_enter)

        // Candidate bar
        candidateScroll = view.findViewById(R.id.candidate_scroll)
        candidateContainer = view.findViewById(R.id.candidate_container)
        undoChip = view.findViewById(R.id.undo_chip)

        // Временные буквенные кнопки (RU)
        val letterIds = intArrayOf(
            R.id.btn_ru_k,  // к
            R.id.btn_ru_o1, // о
            R.id.btn_ru_r,  // р
            R.id.btn_ru_e1, // е
            R.id.btn_ru_t,  // т
            R.id.btn_ru_c,  // ч
            R.id.btn_ru_e2, // е
            R.id.btn_ru_p,  // п
            R.id.btn_ru_u,  // у
            R.id.btn_ru_h,  // х
            R.id.btn_ru_o2, // о
            R.id.btn_ru_e3  // е
        )
        letterIds.forEach { id ->
            view.findViewById<Button>(id)?.setOnClickListener { b ->
                val ch = (b as Button).text?.toString() ?: return@setOnClickListener
                currentInputConnection?.commitText(ch, 1)
                // Если была pending-логика пробела — обновим кандидатов под JOIN
                engine.onCharCommitted(currentInputConnection) { list ->
                    showCandidates(list)
                }
            }
        }

        btnSpace.setOnClickListener {
            engine.onSpacePressed(currentInputConnection) { candidates ->
                showCandidates(candidates)
            }
        }
        btnDel.setOnClickListener {
            currentInputConnection?.deleteSurroundingText(1, 0)
            clearCandidates()
        }
        btnEnter.setOnClickListener {
            sendEnter(currentInputConnection)
            clearCandidates()
        }

        inputView = view
        return view
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        clearCandidates()
        hideUndo()
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    // --- Candidate bar ---

    private fun showCandidates(list: List<SuggestionEngine.Candidate>) {
        val container = candidateContainer ?: return
        val scroll = candidateScroll ?: return

        container.removeAllViews()
        if (list.isEmpty()) {
            scroll.visibility = View.GONE
            return
        }
        scroll.visibility = View.VISIBLE

        // Создаем кнопки-кандидаты
        list.forEach { c ->
            val tv = TextView(this).apply {
                text = when (c.type) {
                    SuggestionEngine.CandidateType.JOIN -> c.text
                    SuggestionEngine.CandidateType.PERIOD -> ". "
                    SuggestionEngine.CandidateType.COMMA -> ", "
                    SuggestionEngine.CandidateType.SPACE -> "␣"
                }
                setPadding(20, 6, 20, 6)
                setTextColor(0xFFFFFFFF.toInt())
                setBackgroundColor(0xFF444444.toInt())
                textSize = 14f
                setOnClickListener {
                    val applied = engine.applyCandidate(currentInputConnection, c)
                    if (applied && c.type == SuggestionEngine.CandidateType.JOIN) {
                        showUndo()
                    } else {
                        hideUndo()
                    }
                    clearCandidates()
                }
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            ).apply { rightMargin = 12 }
            container.addView(tv, lp)
        }
    }

    private fun clearCandidates() {
        (candidateContainer)?.removeAllViews()
        (candidateScroll)?.visibility = View.GONE
    }

    // --- Undo chip ---

    private fun showUndo() {
        undoChip?.apply {
            text = "Отменить склейку"
            visibility = View.VISIBLE
            setOnClickListener {
                val ok = engine.undoJoin(currentInputConnection)
                if (ok) {
                    visibility = View.GONE
                }
            }
        }
    }

    private fun hideUndo() {
        undoChip?.visibility = View.GONE
    }

    // --- helpers ---

    private fun sendEnter(ic: InputConnection?) {
        if (ic == null) return
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
    }
}
