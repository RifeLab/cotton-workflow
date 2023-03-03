package org.phenoapps.cotton.dialogs

import android.app.Activity
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

    private lateinit var weighButton: Button
    private lateinit var scanButton: Button
    private lateinit var workflowButotn: Button
    private lateinit var deleteButton: Button
    private lateinit var editButton: Button

    private var codePreviewTextView: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.dialog_sample_action)

        weighButton = findViewById(R.id.dialog_sample_action_weigh_btn)
        workflowButotn = findViewById(R.id.dialog_sample_action_workflow_btn)
        scanButton = findViewById(R.id.dialog_sample_action_scan_btn)
        deleteButton = findViewById(R.id.dialog_sample_action_delete_btn)
        editButton = findViewById(R.id.dialog_sample_action_edit_btn)

        codePreviewTextView = findViewById(R.id.dialog_sample_action_code_tv)

        scanButton.visibility = View.GONE
        workflowButotn.visibility = View.GONE
        deleteButton.visibility = View.GONE

        codePreviewTextView?.text = model.code

        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        weighButton.setOnClickListener {
            controller.weighSample(model)
            dismiss()
        }

        deleteButton.setOnClickListener {
            controller.deleteSample(model)
            dismiss()
        }

        workflowButotn.setOnClickListener {
            controller.workflow(model, false)
            dismiss()
        }

        editButton.setOnClickListener {
            controller.workflow(model, true)
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