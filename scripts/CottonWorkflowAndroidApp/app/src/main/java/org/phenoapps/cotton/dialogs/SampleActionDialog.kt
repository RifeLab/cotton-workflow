package org.phenoapps.cotton.dialogs

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import org.phenoapps.cotton.R
import org.phenoapps.cotton.interfaces.SampleController
import org.phenoapps.cotton.models.SampleModel
import org.phenoapps.cotton.util.WorkflowUtil

open class SampleActionDialog(
    act: Activity,
    private val controller: SampleController,
    private val model: SampleModel,
) : Dialog(act), CoroutineScope by MainScope() {

    private lateinit var scanButton: Button
    private lateinit var workflowButotn: Button
    private lateinit var deleteButton: Button
    private lateinit var editButton: Button
    private lateinit var cancelButton: Button

    private var codePreviewTextView: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.dialog_sample_action)

        workflowButotn = findViewById(R.id.dialog_sample_action_workflow_btn)
        scanButton = findViewById(R.id.dialog_sample_action_scan_btn)
        deleteButton = findViewById(R.id.dialog_sample_action_delete_btn)
        editButton = findViewById(R.id.dialog_sample_action_edit_btn)
        cancelButton = findViewById(R.id.dialog_sample_action_cancel_btn)

        codePreviewTextView = findViewById(R.id.dialog_sample_action_code_tv)

        val subsamples = controller.getSubSamples(model.sid ?: -1L)
        val testSample = subsamples.first { it.type == WorkflowUtil.Companion.SubSampleType.TEST.ordinal }

        scanButton.visibility = if (testSample.code == null) {
             View.VISIBLE
        } else View.GONE

        workflowButotn.visibility = View.GONE
        deleteButton.visibility = View.GONE

        codePreviewTextView?.text = model.code

        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        cancelButton.setOnClickListener { dismiss() }

        deleteButton.setOnClickListener {

            AlertDialog.Builder(context, R.style.AlertDialogTheme)
                .setTitle(R.string.dialog_sample_delete_confirm_title)
                .setMessage(R.string.dialog_sample_delete_confirm_message)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    controller.deleteSample(model)
                    dismiss()
                }
                .setNegativeButton(android.R.string.cancel) { d, _ ->
                    d.dismiss()
                }
                .create()
                .show()
        }

        workflowButotn.setOnClickListener {
            controller.workflow(model, edit = false, new = false)
            dismiss()
        }

        editButton.setOnClickListener {
            controller.workflow(model, edit = true, new = false)
            dismiss()
        }

        scanButton.setOnClickListener {
            controller.scanSample(model)
            dismiss()
        }

        when (model.type) {

            WorkflowUtil.Companion.SubSampleType.PARENT.ordinal -> {

                workflowButotn.visibility = View.VISIBLE

                deleteButton.visibility = View.VISIBLE

            }
            WorkflowUtil.Companion.SubSampleType.TEST.ordinal -> {

                scanButton.visibility = View.VISIBLE

            }
        }
    }
}