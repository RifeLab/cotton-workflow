package org.phenoapps.cotton.fragments

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
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
import org.phenoapps.cotton.activities.DefineStorageActivity
import org.phenoapps.cotton.adapters.SampleAdapter
import org.phenoapps.cotton.dialogs.NewSampleAcceptDialog
import org.phenoapps.cotton.dialogs.SampleActionDialog
import org.phenoapps.cotton.interfaces.SampleController
import org.phenoapps.cotton.interfaces.ScanInteractor
import org.phenoapps.cotton.models.SampleModel
import org.phenoapps.cotton.util.BarcodeUtil
import org.phenoapps.cotton.util.PrintThread
import org.phenoapps.cotton.viewmodels.SampleListViewModel
import org.phenoapps.fragments.bluetooth.BluetoothFragment
import org.phenoapps.security.Security
import java.util.*
import kotlin.NoSuchElementException

@AndroidEntryPoint
class SampleListFragment: BluetoothFragment(R.layout.fragment_sample_list), ScanInteractor, SampleController {

    /**
     * TODO: export basic csv data
     * TODO: metadata (person, ...)
     * TODO: scale options (generify weight  metric)
     */
    private var prefs: SharedPreferences? = null
    private var sampleListRecyclerView: RecyclerView? = null
    private var mainButton: ExtendedFloatingActionButton? = null
    private var scanButton: FloatingActionButton? = null
    private var printButton: FloatingActionButton? = null

    private var code: String? = null

    private val viewModel: SampleListViewModel by viewModels()

    private var samples: List<SampleModel>? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        advisor.initialize()
        prefs = PreferenceManager.getDefaultSharedPreferences(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val definerFirstLoad = prefs?.getBoolean(getString(R.string.key_definer_first_load), true) ?: true
        if (definerFirstLoad) {
            prefs?.edit()?.putBoolean(getString(R.string.key_definer_first_load), false)?.apply()
            definerLauncher.launch(Intent(context, DefineStorageActivity::class.java))
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sampleListRecyclerView = view.findViewById(R.id.frag_sample_list_rv)
        mainButton = view.findViewById(R.id.frag_sample_list_main_fab)
        printButton = view.findViewById(R.id.frag_sample_list_print_fab)
        scanButton = view.findViewById(R.id.frag_sample_list_scanner_fab)

        setupUi()
    }

    private val definerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->

    }

    // Register the launcher and result handler
    private val barcodeLauncher = registerForActivityResult(
        ScanContract()
    ) { result: ScanIntentResult ->
        if (result.contents == null) {
            Toast.makeText(context, "Cancelled", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(
                context,
                "Scanned: " + result.contents,
                Toast.LENGTH_LONG
            ).show()

            code = result.contents

            checkForNewSample(result.contents)
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

        mainButton?.shrink()
        mainButton?.setOnClickListener {

            if (mainButton?.isExtended == true) {

                mainButton?.shrink()
                scanButton?.visibility = View.GONE
                printButton?.visibility = View.GONE

            } else {

                mainButton?.extend()
                scanButton?.visibility = View.VISIBLE
                printButton?.visibility = View.VISIBLE
            }
        }

        scanButton?.setOnClickListener {

            val options = ScanOptions()
            options.setDesiredBarcodeFormats(ScanOptions.CODE_39)
            options.setPrompt("Scan a barcode")
            options.setCameraId(0) // Use a specific camera of the device
            options.setOrientationLocked(false)
            options.setBeepEnabled(false)
            options.setBarcodeImageEnabled(true)
            barcodeLauncher.launch(options)
        }

        printButton?.setOnClickListener {

            findNavController().navigate(SampleListFragmentDirections
                .globalActionToPrintFragment(null))
        }
    }

    /**
     * This will go to onAcceptNewBarcode when accepted, else it will be dismissed
     */
    override fun onNewBarcode(code: String) {

        activity?.let { act ->

            act.runOnUiThread {

                val acceptDialog = NewSampleAcceptDialog(act, this)

                acceptDialog.show()

            }
        }
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

        val scanTime = Calendar.getInstance().timeInMillis

        viewModel.viewModelScope.launch {

            val parentSample = SampleModel(
                sid = null,
                code = code,
                weight = null,
                scanTime = scanTime,
                scaleTime = null,
                parent = null
            )

            val parentId = viewModel.insertSample(parentSample)

            parentSample.sid = parentId

            val sub1 = insertSubSample(scanTime, parentId)
            val sub2 = insertSubSample(scanTime, parentId)

            val printerAddress = prefs?.getString(getString(R.string.key_printer_device_id), null)

            if (printerAddress != null) {

                advisor.withNearby {

                    sub1.code?.let { codeA ->

                        sub2.code?.let { codeB ->

                            PrintThread(printerAddress).print(arrayOf(codeA, codeB))

                        }
                    }
                }

            } else {

                activity?.runOnUiThread {

                    Toast.makeText(context, R.string.frag_sample_list_no_printer, Toast.LENGTH_LONG).show()

                }
            }

            requestSampleWeight(parentSample)

        }
    }

    private suspend fun insertSubSample(scanTime: Long, parentId: Long): SampleModel {

        val subSample = SampleModel(
            sid = null,
            code = null,
            weight = null,
            scanTime = scanTime,
            scaleTime = null,
            parent = parentId
        )

        val ssid = viewModel.insertSample(subSample)

        subSample.sid = ssid

        var generatedCode: String
        do {

            generatedCode = BarcodeUtil.generateUniqueBarcode()

        } while (generatedCode.isEmpty() || viewModel.getSampleWithCode(generatedCode) != null)

        subSample.code = generatedCode

        viewModel.updateSample(subSample)

        return subSample
    }

    override fun getScannedCode(): String? {
        return code
    }

    override fun getSamples(): Array<SampleModel> = this.samples?.toTypedArray() ?: arrayOf()

    override fun getSubSamples(sid: Long) = this.samples?.filter { it.parent == sid }?.toTypedArray() ?: arrayOf()

    override fun writeWeight(model: SampleModel) {

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

        model.code?.let { code ->

            //when a sample is clicked, ask user what to do
            activity?.let { act ->

                act.runOnUiThread {

                    val dialog = SampleActionDialog(act, this, model)
                    dialog.show()

                }
            }
        }
    }

    override fun getString(id: Int, diff: String): String {
        return context?.getString(id, diff) ?: String()
    }
}