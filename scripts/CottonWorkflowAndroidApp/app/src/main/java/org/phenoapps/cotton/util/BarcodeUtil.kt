package org.phenoapps.cotton.util

import android.content.Context
import android.graphics.Bitmap
import androidx.preference.PreferenceManager
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import org.phenoapps.cotton.R
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

        fun generateIncrementalBarcode(context: Context): String = try {

            val prefs = PreferenceManager.getDefaultSharedPreferences(context)

            val nextId = prefs.getString(context.getString(R.string.key_preferences_workflow_id_start), "0") ?: "0"

            try {

                var nextNextId = nextId.toLong() + 1

                if (nextId == "9999999999") {

                    nextNextId = 0L

                }

                prefs.edit().putString(context.getString(R.string.key_preferences_workflow_id_start), "$nextNextId").apply()


            } catch (e: Exception) {

                e.printStackTrace()

            }

            nextId.toCode39()

        } catch (e: Exception) {

            e.printStackTrace()

            String()
        }

        fun String.toCode39() = "99${this.padStart(10, '0')}"

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