package org.phenoapps.cotton.fragments

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.viewModels
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.phenoapps.cotton.R
import org.phenoapps.cotton.activities.CameraActivity
import org.phenoapps.cotton.activities.MainActivity
import org.phenoapps.cotton.adapters.SampleAdapter
import org.phenoapps.cotton.interfaces.*
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

    private val soundHelper by lazy {
        (activity as SoundApi).soundHelper
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

    private val barcodeScannerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->

        if (result.resultCode == Activity.RESULT_OK) {

            result?.data?.getStringExtra(CameraActivity.EXTRA_BARCODE)?.let { barcode ->

                code = barcode

                soundHelper.playCelebrate()

                checkForNewSample(barcode)
            }

        } else {

            soundHelper.playError()
        }
    }

    // used to update hvi test subsample barcode
    // Register the launcher and result handler
    private val barcodeUpdateLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->

        if (result.resultCode == Activity.RESULT_OK) {

            result.data?.getStringExtra(CameraActivity.EXTRA_BARCODE)?.let { barcode ->

                sampleToUpdate?.let { sample ->

                    samples?.filter { it.parent == sample.sid }
                        ?.first { it.type == WorkflowUtil.Companion.SubSampleType.TEST.ordinal }?.let { testSubSample ->

                            testSubSample.code = barcode
                            testSubSample.scanTime = Calendar.getInstance().timeInMillis

                            viewModel.updateSample(testSubSample)

                            sampleToUpdate = null
                        }
                }

                soundHelper.playCelebrate()
            }

        } else {

            soundHelper.playError()
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { result ->

        if (result) {

            barcodeScannerLauncher.launch(Intent(context, CameraActivity::class.java).also {
                it.putExtra(CameraActivity.EXTRA_TITLE, getString(R.string.frag_sample_list_scan_a_sample))
            })

        } else {

            soundHelper.playError()
        }
    }

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

    fun checkForNewSample(code: String) {

        //if code exists then ask if user wants to update weight
        //if doesn't exists, assume new sample and add to database, also move to weight fragment
        viewModel.viewModelScope.launch {

            when (val sample = viewModel.getSampleWithCode(code)) {

                null -> onNewBarcode(code)

                else -> {

                    Toast.makeText(context, getString(R.string.frag_sample_barcode_exists), Toast.LENGTH_SHORT).show()

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
                startBarcodeLauncher()
            }
        }

        scanButton?.setImageResource(
            if (getUsbBarcodeReaderEnabled()) R.drawable.plus
            else R.drawable.barcode_scan
        )
    }

    private fun startBarcodeLauncher() {

        cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)

    }

    //call barcode scanner for hvi test subsample barcode scan
    private fun startBarcodeUpdateLauncher(message: String) {

        barcodeUpdateLauncher.launch(Intent(context, CameraActivity::class.java).also {
            it.putExtra(CameraActivity.EXTRA_TITLE, message)
        })
    }

    /**
     * This will go to onAcceptNewBarcode when accepted, else it will be dismissed
     */
    override fun onNewBarcode(code: String) {

        soundHelper.playCelebrate()

        //skip straight to this step now, instead of asking if user wants to add new sample
        onAcceptNewBarcode(code)
    }

    override fun onBarcodeExists(sample: SampleModel) {

        soundHelper.playAdvance()

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