package org.phenoapps.cotton.fragments

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Editable
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.phenoapps.cotton.R
import org.phenoapps.cotton.activities.CameraActivity
import org.phenoapps.cotton.database.entities.SampleEntity
import org.phenoapps.cotton.interfaces.MainToolbarManager
import org.phenoapps.cotton.interfaces.SoundApi
import org.phenoapps.cotton.models.SampleModel
import org.phenoapps.cotton.util.DateUtil.Companion.toDateString
import org.phenoapps.cotton.util.WorkflowUtil
import org.phenoapps.cotton.viewmodels.SampleViewModel
import org.phenoapps.fragments.bluetooth.BluetoothFragment
import java.util.*

/***
 *
 * Base fragment that Workflow and Edit fragments extend.
 *
 */
@AndroidEntryPoint
open class SampleFragment(layoutId: Int) : BluetoothFragment(layoutId), CoroutineScope by MainScope() {

    companion object {
        val TAG = SampleFragment::class.simpleName
    }

    enum class FocusState(priority: Int) {
        TOTAL(0), SEED(1), LINT(2), TEST(3), WAITING(4), EDIT(5)
    }

    protected val soundHelper by lazy {
        (activity as SoundApi).soundHelper
    }

    protected val sampleViewModel: SampleViewModel by viewModels()

    protected var prefs: SharedPreferences? = null

    //cache of samples in database
    protected var samples: List<SampleModel>? = null

    //the sample for this fragment
    protected lateinit var sample: SampleModel

    protected lateinit var seed: SampleModel
    protected lateinit var lint: SampleModel
    protected lateinit var test: SampleModel

    //sample data tv's
    protected lateinit var barcodeTv: TextView

    //total, seed, and lint weight edit texts
    protected lateinit var weightEt: EditText
    protected lateinit var seedWeightTv: TextView
    protected lateinit var seedWeightEt: EditText
    protected lateinit var lintWeightTv: TextView
    protected lateinit var lintWeightEt: EditText
    protected lateinit var testWeightEt: EditText
    protected lateinit var testWeightTv: TextView
    protected lateinit var testBarcodeEt: EditText

    //timestamp text views to show when scale measure was taken
    protected lateinit var totalWeightTime: TextView
    protected lateinit var seedWeightTime: TextView
    protected lateinit var lintWeightTime: TextView
    protected lateinit var testWeightTime: TextView

    protected lateinit var testBarcodeHeader: TextView

    //save button
    protected lateinit var saveButton: Button

    protected lateinit var sampleLiveData: LiveData<List<SampleEntity>>

    protected fun isSeedInitialized() = ::seed.isInitialized
    protected fun isLintInitialized() = ::lint.isInitialized
    protected fun isTestInitialized() = ::test.isInitialized

