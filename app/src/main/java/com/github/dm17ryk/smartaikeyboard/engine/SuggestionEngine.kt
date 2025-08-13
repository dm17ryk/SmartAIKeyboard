package com.github.dm17ryk.smartaikeyboard.engine

import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.inputmethod.InputConnection

/**
 * Мини-движок подсказок и "умного пробела" (pending space).
 * MVP: генерим кандидатов только при пробеле.
 */
class SuggestionEngine {

    private val handler = Handler(Looper.getMainLooper())
    private var pendingSpace = false
    private var lastJoinContext: Pair<String, String>? = null  // (L, R), чтобы уметь "откатить"

    /** Вызываем при нажатии пробела. */
    fun onSpacePressed(ic: InputConnection?, candidates: (List<Candidate>) -> Unit) {
        if (ic == null) return

        // Ставим pending space (пока без коммита " ")
        pendingSpace = true
        lastJoinContext = null
        candidates(generateCandidates(ic))

        // Через таймаут подтверждаем пробел, если ничего не выбрали/не автосклеили
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({
            if (pendingSpace) {
                ic.commitText(" ", 1)
                pendingSpace = false
            }
        }, 250) // таймаут подтверждения
    }

    /** Вызываем при печати обычной буквы — чтобы скрыть/перестроить кандидатов. */
    fun onCharCommitted(ic: InputConnection?, candidates: (List<Candidate>) -> Unit) {
        if (pendingSpace) {
            // Если печатаем сразу следующую букву — обычно это "L[space]R" -> показать join-подсказку заново.
            candidates(generateCandidates(ic))
        } else {
            candidates(emptyList())
        }
    }

    /** Применить выбранного кандидата. Возвращает true если что-то сделали. */
    fun applyCandidate(ic: InputConnection?, c: Candidate): Boolean {
        if (ic == null) return false
        when (c.type) {
            CandidateType.JOIN -> {
                // Получить L и R вокруг pending space и склеить
                val ctx = extractAround(ic) ?: return false
                val (left, right) = ctx
                val old = "$left $right"
                val joined = left + right
                // Заменяем [left][pending][right] на joined
                replaceAroundPending(ic, left.length, right.length, joined)
                pendingSpace = false
                lastJoinContext = Pair(left, right) // для Undo
                return true
            }
            CandidateType.PERIOD -> {
                ic.commitText(". ", 1)
                pendingSpace = false
                return true
            }
            CandidateType.COMMA -> {
                ic.commitText(", ", 1)
                pendingSpace = false
                return true
            }
            CandidateType.SPACE -> {
                ic.commitText(" ", 1)
                pendingSpace = false
                return true
            }
        }
    }

    /** Возврат склейки (использует lastJoinContext если мы делали JOIN) */
    fun undoJoin(ic: InputConnection?) : Boolean {
        if (ic == null) return false
        val ctx = lastJoinContext ?: return false
        val (left, right) = ctx
        val joined = left + right
        // Удаляем joined справа от курсора и вставляем "left space right"
        // Считаем, что курсор стоит сразу после joined; это верно для нашего applyCandidate.
        ic.deleteSurroundingText(joined.length, 0)
        ic.commitText("$left $right", 1)
        lastJoinContext = null
        return true
    }

    // --------- внутренности ---------

    data class Candidate(val text: String, val type: CandidateType)
    enum class CandidateType { JOIN, PERIOD, COMMA, SPACE }

    private fun generateCandidates(ic: InputConnection?): List<Candidate> {
        val res = mutableListOf<Candidate>()
        val ctx = extractAround(ic)
        if (ctx != null) {
            val (L, R) = ctx
            val insideWord = L.isNotEmpty() && R.isNotEmpty()
                    && L.last().isLetter() && R.first().isLetter()

            if (insideWord) {
                res += Candidate("Склеить: ${L + R}", CandidateType.JOIN)
            }
            res += Candidate(". ", CandidateType.PERIOD)
            res += Candidate(", ", CandidateType.COMMA)
        }
        res += Candidate("␣", CandidateType.SPACE)
        return res
    }

    /** Возвращает пару (L, R) вокруг "воображаемого" пробела. */
    private fun extractAround(ic: InputConnection?): Pair<String, String>? {
        if (ic == null) return null
        val before = ic.getTextBeforeCursor(40, 0) ?: return null
        val after  = ic.getTextAfterCursor(40, 0) ?: return null

        // При pending space курсор у нас стоит в позиции будущего пробела.
        val L = before.toString().takeLastWhile { !it.isWhitespace() }
        val R = after.toString().takeWhile { !it.isWhitespace() }
        return Pair(L, R)
    }

    /** Заменяем [left][pending][right] на newText. */
    private fun replaceAroundPending(ic: InputConnection, leftLen: Int, rightLen: Int, newText: String) {
        // Удаляем left + right, не коммитя пробел, затем коммитим joined
        ic.beginBatchEdit()
        // удаляем left + right слева от курсора (курсор между ними)
        ic.deleteSurroundingText(leftLen, rightLen)
        ic.commitText(newText, 1)
        ic.endBatchEdit()
    }
}

private fun Char.isLetter(): Boolean = Character.isLetter(this)
