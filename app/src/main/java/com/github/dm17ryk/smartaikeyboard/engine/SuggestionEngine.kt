package com.github.dm17ryk.smartaikeyboard.engine

import android.view.inputmethod.InputConnection

/**
 * Заглушка движка подсказок без любой логики "pending space" и JOIN.
 * Оставляем API, чтобы потом легко вернуть функциональность.
 */
class SuggestionEngine {

    data class Candidate(val text: String, val type: CandidateType)
    enum class CandidateType { PERIOD, COMMA, SPACE }

    /** Вызов при нажатии пробела: теперь просто коммитим пробел сразу. */
    fun onSpacePressed(ic: InputConnection?) {
        ic?.commitText(" ", 1)
    }

    /** Вызов при вводе символа (пока без подсказок). */
    fun onCharCommitted(ic: InputConnection?, ch: String) {
        // no-op
    }

    /** Вызов при Delete (пока без спец-логики). */
    fun onDeletePressed(ic: InputConnection?) {
        // no-op
    }

    /** Применение кандидата — ничего не делаем, т.к. кандидатов нет. */
    fun applyCandidate(ic: InputConnection?, c: Candidate): Boolean = false

    /** Undo — нечего отменять. */
    fun undo(): Boolean = false
}
