package org.phenoapps.cotton.fragments

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResultListener
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
import org.phenoapps.cotton.dialogs.NewSampleAcceptDialog
import org.phenoapps.cotton.dialogs.SampleActionDialog
import org.phenoapps.cotton.interfaces.SampleController
import org.phenoapps.cotton.interfaces.ScanInteractor
import org.phenoapps.cotton.models.SampleModel
import org.phenoapps.cotton.util.PrintThread
import org.phenoapps.cotton.util.WorkflowUtil
import org.phenoapps.cotton.viewmodels.SampleViewModel
import org.phenoapps.fragments.bluetooth.BluetoothFragment
import java.util.*

@AndroidEntryPoint
class SampleListFragment: BluetoothFragment(R.layout.fragment_sample_list), ScanInteractor, SampleController {

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

    private var onNewSample: Boolean = false

    override fun onAttach(context: Context) {
        super.onAttach(context)
        advisor.initialize()
        prefs = PreferenceManager.getDefaultSharedPreferences(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

//            Toast.makeText(
//                context,
//                result.contents,
//                Toast.LENGTH_LONG
//            ).show()

            sampleToUpdate?.let { sample ->

                sample.code = result.contents
                sample.scanTime = Calendar.getInstance().timeInMillis

                viewModel.updateSample(sample)

                sampleToUpdate = null
            }
        }
    }

    private fun checkForNewSample(code: String) {

        //if code exists then ask if user wants to update weight
        //if doesn't exists, assume new sample and add to database, also move to weight fragment
        viewModel.viewModelScope.launch {

            when (val sample = viewModel.getSampleWithCode(code)) {

                null -> onNewBarcode(code)

                else -> {

                    Toast.makeText(context, "Barcode exists already!", Toast.LENGTH_SHORT).show()

                    onBarcodeExists(SampleModel(sample))
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

            startBarcodeLauncher(getString(R.string.frag_sample_list_scan_a_sample))
        }
    }

    private fun startBarcodeLauncher(message: String) {
        val options = ScanOptions()
        options.setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES)
        options.setPrompt(message)
        options.setCameraId(0) // Use a specific camera of the device
        options.setOrientationLocked(false)
        options.setBeepEnabled(false)
        options.setBarcodeImageEnabled(true)
        barcodeLauncher.launch(options)
    }

    private fun startBarcodeUpdateLauncher(message: String) {
        val options = ScanOptions()
        options.setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES)
        options.setPrompt(message)
        options.setCameraId(0) // Use a specific camera of the device
        options.setOrientationLocked(false)
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

    private fun resolveSampleResponse(sample: SampleModel?) {

        sample?.let { it ->

            try {

                it.scaleTime = Calendar.getInstance().timeInMillis
                writeWeight(it)

            } catch (e: NoSuchElementException) {

                e.printStackTrace()

            }
        }
    }

    private fun startScaleFragment(sample: SampleModel) {

        setFragmentResultListener(ScaleFragment.REQUEST_KEY) { _, bundle ->
            resolveSampleResponse(bundle.getParcelable("sample"))
        }

        findNavController().navigate(R.id.global_action_to_scale_fragment,
            bundleOf("sample" to sample))
    }

    private fun requestSampleWeight(sample: SampleModel) {

        startScaleFragment(sample)

    }

    override fun onBarcodeExists(sample: SampleModel) {

        if (sample.weight == null) {

            requestSampleWeight(sample)

        } else {

            sampleClicked(sample)
        }
    }

    override fun onAcceptNewBarcode(code: String) {

        context?.let { ctx ->

            val scanTime = Calendar.getInstance().timeInMillis

            val person = (activity as MainActivity).getPerson()

            viewModel.viewModelScope.launch {

                val parentSample = SampleModel(
                    sid = null,
                    code = code,
                    weight = null,
                    scanTime = scanTime,
                    scaleTime = null,
                    parent = null,
                    person = person,
                    type = WorkflowUtil.Companion.SubSampleType.PARENT.ordinal
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

                onNewSample = true
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

        val printerId = prefs?.getString(getString(R.string.key_printer_device_id), null)

        if (printerId != null) {

            model.code?.let { barcode ->

                PrintThread(printerId).print(arrayOf(barcode))

            }

        } else {

            findNavController().navigate(SampleListFragmentDirections
                .globalActionToPrintFragment(code = model.code))

        }

        return true
    }

    override fun weighSample(model: SampleModel) {

        requestSampleWeight(model)

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

        //when a sample is clicked, ask user what to do
        activity?.let { act ->

            act.runOnUiThread {

                val dialog = SampleActionDialog(act, this, model)
                dialog.show()

            }
        }
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

        findNavController().navigate(SampleListFragmentDirections
            .globalActionToSample(model, edit, new))
    }

    override fun getErrorEnabled(): Boolean {

        return prefs?.getBoolean(getString(R.string.key_preferences_error_threshold), true) ?: true

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
}