    protected val observer = Observer<List<SampleEntity>> { data ->

        if (data != null) {

            samples = data.map { SampleModel(it) }

            updateUi()

        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        prefs = PreferenceManager.getDefaultSharedPreferences(context)
        advisor.initialize()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val s = arguments?.getParcelable<SampleModel>("sample")
        if (s == null) findNavController().popBackStack()
        else sample = s

        sampleLiveData = sampleViewModel.getSamples()

        barcodeTv = view.findViewById(R.id.frag_sample_code_tv)
        weightEt = view.findViewById(R.id.frag_sample_total_weight_et)
        seedWeightEt = view.findViewById(R.id.frag_sample_seed_weight_et)
        seedWeightTv = view.findViewById(R.id.frag_sample_seed_weight_header_tv)
        lintWeightEt = view.findViewById(R.id.frag_sample_lint_weight_et)
        lintWeightTv = view.findViewById(R.id.frag_sample_lint_weight_header_tv)
        totalWeightTime = view.findViewById(R.id.frag_sample_total_weight_time_tv)
        seedWeightTime = view.findViewById(R.id.frag_sample_seed_weight_time_tv)
        lintWeightTime = view.findViewById(R.id.frag_sample_lint_weight_time_tv)
        testWeightEt = view.findViewById(R.id.frag_sample_test_weight_et)
        testWeightTime = view.findViewById(R.id.frag_sample_test_weight_time_tv)
        testWeightTv = view.findViewById(R.id.frag_sample_test_weight_header_tv)
        testBarcodeEt = view.findViewById(R.id.frag_sample_test_barcode_et)
        
        testBarcodeHeader = view.findViewById(R.id.frag_sample_test_code_header_tv)

        //initialize buttons
        saveButton = view.findViewById(R.id.frag_sample_save_btn)

        //at first all fields are empty and times are "gone"
        totalWeightTime.visibility = View.GONE
        seedWeightTime.visibility = View.GONE
        lintWeightTime.visibility = View.GONE
        testWeightTime.visibility = View.GONE

        testBarcodeEt.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->

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

        setupSoundOnActionNext()
        loadSamples()
    }

    private val barcodeScannerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->

        if (result.resultCode == Activity.RESULT_OK) {

            if (result.data == null) {
                Toast.makeText(context,
                    getString(R.string.canceled),
                    Toast.LENGTH_LONG
                ).show()

                soundHelper.playError()
            }

            result.data?.getStringExtra(CameraActivity.EXTRA_BARCODE)?.let { code ->

                if (isTestInitialized()) {

                    launch {

                        if (sampleViewModel.getSampleWithCode(code) == null) {

                            activity?.runOnUiThread {

                                test.code = code
                                testBarcodeEt.setText(test.code)
                                test.scanTime = Calendar.getInstance().timeInMillis

                                soundHelper.playCelebrate()
                            }

                        } else {

                            activity?.runOnUiThread {

                                Toast.makeText(context, R.string.frag_sample_barcode_exists, Toast.LENGTH_LONG).show()

                                soundHelper.playError()
                            }
                        }
                    }
                }
            }

        } else {

            Toast.makeText(context,
                getString(R.string.canceled),
                Toast.LENGTH_LONG
            ).show()

            soundHelper.playError()
        }
    }

    private fun setupSoundOnActionNext() {

        weightEt.setOnEditorActionListener { _, action, _ ->

            if (action == EditorInfo.IME_ACTION_NEXT) {

                seedWeightEt.requestFocus()

                soundHelper.playAdvance()

                return@setOnEditorActionListener true
            }

            false
        }

        seedWeightEt.setOnEditorActionListener { _, action, _ ->

            if (action == EditorInfo.IME_ACTION_NEXT) {

                lintWeightEt.requestFocus()

                soundHelper.playAdvance()

                return@setOnEditorActionListener true
            }

            false
        }

        lintWeightEt.setOnEditorActionListener { _, action, _ ->

            if (action == EditorInfo.IME_ACTION_NEXT) {

                testBarcodeEt.requestFocus()

                soundHelper.playAdvance()

                return@setOnEditorActionListener true
            }

            false
        }
    }

    private fun startBarcodeLauncher(message: String) {

        barcodeScannerLauncher.launch(Intent(context, CameraActivity::class.java).also {
            it.putExtra(CameraActivity.EXTRA_TITLE, message)
        })
    }

    //called from main activity when back button is pressed
    //if this is a new sample, ask if user wants to save or delete
    open fun resolveBackPress() {

        if (arguments?.getBoolean("new") == true) {

            askSaveOrDeleteNewSample()

        } else findNavController().popBackStack()
    }

    //find seed/lint samples from this parent
    //if they exist: show their UI and load data
    open fun updateUi() {

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

                    if (test.code != null) {
                        testBarcodeEt.setText(test.code)
                    }
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

        saveButton.setOnClickListener {

            soundHelper.playCelebrate()

            saveWorkflowData()
        }
    }

