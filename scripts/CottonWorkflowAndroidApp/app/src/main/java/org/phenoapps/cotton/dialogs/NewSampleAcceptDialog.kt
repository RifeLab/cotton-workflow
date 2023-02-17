package org.phenoapps.cotton.dialogs

import android.app.Activity
import android.app.Dialog
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.*
import org.phenoapps.cotton.R
import org.phenoapps.cotton.interfaces.ScanInteractor
import org.phenoapps.cotton.util.BarcodeUtil

open class NewSampleAcceptDialog(private val act: Activity, private val interactor: ScanInteractor) : Dialog(act), CoroutineScope by MainScope() {

    private var acceptButton: Button? = null
    private var cancelButton: Button? = null
    private var codePreviewImageView: ImageView? = null
    private var codePreviewTextView: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.dialog_accept_new_sample)

        acceptButton = findViewById(R.id.dialog_accept_new_sample_accept_btn)
        cancelButton = findViewById(R.id.dialog_accept_new_sample_cancel_btn)
        codePreviewImageView = findViewById(R.id.dialog_accept_new_sample_code_iv)
        codePreviewTextView = findViewById(R.id.dialog_accept_new_sample_code_tv)

        cancelButton?.setOnClickListener {
            dismiss()
        }

        codePreviewTextView?.text = interactor.getScannedCode()

        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        previewBarcode()

        acceptButton?.setOnClickListener {

            interactor.getScannedCode()?.let { code ->

                interactor.onAcceptNewBarcode(code)

            }

            dismiss()
        }
    }

    private fun previewBarcode() {

        runBlocking {

            withContext(Dispatchers.IO) {

                interactor.getScannedCode()?.let { code ->

                    BarcodeUtil.encodeBarcode(code, { bmp ->

                        codePreviewImageView?.setImageBitmap(bmp)

                    }) {

                        act.runOnUiThread {

                            Toast.makeText(act, "Failed to encode barcode", Toast.LENGTH_SHORT).show()

                        }
                    }
                }

                act.runOnUiThread {

                    window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

                }
            }
        }
    }
}