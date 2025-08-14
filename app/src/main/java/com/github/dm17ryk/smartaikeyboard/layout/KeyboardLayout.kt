package com.github.dm17ryk.smartaikeyboard.layout

import android.content.Context
import org.xmlpull.v1.XmlPullParser

data class KeySpec(
    val label: String,
    val longPress: String? = null // одно значение или список через запятую — пока берем первое
)
data class RowSpec(val keys: List<KeySpec>)
data class KeyboardSpec(val rows: List<RowSpec>)

object KeyboardLoader {

    fun load(context: Context, xmlResId: Int): KeyboardSpec {
        val rows = mutableListOf<RowSpec>()
        val parser = context.resources.getXml(xmlResId)
        var event = parser.eventType
        var currentRow: MutableList<KeySpec>? = null

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "row" -> currentRow = mutableListOf()
                        "key" -> {
                            val l = parser.getAttributeValue(null, "l") ?: ""
                            val lp = parser.getAttributeValue(null, "lp") // может быть null
                            currentRow?.add(KeySpec(label = l, longPress = lp))
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "row" && currentRow != null) {
                        rows.add(RowSpec(keys = currentRow.toList()))
                        currentRow = null
                    }
                }
            }
            event = parser.next()
        }
        return KeyboardSpec(rows)
    }
}
