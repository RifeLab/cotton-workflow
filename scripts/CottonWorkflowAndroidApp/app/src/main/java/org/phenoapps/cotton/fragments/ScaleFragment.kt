package org.phenoapps.cotton.fragments

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.setFragmentResult
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import org.phenoapps.cotton.R
import org.phenoapps.cotton.activities.MainActivity
import org.phenoapps.cotton.models.SampleModel
import org.phenoapps.cotton.viewmodels.OhausSampleViewModel
import org.phenoapps.fragments.bluetooth.BluetoothFragment

@AndroidEntryPoint
class ScaleFragment: BluetoothFragment(R.layout.fragment_scale), CoroutineScope by MainScope() {

    companion object {
        const val REQUEST_KEY = "org.phenoapps.fragments.scales.request"
        const val INTERRUPT_WAIT = 15000L
    }

    private val viewModel: OhausSampleViewModel by activityViewModels()

    private var prefs: SharedPreferences? = null

    private var editText: EditText? = null

    private var interrupted = false

    override fun onAttach(context: Context) {
        super.onAttach(context)
        prefs = PreferenceManager.getDefaultSharedPreferences(context)
        advisor.initialize()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        editText = view.findViewById(R.id.act_scale_et)
        val acceptButton = view.findViewById<Button>(R.id.frag_weight_sample_accept_btn)

        val sample = arguments?.getParcelable<SampleModel>("sample")

        acceptButton?.setOnClickListener {

            val weight = editText?.text?.toString()

            try {

                sample?.weight = weight?.toDouble()

            } catch (e: NumberFormatException) {

                e.printStackTrace()

            }

            if (sample?.weight != null) {

                setFragmentResult(REQUEST_KEY, bundleOf("sample" to sample))

                findNavController().popBackStack()

            } else {

                Toast.makeText(
                    context,
                    R.string.frag_scale_sample_weight_cant_be_empty, Toast.LENGTH_LONG
                ).show()
            }
        }

        editText?.setOnClickListener { _ ->
            interrupted = true
        }

        val scaleId = (activity as MainActivity).getScaleId()

        if (scaleId != null) {

            advisor.withNearby { adapter ->

                viewModel.readWeight(requireContext(), adapter, scaleId).observe(viewLifecycleOwner) { data ->

                    if (!interrupted) {

                        if (data != null && data.isNotBlank()) {

                            editText?.setText(data)

                        }
                    } else {

                        activity?.runOnUiThread {

                            Handler(Looper.getMainLooper()).postDelayed({
                                interrupted = false
                            }, INTERRUPT_WAIT)
                        }
                    }
                }
            }
        }
    }
}