package org.phenoapps.cotton.fragments

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import org.phenoapps.cotton.R
import org.phenoapps.cotton.models.SampleModel
import org.phenoapps.fragments.bluetooth.BluetoothFragment
import org.phenoapps.security.Security
import org.phenoapps.viewmodels.scales.OhausViewModel

@AndroidEntryPoint
class ScaleFragment: BluetoothFragment(R.layout.fragment_scale), CoroutineScope by MainScope() {

    companion object {
        const val REQUEST_KEY = "org.phenoapps.fragments.scales.request"
    }

    private val viewModel: OhausViewModel by viewModels()

    private var cachedRead: String? = null

    private var prefs: SharedPreferences? = null

    private var editText: EditText? = null
    private var connectButton: FloatingActionButton? = null

    //its possible that ohaus only sends first half of string keep this in var
    private var firstHalf: String = ""

    override fun onAttach(context: Context) {
        super.onAttach(context)
        prefs = PreferenceManager.getDefaultSharedPreferences(context)
        advisor.initialize()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        editText = view.findViewById(R.id.act_scale_et)
        connectButton = view.findViewById(R.id.frag_weight_sample_connect_fab)
        val acceptButton = view.findViewById<Button>(R.id.frag_weight_sample_accept_btn)

        val sample = arguments?.getParcelable<SampleModel>("sample")

        acceptButton?.setOnClickListener {

            var weight = cachedRead
            if (weight == null) {
                weight = editText?.text?.toString()
            }

            sample?.weight = weight

            setFragmentResult(REQUEST_KEY, bundleOf("sample" to sample))

            findNavController().popBackStack()

        }

        connectButton?.setOnClickListener {

            findNavController().navigate(ScaleFragmentDirections
                .globalActionToDeviceChooserFragment("scale"))

        }

        startSearch()

    }

    private fun startSearch() {

        launch {

            while (!viewModel.isConnected()) {
                startConnection()
                delay(5000L)
            }

            startObserving()

        }
    }

    private fun stopSearch() {
        //cancel()
    }

    private fun startConnection() {

        context?.let { ctx ->

            advisor.withNearby { adapter ->

                val scaleId = prefs?.getString(getString(R.string.key_scale_device_id), null)

                if (scaleId != null) {

                    viewModel.register(adapter, ctx, scaleId)

                }
            }
        }
    }

    private fun startObserving() {

        advisor.withNearby {

            viewModel.scaleReading.observe(viewLifecycleOwner) { scaleReading ->

                if (scaleReading != null && scaleReading.isNotBlank()) {

                    val readable = scaleReading.replace(" ", "").trim()

                    //the metric, in this case always 'g' will be the end of the string
                    //but ohaus potentially splits it into multiple readings
                    if (!readable.endsWith("g")) {

                        firstHalf = readable

                    } else {

                        cachedRead = "$firstHalf$readable"

                        if (cachedRead != "g" && cachedRead?.contains(".") == true) {

                            editText?.setText(cachedRead)

                        }

                        firstHalf = String()

                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopSearch()
    }
}