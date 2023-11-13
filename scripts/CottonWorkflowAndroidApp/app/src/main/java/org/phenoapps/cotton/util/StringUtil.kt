package org.phenoapps.cotton.util

class StringUtil {

    companion object {

        fun String.sanitizeCsv() = "\"${this.replace("\"", "\"\"")}\""

    }
}