package org.phenoapps.cotton.fragments

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import org.phenoapps.cotton.R
import org.phenoapps.cotton.activities.MainActivity
import org.phenoapps.cotton.database.entities.SampleEntity
import org.phenoapps.cotton.models.SampleModel
import org.phenoapps.cotton.util.DateUtil.Companion.toDateString
import org.phenoapps.cotton.util.ScaleUtil
import org.phenoapps.cotton.util.WorkflowUtil
import org.phenoapps.cotton.viewmodels.OhausSampleViewModel
import org.phenoapps.cotton.viewmodels.SampleViewModel
import org.phenoapps.fragments.bluetooth.BluetoothFragment
import java.util.*

/***
 *
 * private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
 *
 *
 * Follows this process:
    We tare the scale to the bag size the samples are collected in. (12lb, 16lb, or 25lb bag)
    We use a barcode scanner and scan the bag.
    Then we weight the bag with the cotton sample inside.
    Then we gin the sample.
    We collect the fuzzy seed and place back into original bag
        We weigh the bag with the fuzzy seeds inside.
    Next we grab the lint,  and place it on top of a bag that matches the sample bag(empty bag). Get the lint weight.
        Remove 25 grams of lint.
        25 grams of lint is place inside a labeled 2lb bag( for HVI testing). This bag is scanned as well.
    The remaining lint is thrown away or kept.
    Repeat the process.
    We  use the error check for any samples that are +/-5

 Whenever a main page item is clicked, this screen opens to show/input details of a sample.

 Screens for parents show total, seed, lint, etc weights. Child screens only show total weight.

 When a scale connection is made the fragment automatically fills in the data on stable reads,
 if the user clicks any of the edit text the flow is interrupted and won't restart until fragment restarts.

 TODO: maybe add a save/confirm button, or freeze data if already saved
 */
@AndroidEntryPoint
class SampleFragment : BluetoothFragment(R.layout.fragment_sample), CoroutineScope by MainScope() {

    enum class FocusState(priority: Int) {
        TOTAL(0), SEED(1), LINT(2), TEST(3), WAITING(4), EDIT(5)
    }

    private val viewModel: OhausSampleViewModel by activityViewModels()
    private val sampleViewModel: SampleViewModel by viewModels()

    private var prefs: SharedPreferences? = null

    //cache of samples in database
    private var samples: List<SampleModel>? = null

    //the sample for this fragment
    private lateinit var sample: SampleModel

    private lateinit var seed: SampleModel
    private lateinit var lint: SampleModel
    private lateinit var test: SampleModel

    //sample data tv's
    private lateinit var barcodeTv: TextView

    //total, seed, and lint weight edit texts
    private lateinit var weightEt: EditText
    private lateinit var seedWeightTv: TextView
    private lateinit var seedWeightEt: EditText
    private lateinit var lintWeightTv: TextView
    private lateinit var lintWeightEt: EditText
    private lateinit var testWeightEt: EditText
    private lateinit var testWeightTv: TextView

    //timestamp text views to show when scale measure was taken
    private lateinit var totalWeightTime: TextView
    private lateinit var seedWeightTime: TextView
    private lateinit var lintWeightTime: TextView
    private lateinit var testWeightTime: TextView

    private lateinit var sampleLiveData: LiveData<List<SampleEntity>>

    private val observer = Observer<List<SampleEntity>> { data ->

        if (data != null) {

            samples = data.map { SampleModel(it) }

            updateUi()

        }
    }

    private val totalWeightListener = object : TextWatcher {

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

        override fun afterTextChanged(s: Editable?) {

            updateSampleWeight(sample, s, totalWeightTime)
        }
    }

    private val seedWeightListener = object : TextWatcher {

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

        override fun afterTextChanged(s: Editable?) {

            if (::seed.isInitialized){
                updateSampleWeight(seed, s, seedWeightTime)
            }
        }
    }

    private val lintWeightListener = object : TextWatcher {

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

        override fun afterTextChanged(s: Editable?) {

            if (::lint.isInitialized) {
                updateSampleWeight(lint, s, lintWeightTime)
            }
        }
    }

    private val testWeightListener = object : TextWatcher {

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

        override fun afterTextChanged(s: Editable?) {

            if (::test.isInitialized) {
                updateSampleWeight(test, s, testWeightTime)
            }
        }
    }

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

