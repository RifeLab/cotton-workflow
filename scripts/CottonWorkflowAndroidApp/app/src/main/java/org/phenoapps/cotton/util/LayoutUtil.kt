package org.phenoapps.cotton.util

import android.view.View
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT

class LayoutUtil {

    companion object {

        fun View.shrink() {
            layoutParams = layoutParams.also {
                it.height = 0
            }
        }

        fun View.wrap() {
            layoutParams = layoutParams.also {
                it.height = WRAP_CONTENT
            }
        }
    }
}