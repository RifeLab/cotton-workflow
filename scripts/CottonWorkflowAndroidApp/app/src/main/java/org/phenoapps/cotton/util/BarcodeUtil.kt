package org.phenoapps.cotton.util

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import kotlin.random.Random

class BarcodeUtil {

    companion object {

        /**
         * Barcode spec: https://www.cottoninc.com/cotton-production/ag-research/variety-improvement/breeder-fiber-sample-information/
         * Taken from above reference:
         *
         * CODE_39 barcode standard already includes the start/stop '*'
            Font: SKANDATArC39
            Code must begin with *99 and end with *
            Code will contain a total of 12 numbers as shown in example below
            Example: *991122345678*
            No symbols or alphanumeric characters
         */
        fun generateUniqueBarcode(): String = try {

            val randomId = Random.nextLong(9999999999L).toString()

            "99${randomId.padStart(10, '0')}"

        } catch (e: Exception) {

            e.printStackTrace()

            String()
        }

        fun encodeBarcode(code: String, onSuccess: (Bitmap) -> Unit, onFail: (() -> Unit)? = null) {

            try {

                val encoder = BarcodeEncoder()

                val bmp = encoder.encodeBitmap(code, BarcodeFormat.CODE_39, 600, 128)

                onSuccess(bmp)

            } catch (e: Exception) {

                e.printStackTrace()

                onFail?.invoke()
            }
        }
    }
}