            Toast.makeText(
                context,
                result.contents,
                Toast.LENGTH_LONG
            ).show()

            if (::test.isInitialized) {

                test.code = result.contents
                test.scanTime = Calendar.getInstance().timeInMillis
                sampleViewModel.updateSample(test)

                findNavController().popBackStack()
            }
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
        val edit = arguments?.getBoolean("edit") == true

        if (edit) state = FocusState.EDIT
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

        //at first all fields are empty and times are "gone"
        totalWeightTime.visibility = View.GONE
        seedWeightTime.visibility = View.GONE
        lintWeightTime.visibility = View.GONE
        testWeightTime.visibility = View.GONE

        loadSamples()
    }

    //find seed/lint samples from this parent
    //if they exist: show their UI and load data
    private fun updateUi() {

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

                testWeightTv.visibility = View.VISIBLE
                testWeightEt.visibility = View.VISIBLE
                testWeightTime.visibility = View.VISIBLE
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

        weightEt.addTextChangedListener(totalWeightListener)
        lintWeightEt.addTextChangedListener(lintWeightListener)
        seedWeightEt.addTextChangedListener(seedWeightListener)
        testWeightEt.addTextChangedListener(testWeightListener)

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

        //TODO if data should be frozen
        //if (sample.weight == null) {

        //}

        startWeightListener()
    }

    private fun loadSamples() {

        sampleLiveData.observe(viewLifecycleOwner, observer)
    }

    private var lastReading = 0.0
    private var state = FocusState.TOTAL
    private fun startWeightListener() {

        val scaleId = (activity as MainActivity).getScaleId()

        viewModel.advisor = advisor

        advisor.withNearby { adapter ->

            viewModel.readWeight(requireContext(), adapter, scaleId).observe(viewLifecycleOwner) { data ->

                //println("Weight: ${data.toCharArray().joinToString(",")}")

                try {

                    val weight = data.replace(ScaleUtil.UNIT, "").toDouble()

                    val testThresh = getTestThresh()

                    //in test mode take input until 25g are taken off (TODO should really figure out their units)
                    //else take a reading between 0.0g readings
                    if (state == FocusState.TEST) {

                        if (::lint.isInitialized) {

                            val diff = (lint.weight ?: 0.0) - weight
                            //TODO used '>=' here but mainly for testing, or make a min/max thresh
                            //TODO should this save the threshed amount of the final lint ?
                            //TODO make an issue on things to ask Scientists
                            if (diff >= testThresh) {

                                testWeightEt.setText("$weight")

                                state = FocusState.WAITING

                                Toast.makeText(context, R.string.frag_sample_test_complete, Toast.LENGTH_LONG).show()

                                startBarcodeLauncher(getString(R.string.frag_sample_scan_test_label))
                            }
                        }

                    } else if (state == FocusState.EDIT) {

                        //do nothing !

                    } else if (lastReading == 0.0 && weight > 0) {

                        when (state) {

                            FocusState.TOTAL -> {

                                weightEt.setText("$weight")

                                seedWeightEt.selectAll()

                                state = FocusState.SEED
                            }

                            FocusState.SEED -> {

                                seedWeightEt.setText("$weight")

                                lintWeightEt.selectAll()

                                state = FocusState.LINT
                            }

                            FocusState.LINT -> {

                                lintWeightEt.setText("$weight")

                                testWeightEt.selectAll()

                                state = FocusState.TEST

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

    private fun getTestThresh(): Double = try {

        val value = prefs?.getString(getString(R.string.key_preferences_test_weight_threshold), "0.025") ?: "0.025"

        value.toDouble()

    } catch (e: Exception) {

        0.025 //TODO
    }

    private fun updateSampleWeight(model: SampleModel?, weight: Editable?, timeView: TextView) {

        val time = Calendar.getInstance().timeInMillis

        model?.let { m ->

            try {

                m.person = prefs?.getString(getString(R.string.key_preferences_person), null)
                m.weight = weight?.toString()?.toDouble()
                m.scaleTime = time

                timeView.text = time.toDateString()
                timeView.visibility = View.VISIBLE

                sampleViewModel.updateSample(m)

            } catch (e: NumberFormatException) {

                e.printStackTrace()

            }
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
}