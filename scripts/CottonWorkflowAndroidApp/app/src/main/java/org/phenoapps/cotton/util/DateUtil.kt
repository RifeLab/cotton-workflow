package org.phenoapps.cotton.util

import java.text.SimpleDateFormat

class DateUtil {

    companion object {

        fun Long.toDateString(): String {

            val formatter = SimpleDateFormat.getDateTimeInstance()

            return formatter.format(this)
        }
    }
}