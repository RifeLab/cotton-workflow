package org.phenoapps.cotton.dialogs

import android.app.Activity
import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import kotlinx.coroutines.*
import org.phenoapps.cotton.R
import org.phenoapps.cotton.interfaces.SampleController
import org.phenoapps.cotton.models.SampleModel

open class SampleActionDialog(
    act: Activity,
    private val controller: SampleController,
    private val model: SampleModel,
    private val samples: List<SampleModel>?
) : Dialog(act), CoroutineScope by MainScope() {

    private var acceptButton: Button? = null
    private var cancelButton: Button? = null
    private var radioGroup: RadioGroup? = null
    private var addSubsampleButton: RadioButton? = null

    private var codePreviewTextView: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.dialog_sample_action)

        acceptButton = findViewById(R.id.dialog_sample_action_accept_btn)
        cancelButton = findViewById(R.id.dialog_sample_action_cancel_btn)
        radioGroup = findViewById(R.id.dialog_sample_action_rg)
        codePreviewTextView = findViewById(R.id.dialog_sample_action_code_tv)
        addSubsampleButton = findViewById(R.id.dialog_sample_action_add_subsample)

        cancelButton?.setOnClickListener {
            dismiss()
        }

        codePreviewTextView?.text = model.code

        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        acceptButton?.setOnClickListener {

            when (radioGroup?.checkedRadioButtonId) {

                R.id.dialog_sample_action_print_rb -> {

                    controller.printSample(model)

                }

                R.id.dialog_sample_action_weigh_rb -> {

                    controller.weighSample(model)

                }

                R.id.dialog_sample_action_delete_rb -> {

                    controller.deleteSample(model)
                }

                R.id.dialog_sample_action_add_subsample -> {

                    controller.addSample(model)
                }
            }

            dismiss()
        }

        addSubsampleButton?.visibility = View.GONE

        try {

            val children = samples?.filter { it.parent == model.sid } ?: listOf()

            if (children.size < 2 && model.parent == null) {

                addSubsampleButton?.visibility = View.VISIBLE

            } else {

                addSubsampleButton?.visibility = View.GONE
            }

        } catch (e: Exception) {

            e.printStackTrace()

        }
    }
}