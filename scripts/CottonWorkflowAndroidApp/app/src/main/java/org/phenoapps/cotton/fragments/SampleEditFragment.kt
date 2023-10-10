package org.phenoapps.cotton.fragments

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.navigation.fragment.findNavController
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.phenoapps.cotton.R
import org.phenoapps.cotton.activities.MainActivity
import org.phenoapps.cotton.util.DateUtil.Companion.toDateString
import org.phenoapps.cotton.util.WorkflowUtil
import java.util.*

/***
 *
 * This fragment is used to edit an existing sample.
 *
 */
@AndroidEntryPoint
class SampleEditFragment : SampleFragment(R.layout.fragment_sample_edit) {

    private var state = FocusState.TOTAL

    //fab scale button
    private lateinit var scaleFab: FloatingActionButton

    private var scaleReadingValue: String? = null

    // Barcode launcher for scanning Test label
    private val barcodeLauncher = registerForActivityResult(
        ScanContract()
    ) { result: ScanIntentResult ->
        if (result.contents == null) {

            Toast.makeText(context,
                getString(R.string.canceled),
                Toast.LENGTH_LONG
            ).show()

        } else {

            if (isTestInitialized()) {

                val code = result.contents

                launch {

                    if (sampleViewModel.getSampleWithCode(code) == null) {

                        activity?.runOnUiThread {

                            test.code = result.contents
                            testBarcodeEt.setText(test.code)
                            test.scanTime = Calendar.getInstance().timeInMillis
                            sampleViewModel.updateSample(test)

                        }

                    } else {

                        activity?.runOnUiThread {

                            Toast.makeText(context, R.string.frag_sample_barcode_exists, Toast.LENGTH_LONG).show()

                        }
                    }
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        scaleFab = view.findViewById(R.id.frag_sample_weight_fab)

        //set save button text
        saveButton.text = getString(R.string.update)

        initializeScaleButton()
    }

    private fun initializeScaleButton() {

        scaleFab.setOnClickListener {

            val model = when (state) {
                FocusState.SEED -> seed
                FocusState.LINT -> lint
                FocusState.TEST -> test
                else -> sample
            }

            try {

                model.weight = scaleReadingValue?.toDouble()

            } catch (e: NumberFormatException) {

                e.printStackTrace()

            }

            if (model.weight != null) {

                //update text view based on state
                state = when (state) {
                    FocusState.SEED -> {
                        seedWeightEt.setText(model.weight.toString())
                        lintWeightEt.requestFocus()
                        FocusState.LINT
                    }
                    FocusState.LINT -> {
                        lintWeightEt.setText(model.weight.toString())
                        if (getTestEnabled()) {
                            testWeightEt.requestFocus()
                            FocusState.TEST
                        } else if (getUsbBarcodeReaderEnabled()) {
                            testBarcodeEt.requestFocus()
                            FocusState.WAITING
                        } else {
                            testBarcodeEt.requestFocus()
                            FocusState.WAITING
                        }
                    }
                    FocusState.TEST -> {
                        testWeightEt.setText(model.weight.toString())
                        FocusState.WAITING
                    }
                    FocusState.TOTAL -> {
                        weightEt.setText(model.weight.toString())
                        seedWeightEt.requestFocus()
                        FocusState.SEED
                    }
                    else -> {
                        //complete
                        FocusState.WAITING
                    }
                }
            }
        }

        val scaleId = (activity as MainActivity).getScaleId()

        if (scaleId != null) {

            advisor.withNearby { adapter ->

                if ((activity as MainActivity).connected) {

                    (activity as MainActivity).ohausViewModel.readWeight().observe(viewLifecycleOwner) { data ->

                        if (data != null && data.isNotBlank()) {

                            scaleReadingValue = data

                        }
                    }
                }
            }
        }
    }

    private fun startBarcodeLauncher(message: String) {
        val options = ScanOptions()
        options.setOrientationLocked(true)
        options.setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES)
        options.setPrompt(message)
        options.setCameraId(0) // Use a specific camera of the device
        options.setBeepEnabled(false)
        options.setBarcodeImageEnabled(true)
        barcodeLauncher.launch(options)
    }

    //called from main activity when back button is pressed
    //if this is a new sample, ask if user wants to save or delete
    override fun resolveBackPress() {

        if (arguments?.getBoolean("new") == true) {

            askSaveOrDeleteNewSample()

        } else findNavController().popBackStack()
    }

    //find seed/lint samples from this parent
    //if they exist: show their UI and load data
    override fun updateUi() {

        //stop listening for new data
        sampleLiveData.removeObserver(observer)

        //first check if this sample is a parent
        if (sample.type == WorkflowUtil.Companion.SubSampleType.PARENT.ordinal) {

            //find children from parent
            val children = samples?.filter { it.parent == sample.sid } ?: listOf()

            //ensure we have two children
            if (children.size == WorkflowUtil.NumSubSamples) {

                //sort by sid, the lowest sid will always be the seed
                val sorted = children.sortedBy { it.sid }
                seed = sorted[0]
                lint = sorted[1]
                test = sorted[2]

                if (seed.weight != null) {

                    seedWeightTime.visibility = View.VISIBLE
                    seedWeightEt.setText(seed.weight?.toString())
                    seedWeightTime.text = seed.scaleTime?.toDateString()
                }

                if (lint.weight != null) {

                    lintWeightTime.visibility = View.VISIBLE
                    lintWeightEt.setText(lint.weight?.toString())
                    lintWeightTime.text = lint.scaleTime?.toDateString()
                }

                if (test.weight != null) {

                    testWeightEt.setText(test.weight?.toString())
                    testWeightTime.text = test.scaleTime?.toDateString()
                }

                if (!getTestEnabled()) {
                    testWeightEt.visibility = View.GONE
                    testWeightTime.visibility = View.GONE
                    testWeightTv.visibility = View.GONE
                } else {
                    testWeightTv.visibility = View.VISIBLE
                    testWeightEt.visibility = View.VISIBLE
                    testWeightTime.visibility = View.VISIBLE
                    testWeightTv.visibility = View.VISIBLE
                    testBarcodeEt.visibility = View.VISIBLE
                    testBarcodeHeader.visibility = View.VISIBLE
                }

                if (test.code != null) {
                    testBarcodeEt.setText(test.code)
                }
            }

        } else {

            lintWeightTv.visibility = View.GONE
            seedWeightTv.visibility = View.GONE
            seedWeightEt.visibility = View.GONE
            lintWeightEt.visibility = View.GONE
            testWeightEt.visibility = View.GONE
            testWeightTime.visibility = View.GONE
            testWeightTv.visibility = View.GONE

        }

        //set parent data
        barcodeTv.text = sample.code

        if (sample.weight != null) {

            totalWeightTime.visibility = View.VISIBLE
            weightEt.setText(sample.weight?.toString())
            totalWeightTime.text = sample.scaleTime?.toDateString()

        }

        weightEt.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) state = FocusState.TOTAL
        }
        lintWeightEt.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) state = FocusState.LINT
        }
        seedWeightEt.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) state = FocusState.SEED
        }
        testWeightEt.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) state = FocusState.TEST
        }

        weightEt.setOnClickListener {
            state = FocusState.TOTAL
        }

        lintWeightEt.setOnClickListener {
            state = FocusState.LINT
        }

        seedWeightEt.setOnClickListener {
            state = FocusState.SEED
        }

        testWeightEt.setOnClickListener {
            state = FocusState.TEST
        }

        saveButton.setOnClickListener {

            saveWorkflowData()

            findNavController().popBackStack()
        }

        weightEt.requestFocus()
    }

    //called from main toolbar to delete current sample
    override fun resolveDelete() {

        AlertDialog.Builder(context, R.style.AlertDialogTheme)
            .setTitle(R.string.dialog_sample_delete_confirm_title)
            .setMessage(R.string.dialog_sample_delete_confirm_message)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                deleteWorkflowData()
                findNavController().popBackStack()
            }
            .setNegativeButton(android.R.string.cancel) { d, _ ->
                d.dismiss()
            }
            .create()
            .show()
    }

    //called from main toolbar to restart workflow
    override fun resolveWorkflow() {

        findNavController().navigate(SampleEditFragmentDirections
            .actionToWorkflowFragment(sample))
    }
}