package com.simplemobiletools.smsmessenger.extensions

import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.widget.TextView

/**
 * Highlights given [highlightText] occurrences using colors of [color] and [secondaryColor].
 * Also allows highlighting one of the occurrences located at [secondaryItemIndex] with a different [secondaryColor]
 *
 * @param highlightText The desired string needs to be highlighted
 *
 * @param color The desired default color for highlighting [highlightText] occurrences
 *
 * @param secondaryColor The desired color for highlighting specific [highlightText] occurrence at given [secondaryItemIndex]
 *
 * @param secondaryItemIndex The index of [highlightText] occurrence wanted to be colored with [secondaryColor] different than the default highlight [color]
 *
 */
fun TextView.multiColorHighlightText(highlightText: String, color: Int, secondaryColor: Int, secondaryItemIndex: Int) {
    val content = text.toString()
    var indexOf = content.indexOf(highlightText, 0, true)
    val wordToSpan = SpannableString(text)
    var offset = 0

    while (offset < content.length && indexOf != -1) {
        indexOf = content.indexOf(highlightText, offset, true)

        if (indexOf == -1) {
            break
        } else {
            val highlightColor = if (indexOf == secondaryItemIndex) {
                secondaryColor
            } else {
                color
            }
            val spanBgColor = BackgroundColorSpan(highlightColor)
            val spanFlag = Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            wordToSpan.setSpan(spanBgColor, indexOf, indexOf + highlightText.length, spanFlag)
            setText(wordToSpan, TextView.BufferType.SPANNABLE)
        }

        offset = indexOf + 1
    }
}
