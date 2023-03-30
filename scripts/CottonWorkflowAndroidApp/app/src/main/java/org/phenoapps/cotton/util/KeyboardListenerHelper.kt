package org.phenoapps.cotton.util

import android.graphics.Rect
import android.view.View

class KeyboardListenerHelper {

    private var isKeyboardShowing = false

    fun connect(view: View, function: (Boolean) -> Unit) {

        // ContentView is the root view of the layout of this activity/fragment
        view.viewTreeObserver.addOnGlobalLayoutListener {
            val r = Rect()
            view.getWindowVisibleDisplayFrame(r);
            val screenHeight = view.rootView.height;

            // r.bottom is the position above soft keypad or device button.
            // if keypad is shown, the r.bottom is smaller than that before.
            val keypadHeight = screenHeight - r.bottom;

            if (keypadHeight > screenHeight * 0.15) { // 0.15 ratio is perhaps enough to determine keypad height.
                // keyboard is opened
                if (!isKeyboardShowing) {
                    isKeyboardShowing = true
                    function(true)
                }
            }
            else {
                // keyboard is closed
                if (isKeyboardShowing) {
                    isKeyboardShowing = false
                    function(false)
                }
            }
        }
    }
}