    protected fun askSaveOrDeleteNewSample() {

        val builder = AlertDialog.Builder(context, R.style.AlertDialogTheme)
        builder.setTitle(R.string.frag_sample_ask_resolve_new_sample_title)
        builder.setMessage(R.string.frag_sample_ask_resolve_new_sample_message)
        builder.setPositiveButton(R.string.save) { _, _ ->

            saveWorkflowData()

            soundHelper.playCelebrate()

            findNavController().popBackStack()
        }
        builder.setNegativeButton(R.string.delete) { _, _ ->

            deleteWorkflowData()

            soundHelper.playDelete()

            findNavController().popBackStack()
        }
        builder.show()
    }

    protected fun deleteWorkflowData() {

        if (::sample.isInitialized) {
            sampleViewModel.deleteSample(sample)
        }

        if (::seed.isInitialized) {
            sampleViewModel.deleteSample(seed)
        }

        if (::test.isInitialized) {
            sampleViewModel.deleteSample(test)
        }

        if (::lint.isInitialized) {
            sampleViewModel.deleteSample(lint)
        }
    }

    //update sample weight and time
    protected fun saveWorkflowData() {

        //save all samples
        if (::sample.isInitialized) {
            updateSampleWeight(sample, weightEt.text, totalWeightTime)
        }

        if (::seed.isInitialized) {
            updateSampleWeight(seed, seedWeightEt.text, seedWeightTime)
        }

        if (::test.isInitialized) {
            updateSampleWeight(test, testWeightEt.text, testWeightTime, testBarcodeEt)
        }

        if (::lint.isInitialized) {
            updateSampleWeight(lint, lintWeightEt.text, lintWeightTime)
        }
    }

    private fun loadSamples() {

        sampleLiveData.observe(viewLifecycleOwner, observer)
    }

    protected fun getTestEnabled(): Boolean {

        return prefs?.getBoolean(getString(R.string.key_preferences_test_enabled), false) ?: false

    }

    protected fun getUsbBarcodeReaderEnabled(): Boolean {

        return prefs?.getBoolean(getString(R.string.key_preferences_usb_barcode_reader_enabled), false) ?: false

    }

    private fun updateSampleWeight(model: SampleModel?, weight: Editable?, timeView: TextView, barcodeView: TextView? = null) {

        val time = Calendar.getInstance().timeInMillis

        model?.let { m ->

            m.person = prefs?.getString(getString(R.string.key_preferences_person), null)

            try {

                m.weight = weight?.toString()?.toDouble()

            } catch (e: NumberFormatException) {

                Log.d(TAG, "weight error NFE: ${weight.toString()}")

                e.printStackTrace()

            }

            m.scaleTime = time

            timeView.text = time.toDateString()
            timeView.visibility = View.VISIBLE

            if (barcodeView != null) {
                m.code = barcodeView.text.toString()
            }

            sampleViewModel.updateSample(m)
        }
    }

    //called from main toolbar to delete current sample
    open fun resolveDelete() {

        //not implemented
    }

    //called from main toolbar to restart workflow
    open fun resolveWorkflow() {

        // not implemented
    }

    fun resolveNote() {

        val editText = EditText(context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(8, 8, 8, 8)
            }
            setText(sample.note)
            isSingleLine = false
            setLines(1)
            setHorizontallyScrolling(false)
            maxLines = 1
        }

        AlertDialog.Builder(context, R.style.AlertDialogTheme)
            .setTitle(R.string.frag_sample_note_dialog_title)
            .setMessage(R.string.frag_sample_note_dialog_message)
            .setView(editText)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                sample.note = editText.text.toString()
                sampleViewModel.updateSample(sample)
            }
            .create()
            .show()
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        (activity as MainToolbarManager).updateToolbarVisibility()
    }

    override fun onResume() {
        super.onResume()
        (activity as MainToolbarManager).updateToolbarVisibility()
    }
}