package org.phenoapps.cotton.fragments

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.phenoapps.cotton.R
import org.phenoapps.cotton.activities.MainActivity
import org.phenoapps.cotton.adapters.SampleAdapter
import org.phenoapps.cotton.interfaces.MainToolbarManager
import org.phenoapps.cotton.interfaces.SampleController
import org.phenoapps.cotton.interfaces.ScanInteractor
import org.phenoapps.cotton.interfaces.UsbBarcodeReader
import org.phenoapps.cotton.models.SampleModel
import org.phenoapps.cotton.util.WorkflowUtil
import org.phenoapps.cotton.viewmodels.SampleViewModel
import org.phenoapps.fragments.bluetooth.BluetoothFragment
import java.util.*

@AndroidEntryPoint
class SampleListFragment: BluetoothFragment(R.layout.fragment_sample_list),
    ScanInteractor, SampleController {

    companion object {
        //must be <= MAX_INT
        const val MAX_ID_INCREMENT_ATTEMPTS = 1000
    }

    private var prefs: SharedPreferences? = null
    private var sampleListRecyclerView: RecyclerView? = null
    private var mainButton: ExtendedFloatingActionButton? = null
    private var scanButton: FloatingActionButton? = null
    private var printButton: FloatingActionButton? = null

    private var code: String? = null

    private val viewModel: SampleViewModel by viewModels()

    private var samples: List<SampleModel>? = null

    private var sampleToUpdate: SampleModel? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        advisor.initialize()
        prefs = PreferenceManager.getDefaultSharedPreferences(context)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sampleListRecyclerView = view.findViewById(R.id.frag_sample_list_rv)
        scanButton = view.findViewById(R.id.frag_sample_list_scanner_fab)

        setupUi()
    }

    // Register the launcher and result handler
    private val barcodeLauncher = registerForActivityResult(
        ScanContract()
    ) { result: ScanIntentResult ->
        if (result.contents == null) {

//            Toast.makeText(context,
//                getString(R.string.canceled),
//                Toast.LENGTH_LONG
//            ).show()

        } else {

//            Toast.makeText(
//                context,
//                result.contents,
//                Toast.LENGTH_LONG
//            ).show()

            code = result.contents

            checkForNewSample(result.contents)
        }
    }

    // used to update hvi test subsample barcode
    // Register the launcher and result handler
    private val barcodeUpdateLauncher = registerForActivityResult(
        ScanContract()
    ) { result: ScanIntentResult ->
        if (result.contents == null) {

//            Toast.makeText(context,
//                getString(R.string.canceled),
//                Toast.LENGTH_LONG
//            ).show()

        } else {

            sampleToUpdate?.let { sample ->

                samples?.filter { it.parent == sample.sid }
                    ?.first { it.type == WorkflowUtil.Companion.SubSampleType.TEST.ordinal }?.let { testSubSample ->

                        testSubSample.code = result.contents
                        testSubSample.scanTime = Calendar.getInstance().timeInMillis

                        viewModel.updateSample(testSubSample)

                        sampleToUpdate = null
                    }
            }
        }
    }

    fun checkForNewSample(code: String) {

        //if code exists then ask if user wants to update weight
        //if doesn't exists, assume new sample and add to database, also move to weight fragment
        viewModel.viewModelScope.launch {

            when (val sample = viewModel.getSampleWithCode(code)) {

                null -> onNewBarcode(code)

                else -> {

                    Toast.makeText(context, "Barcode exists already!", Toast.LENGTH_SHORT).show()

                    if (sample.type == WorkflowUtil.Companion.SubSampleType.PARENT.ordinal) {

                        onBarcodeExists(SampleModel(sample))

                    }
                }
            }
        }
    }

    private fun setupUi() {
        setupButtons()
        setupRecyclerData()
    }

    private fun setupRecyclerData() {

        val adapter = SampleAdapter(this)

        sampleListRecyclerView?.adapter = adapter

        viewModel.getSamples().observe(viewLifecycleOwner) { samples ->

            if (samples != null) {

                //cache all samples
                this.samples = samples.map { SampleModel(it) }

                //main list only shows non-sub-samples
                adapter.submitList(this.samples?.filter { it.parent == null })

            }

            adapter.notifyItemRangeChanged(0, adapter.itemCount)

        }
    }

    private fun setupButtons() {

        scanButton?.setOnClickListener {

            if (getUsbBarcodeReaderEnabled()) {
                (activity as UsbBarcodeReader).askUsbBarcodeScanner()
            } else {
                startBarcodeLauncher(getString(R.string.frag_sample_list_scan_a_sample))
            }
        }

        scanButton?.setImageResource(
            if (getUsbBarcodeReaderEnabled()) R.drawable.plus
            else R.drawable.barcode_scan
        )
    }

    private fun startBarcodeLauncher(message: String) {
        val options = ScanOptions()
        options.setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES)
        options.setPrompt(message)
        options.setCameraId(0) // Use a specific camera of the device
        options.setBeepEnabled(false)
        options.setBarcodeImageEnabled(true)
        barcodeLauncher.launch(options)
    }

    //call barcode scanner for hvi test subsample barcode scan
    private fun startBarcodeUpdateLauncher(message: String) {
        val options = ScanOptions()
        options.setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES)
        options.setPrompt(message)
        options.setCameraId(0) // Use a specific camera of the device
        options.setBeepEnabled(false)
        options.setBarcodeImageEnabled(true)
        barcodeUpdateLauncher.launch(options)
    }

    /**
     * This will go to onAcceptNewBarcode when accepted, else it will be dismissed
     */
    override fun onNewBarcode(code: String) {
        //skip straight to this step now, instead aof asking if user wants to add new sample
        onAcceptNewBarcode(code)
    }

    override fun onBarcodeExists(sample: SampleModel) {

        sampleClicked(sample)

    }

    override fun onAcceptNewBarcode(code: String) {

        context?.let { ctx ->

            val scanTime = Calendar.getInstance().timeInMillis

            val person = (activity as MainActivity).getPerson()
            val experiment = (activity as MainActivity).getExperiment()

            viewModel.viewModelScope.launch {

                val parentSample = SampleModel(
                    sid = null,
                    code = code,
                    weight = null,
                    scanTime = scanTime,
                    scaleTime = null,
                    parent = null,
                    person = person,
                    type = WorkflowUtil.Companion.SubSampleType.PARENT.ordinal,
                    experiment = experiment
                )

                val parentId = viewModel.insertSample(parentSample)

                parentSample.sid = parentId

                //seed, sub sample will copy the parent code
                insertSubSample(scanTime, parentId, WorkflowUtil.Companion.SubSampleType.SEED.ordinal, code)
                //lint, sub sample does not have a barcode, and material is essentially thrown out
                insertSubSample(null, parentId, WorkflowUtil.Companion.SubSampleType.LINT.ordinal)
                //test, sub sample that will need to be scanned
                insertSubSample(null, parentId, WorkflowUtil.Companion.SubSampleType.TEST.ordinal)

                workflow(parentSample, edit = false, new = true)

            }
        }
    }

    private suspend fun insertSubSample(scanTime: Long?, parentId: Long, type: Int, inheritParentCode: String? = null): SampleModel {

        val person = (activity as MainActivity).getPerson()

        val subSample = SampleModel(
            sid = null,
            code = inheritParentCode,
            weight = null,
            scanTime = scanTime,
            scaleTime = null,
            parent = parentId,
            person = person,
            type = type
        )

        val ssid = viewModel.insertSample(subSample)

        subSample.sid = ssid

        viewModel.updateSample(subSample)

        return subSample

    }

    override fun getScannedCode(): String? {
        return code
    }

    override fun getSamples(): Array<SampleModel> = this.samples?.toTypedArray() ?: arrayOf()

    override fun getSubSamples(sid: Long) = this.samples?.filter { it.parent == sid }?.toTypedArray() ?: arrayOf()

    override fun writeWeight(model: SampleModel) {

        val person = (activity as MainActivity).getPerson()

        model.also { it.person = person }

        viewModel.updateSample(model)

    }

    override fun printSample(model: SampleModel): Boolean {

        return true
    }

    override fun scanSample(model: SampleModel) {

        sampleToUpdate = model

        startBarcodeUpdateLauncher(getString(R.string.frag_sample_list_scan_a_sample))

    }

    override fun deleteSample(model: SampleModel) {

        try {

            val subsamples = samples?.filter { it.parent == model.sid } ?: listOf()

            for (sample in (subsamples + model)) {

                viewModel.deleteSample(sample)

            }

        } catch (e: NoSuchElementException) {

            e.printStackTrace()

        }
    }

    override fun sampleClicked(model: SampleModel) {

        //when a sample is clicked, open the sample's edit screen
        workflow(model, edit = true, new = false)

    }

    override fun addSample(model: SampleModel) {

//        context?.let { ctx ->
//
//            model.sid?.let { parentId ->
//
//                val scanTime = Calendar.getInstance().timeInMillis
//
//                viewModel.viewModelScope.launch {
//
//                    insertSubSample(scanTime, parentId)
//
//                }
//            }
//        }
    }

    override fun workflow(model: SampleModel, edit: Boolean, new: Boolean) {

        if (edit) {

            findNavController().navigate(SampleListFragmentDirections
                .globalActionToSample(model, new))

        } else {

            findNavController().navigate(SampleListFragmentDirections
                .globalActionToWorkflowFragment(model, new))

        }
    }

    override fun getUsbBarcodeReaderEnabled(): Boolean {

        return prefs?.getBoolean(getString(R.string.key_preferences_usb_barcode_reader_enabled), false) ?: false

    }

    override fun getErrorEnabled(): Boolean {

        return prefs?.getBoolean(getString(R.string.key_preferences_error_threshold), true) ?: true

    }

    override fun getTestEnabled(): Boolean {

        return prefs?.getBoolean(getString(R.string.key_preferences_test_enabled), false) ?: false

    }

    override fun getErrorThresh(): Double = try {

        val value = prefs?.getString(getString(R.string.key_preferences_error_check_threshold), "5.0") ?: "5.0"

        value.toDouble()

    } catch (e: Exception) {

        5.0
    }

    override fun getString(id: Int, diff: String): String {
        return context?.getString(id, diff) ?: String()
    }

    override fun onResume() {
        super.onResume()
        (activity as MainToolbarManager).updateToolbarVisibility()
    }
}