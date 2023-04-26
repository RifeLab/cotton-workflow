package org.phenoapps.cotton.util

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.view.KeyEvent
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import dagger.hilt.android.qualifiers.ActivityContext
import org.phenoapps.cotton.R
import org.phenoapps.cotton.interfaces.UsbBarcodeReader
import javax.inject.Inject

class BarcodeScannerHelper @Inject constructor(@ActivityContext private val context: Context) {

    var barcode = String()

    private val barcodeKeyListener: View.OnKeyListener = View.OnKeyListener { v, keyCode, event ->
        //barcode scanner
        val c = event.unicodeChar
        //accept only 0..9 and ENTER
        if (c in 48..57 || c == 10) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                if (c != 10) {
                    barcode += "" + c.toChar()
                } else {
                    if (barcode.isNotBlank()) {
                        val b: String = barcode
                        editText.setText(b)
                        barcode = ""
                        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
                    }
                }
            }
        }

        true
    }

    private val editText = EditText(context).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            setMargins(8, 8, 8, 8)
        }
        isSingleLine = true
        setLines(1)
        setHorizontallyScrolling(false)
        maxLines = 1
        setOnKeyListener(barcodeKeyListener)
    }

    //https://stackoverflow.com/questions/34411919/android-and-external-usb-barcode-scanner-how-catch-the-enter
    fun dispatchKeyEvent(event: KeyEvent): Boolean {
        //barcode scanner
        val c = event.unicodeChar
        //accept only 0..9 and ENTER
        if (c in 48..57 || c == 10) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                if (c != 10) {
                    barcode += "" + c.toChar()
                } else {
                    if (barcode.isNotBlank()) {
                        val b: String = barcode
                        barcode = ""
                        (context as UsbBarcodeReader).resolveBarcode(b)
                        dialog.dismiss()
                    }
                }
            }
            return true
        }
        return false
    }

    private val dialog: AlertDialog = AlertDialog.Builder(context, R.style.AlertDialogTheme)
        .setTitle(R.string.frag_sample_list_usb_reader_dialog_title)
        .setPositiveButton(android.R.string.ok) { d, _ ->
            (context as UsbBarcodeReader).resolveBarcode(editText.text.toString())
            editText.text.clear()
            d.dismiss()
        }
        .setView(editText)
        .create()

    fun askUsbBarcodeScanner() {

        if (!dialog.isShowing) dialog.show()

    }
}