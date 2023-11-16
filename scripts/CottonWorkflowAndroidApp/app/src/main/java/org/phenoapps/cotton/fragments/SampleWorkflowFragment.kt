package org.phenoapps.cotton.fragments

import StabilityMonitor
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.phenoapps.cotton.R
import org.phenoapps.cotton.activities.CameraActivity
import org.phenoapps.cotton.activities.MainActivity
import org.phenoapps.cotton.util.DateUtil.Companion.toDateString
import org.phenoapps.cotton.util.ScaleUtil
import org.phenoapps.cotton.util.WorkflowUtil
import java.util.*

/***
 *
 * private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
 *
  v1.4 update - updated next weight threshold to trigger when last weight is <= 0 (instead of exactly 0)
    Follows this process:
    1. We tare the scale to the bag size the samples are collected in. (12lb, 16lb, or 25lb bag)
        a. scale should be at 0
    2. We use a barcode scanner and scan the bag.
    3. Then we weight the bag with the cotton sample inside.
        a. scale should be at some weight C (weight of the cotton, this will always be > 0)
    4. Then we gin the sample.
        a. scale will be - when bag is removed for gin
    5. We collect the fuzzy seed and place back into original bag
        a. scale should be > 0, because its greater than the original tare
        We weigh the bag with the fuzzy seeds inside.
    6. Next we grab the lint,  and place it on top of a bag that matches the sample bag(empty bag). Get the lint weight.
        a. removing the bag will cause - weight
        b. sample bag + lint will cause + weight
        Remove 25 grams of lint.
            c. weight should always be > 0, with remaining lint
        25 grams of lint is place inside a labeled 2lb bag( for HVI testing). This bag is scanned as well.
    The remaining lint is thrown away or kept.
    Repeat the process.
    We  use the error check for any samples that are +/-5

 Whenever a main page item is clicked, this screen opens to show/input details of a sample.

 Screens for parents show total, seed, lint, etc weights. Child screens only show total weight.

 When a scale connection is made the fragment automatically fills in the data on stable reads,
 if the user clicks any of the edit text the flow is interrupted and won't restart until fragment restarts.
 */
@AndroidEntryPoint
class SampleWorkflowFragment : SampleFragment(R.layout.fragment_sample_workflow) {

    private lateinit var stabilityMonitor: StabilityMonitor

    private var state = FocusState.TOTAL

    //numeric image views
    private lateinit var numericOneIv: ImageView
    private lateinit var numericTwoIv: ImageView
    private lateinit var numericThreeIv: ImageView
    private lateinit var numericFourIv: ImageView
    private lateinit var numericFiveIv: ImageView

    private val barcodeLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->

