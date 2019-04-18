package com.master.contacthelper.extensions

import android.telephony.PhoneNumberUtils
import android.text.TextUtils

fun String.normalizeNumber(): String {
    if (TextUtils.isEmpty(this)) {
        return ""
    }

    val sb = StringBuilder()
    val len = this.length
    for (i in 0 until len) {
        val c = this[i]
        // Character.digit() supports ASCII and Unicode digits (fullwidth, Arabic-Indic, etc.)
        val digit = Character.digit(c, 10)
        if (digit != -1) {
            sb.append(digit)
        } else if (sb.length == 0 && c == '+') {
            sb.append(c)
        } else if (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z') {
            return PhoneNumberUtils.convertKeypadLettersToDigits(this).normalizeNumber()
        }
    }
    return sb.toString()
}

operator fun String.times(x: Int): String {
    val stringBuilder = StringBuilder()
    for (i in 1..x) {
        stringBuilder.append(this)
    }
    return stringBuilder.toString()
}