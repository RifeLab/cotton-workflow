package org.phenoapps.cotton.fragments

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import dagger.hilt.android.AndroidEntryPoint
import org.phenoapps.cotton.R
import org.phenoapps.cotton.models.SampleModel
import org.phenoapps.cotton.util.PrintThread
import org.phenoapps.fragments.bluetooth.BluetoothFragment

@AndroidEntryPoint
class PrintFragment: BluetoothFragment(R.layout.fragment_print) {

    private var prefs: SharedPreferences? = null

    private var editText: EditText? = null

    private var connectButton: FloatingActionButton? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        prefs = PreferenceManager.getDefaultSharedPreferences(context)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        editText = view.findViewById(R.id.frag_print_et)
        connectButton = view.findViewById(R.id.frag_print_sample_connect_fab)
        val acceptButton = view.findViewById<Button>(R.id.frag_print_sample_accept_btn)

        val sample = arguments?.getParcelable<SampleModel>("sample")
        editText?.setText(sample?.code ?: String())

        acceptButton?.setOnClickListener {

            val code = editText?.text?.toString()

            if (code != null && code.isNotBlank() && "*" !in code && code.length <= 10) {

                val printerId = prefs?.getString(getString(R.string.key_printer_device_id), null)
                if (printerId == null) {

                    Toast.makeText(context, R.string.frag_print_no_id_set, Toast.LENGTH_LONG).show()

                } else {

                    checkConnectionAndPrint(printerId, code)

                }
            }
        }

        connectButton?.setOnClickListener {

            findNavController().navigate(ScaleFragmentDirections
                .globalActionToDeviceChooserFragment("printer"))

        }
    }

    /**
     * Attempts to connect to the printer.
     * If a connection is successful, starts a print thread.
     * Otherwise, tells the user to go to connect screen.
     */
    @SuppressLint("MissingPermission")
    private fun checkConnectionAndPrint(address: String, code: String) {

        context?.let { ctx ->

            advisor.withNearby { adapter ->

                try {

                    val device = adapter.getRemoteDevice(address)

                    device.connectGatt(context, false, object : BluetoothGattCallback() {

                        override fun onConnectionStateChange(
                            gatt: BluetoothGatt?,
                            status: Int,
                            newState: Int
                        ) {
                            super.onConnectionStateChange(gatt, status, newState)

                            when (status) {

                                BluetoothGatt.STATE_CONNECTED -> {

                                    gatt?.close()

                                }
                                133 -> { //error

                                    activity?.runOnUiThread {

                                        Toast.makeText(context, R.string.frag_print_cant_connect_to_device, Toast.LENGTH_LONG).show()

                                        gatt?.close()
                                    }

                                }
                                BluetoothGatt.STATE_DISCONNECTED -> {

                                    PrintThread(address).print(arrayOf(code))

                                    findNavController().navigate(PrintFragmentDirections
                                        .globalActionToHomeFragment())
                                }
                            }
                        }
                    })

                } catch (e: Exception) {

                    e.printStackTrace()

                    Toast.makeText(context, R.string.frag_print_connection_failed, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}