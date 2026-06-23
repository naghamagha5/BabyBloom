package com.babybloom.util

import java.text.NumberFormat
import java.util.Locale

private val arabicIntegerFormatter: NumberFormat =
    NumberFormat.getIntegerInstance(Locale.forLanguageTag("ar")).apply {
        isGroupingUsed = false
    }

fun Int.toArabicDigits(): String = arabicIntegerFormatter.format(this)