        if (result.resultCode == Activity.RESULT_OK) {

            if (result.data == null) {

                soundHelper.playError()

                Toast.makeText(
                    context,
                    getString(R.string.canceled),
                    Toast.LENGTH_LONG
                ).show()
            }

            result.data?.getStringExtra(CameraActivity.EXTRA_BARCODE)?.let { code ->

                launch {

                    if (sampleViewModel.getSampleWithCode(code) == null) {

                        activity?.runOnUiThread {

                            numericFiveIv.setImageResource(R.drawable.check_circle_outline_green)
                            test.code = code
                            testBarcodeEt.setText(test.code)
                            test.scanTime = Calendar.getInstance().timeInMillis
                            sampleViewModel.updateSample(test)

                            soundHelper.playCelebrate()
                        }

                    } else {

                        activity?.runOnUiThread {

                            Toast.makeText(
                                context,
                                R.string.frag_sample_barcode_exists,
                                Toast.LENGTH_LONG
                            ).show()

                            soundHelper.playError()

                        }
                    }
                }
            }

        } else {

            Toast.makeText(
                context,
                getString(R.string.canceled),
                Toast.LENGTH_LONG
            ).show()

            soundHelper.playError()

        }
    }

    private val onFocusChangedListener = View.OnFocusChangeListener { v, hasFocus ->
        if (!hasFocus && (v as EditText).text.isNotBlank()) {
            when (v) {
                weightEt -> {
                    numericOneIv.setImageResource(R.drawable.check_circle_outline_green)
                }
                seedWeightEt -> {
                    numericTwoIv.setImageResource(R.drawable.check_circle_outline_green)
                }
                lintWeightEt -> {
                    numericThreeIv.setImageResource(R.drawable.check_circle_outline_green)
                }
                testWeightEt -> {
                    numericFourIv.setImageResource(R.drawable.check_circle_outline_green)

                    try {

                        checkTestDiff(testWeightEt.text.toString().toDouble())

                    } catch (e: java.lang.NumberFormatException) {

                        e.printStackTrace()

                    }
                }
            }
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        stabilityMonitor = StabilityMonitor(context)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        //initialize numeric ivs
        numericOneIv = view.findViewById(R.id.frag_workflow_numeric_one_iv)
        numericTwoIv = view.findViewById(R.id.frag_workflow_numeric_two_iv)
        numericThreeIv = view.findViewById(R.id.frag_workflow_numeric_three_iv)
        numericFourIv = view.findViewById(R.id.frag_workflow_numeric_four_iv)
        numericFiveIv = view.findViewById(R.id.frag_workflow_numeric_five_iv)

        weightEt.onFocusChangeListener = onFocusChangedListener
        seedWeightEt.onFocusChangeListener = onFocusChangedListener
        lintWeightEt.onFocusChangeListener = onFocusChangedListener
        testWeightEt.onFocusChangeListener = onFocusChangedListener
        testBarcodeEt.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->

            if (hasFocus) {
                if (!getUsbBarcodeReaderEnabled()) {
                    startBarcodeLauncher(getString(R.string.frag_sample_scan_test_label))
                }
            }
        }

        testBarcodeEt.setOnClickListener {
            if (!getUsbBarcodeReaderEnabled()) {
                startBarcodeLauncher(getString(R.string.frag_sample_scan_test_label))
            }
        }

        //set save button text
        saveButton.text = getString(R.string.save)

        view.visibility = View.VISIBLE

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
                    numericTwoIv.setImageResource(R.drawable.check_circle_outline_green)
                }

                if (lint.weight != null) {

                    lintWeightTime.visibility = View.VISIBLE
                    lintWeightEt.setText(lint.weight?.toString())
                    lintWeightTime.text = lint.scaleTime?.toDateString()
                    numericThreeIv.setImageResource(R.drawable.check_circle_outline_green)
                }

                if (test.weight != null) {

                    testWeightEt.setText(test.weight?.toString())
                    testWeightTime.text = test.scaleTime?.toDateString()
                    numericFourIv.setImageResource(R.drawable.check_circle_outline_green)
                }

                //if test is disabled in settings, hide test UI
                if (!getTestEnabled()) {
                    numericFourIv.visibility = View.GONE
                    testWeightEt.visibility = View.GONE
                    testWeightTime.visibility = View.GONE
                    testWeightTv.visibility = View.GONE
                } else {
                    numericFourIv.visibility = View.VISIBLE
                    testWeightEt.visibility = View.VISIBLE
                    testWeightTime.visibility = View.VISIBLE
                    testWeightTv.visibility = View.VISIBLE
                    testBarcodeEt.visibility = View.VISIBLE
                    numericFiveIv.visibility = View.VISIBLE
                    testBarcodeHeader.visibility = View.VISIBLE
                }

                if (test.code != null && test.code!!.isNotBlank()) {
                    testBarcodeEt.setText(test.code)
                    numericFiveIv.setImageResource(R.drawable.check_circle_outline_green)
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
            numericOneIv.setImageResource(R.drawable.check_circle_outline_green)
        }

        weightEt.setOnClickListener {
            state = FocusState.WAITING
        }

        lintWeightEt.setOnClickListener {
            state = FocusState.WAITING
        }

        seedWeightEt.setOnClickListener {
            state = FocusState.WAITING
        }

        testWeightEt.setOnClickListener {
            state = FocusState.WAITING
        }

        saveButton.setOnClickListener {

            saveWorkflowData()

            soundHelper.playCelebrate()

            findNavController().popBackStack()
        }

        startWeightListener()
    }

    private fun checkTestDiff(weight: Double) {

        if (weight > 0) {

            val testThresh = getTestThresh()

            if (isLintInitialized()) {

                val lintWeight = try {
                    lintWeightEt.text.toString().toDouble()
                } catch (e: NumberFormatException) {
                    0.0
                }

                val diff = lintWeight - weight
                //TODO used '>=' here but mainly for testing, or make a min/max thresh
                //TODO should this save the threshed amount of the final lint ?
                //TODO make an issue on things to ask Scientists
                if (diff >= testThresh) {

                    testWeightEt.setText("$weight")

                    state = FocusState.WAITING

                    numericFourIv.setImageResource(R.drawable.check_circle_outline_green)

                    Toast.makeText(context, R.string.frag_sample_test_complete, Toast.LENGTH_LONG).show()

                    soundHelper.playAdvance()

                    saveWorkflowData()

                    if (!getUsbBarcodeReaderEnabled()) {
                        startBarcodeLauncher(getString(R.string.frag_sample_scan_test_label))
                    } else {
                        testBarcodeEt.requestFocus()
                    }
                }
            }
        }
    }

    private var lastReading = 0.0
    private fun startWeightListener() {

        //val scaleId = (activity as MainActivity).getScaleId()

        (activity as MainActivity).ohausViewModel.advisor = advisor

        advisor.withNearby {

            if ((activity as MainActivity).connected) {

                (activity as MainActivity).ohausViewModel.readWeight().observe(viewLifecycleOwner) { data ->

                    //println("Weight: ${data.toCharArray().joinToString(",")}")

                    try {

                        var weight = data.replace(ScaleUtil.UNIT, "").toDouble()

                        if (stabilityMonitor.isEnabled()) {

                            stabilityMonitor.monitor(weight.toString())

                            if (stabilityMonitor.isStable()) {

                                weight = stabilityMonitor.getStableRead().toDouble()

                            } else return@observe

                        }

                        //in test mode take input until 25g are taken off
                        //else take a reading between 0.0g readings
                        if (state == FocusState.TEST) {

                            checkTestDiff(weight)

                        } else if (state == FocusState.EDIT) {

                            //do nothing !

                        } else if (lastReading <= 0.0 && weight > 0) {

                            when (state) {

                                FocusState.TOTAL -> {

                                    weightEt.setText("$weight")

                                    seedWeightEt.selectAll()

                                    state = FocusState.SEED

                                    numericOneIv.setImageResource(R.drawable.check_circle_outline_green)

                                    soundHelper.playAdvance()
                                }

                                FocusState.SEED -> {

                                    seedWeightEt.setText("$weight")

                                    lintWeightEt.selectAll()

                                    state = FocusState.LINT

                                    numericTwoIv.setImageResource(R.drawable.check_circle_outline_green)

                                    soundHelper.playAdvance()

                                }

                                FocusState.LINT -> {

                                    lintWeightEt.setText("$weight")

                                    numericThreeIv.setImageResource(R.drawable.check_circle_outline_green)

                                    state = if (getTestEnabled()) {

                                        testWeightEt.selectAll()

                                        FocusState.TEST

                                    } else {

                                        testBarcodeEt.requestFocus()

                                        FocusState.WAITING
                                    }

                                    soundHelper.playAdvance()

                                }

                                else -> {

                                    //TODO WAITING, in case user interrupts

                                }
                            }
                        }

                        lastReading = weight

                    } catch (e: NumberFormatException) {

                        e.printStackTrace()

                        println(data)
                    }
                }
            }
        }
    }

    private fun startBarcodeLauncher(message: String) {

        barcodeLauncher.launch(Intent(context, CameraActivity::class.java).also {
            it.putExtra(CameraActivity.EXTRA_TITLE, message)
        })
    }

    private fun getTestThresh(): Double = try {

        val value = prefs?.getString(getString(R.string.key_preferences_test_weight_threshold), "0.025") ?: "0.025"

        value.toDouble()

    } catch (e: Exception) {

        0.025 //TODO
    }

    //called from main toolbar to delete current sample
    override fun resolveDelete() {

        AlertDialog.Builder(context, R.style.AlertDialogTheme)
            .setTitle(R.string.dialog_sample_delete_confirm_title)
            .setMessage(R.string.dialog_sample_delete_confirm_message)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                deleteWorkflowData()
                soundHelper.playDelete()
                findNavController().popBackStack()
            }
            .setNegativeButton(android.R.string.cancel) { d, _ ->
                d.dismiss()
            }
            .create()
            .show()
    }

    private fun clearUi() {

        totalWeightTime.text = ""
        seedWeightTime.text = ""
        lintWeightTime.text = ""
        testWeightTime.text = ""

        weightEt.setText("")
        seedWeightEt.setText("")
        lintWeightEt.setText("")
        testWeightEt.setText("")
        testBarcodeEt.setText("")
    }

    //called from main toolbar to restart workflow
    override fun resolveWorkflow() {

        AlertDialog.Builder(context, R.style.AlertDialogTheme)
            .setTitle(R.string.dialog_sample_workflow_confirm_title)
            .setMessage(R.string.dialog_sample_workflow_confirm_message)
            .setPositiveButton(android.R.string.ok) { _, _ ->

                clearUi()

                state = FocusState.TOTAL

                lastReading = Double.NEGATIVE_INFINITY

                weightEt.selectAll()

                resetUi()

                (activity as MainActivity).ohausViewModel.clearScaleLastRead()

                soundHelper.playCycle()
            }
            .setNegativeButton(android.R.string.cancel) { d, _ ->
                d.dismiss()
            }
            .create()
            .show()
    }

    private fun resetUi() {

        numericOneIv.setImageResource(R.drawable.numeric_1_circle_outline)
        numericTwoIv.setImageResource(R.drawable.numeric_2_circle_outline)
        numericThreeIv.setImageResource(R.drawable.numeric_3_circle_outline)
        numericFourIv.setImageResource(R.drawable.numeric_4_circle_outline)
        numericFiveIv.setImageResource(R.drawable.numeric_5_circle_outline)

    }
}