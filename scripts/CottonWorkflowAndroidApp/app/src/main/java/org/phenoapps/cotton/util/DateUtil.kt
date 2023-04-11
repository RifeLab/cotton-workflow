package org.phenoapps.cotton.util

import java.text.SimpleDateFormat
import java.util.*

class DateUtil {

    companion object {

        private const val TIME_FORMAT_PATTERN = "yyyy-MM-dd-HH-mm-ss"

        fun Long.toDateString(): String {

            val formatter = SimpleDateFormat(TIME_FORMAT_PATTERN, Locale.getDefault())

            return formatter.format(this)
        }
    }
}