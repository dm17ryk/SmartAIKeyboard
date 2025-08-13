package com.github.dm17ryk.smartaikeyboard.layout

import android.content.Context
import org.xmlpull.v1.XmlPullParser

data class KeySpec(val label: String)
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
                        "key" -> currentRow?.add(KeySpec(label = parser.getAttributeValue(null, "l") ?: ""))
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
