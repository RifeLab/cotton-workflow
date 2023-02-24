package org.phenoapps.cotton.fragments

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.google.android.material.snackbar.Snackbar
import org.phenoapps.adapters.bluetooth.BluetoothDeviceAdapter
import org.phenoapps.cotton.R
import org.phenoapps.cotton.activities.MainActivity
import org.phenoapps.fragments.bluetooth.BluetoothListFragment
import org.phenoapps.models.bluetooth.BluetoothDeviceModel
import org.phenoapps.security.Security

@SuppressLint("MissingPermission")
class BluetoothDeviceChooserFragment: BluetoothListFragment() {

    companion object {
        const val DELAY_TIME_FOR_CONNECTION_HELPER_MESSAGE = 15000L
    }

    private val devices = arrayListOf<BluetoothDevice>()

    private var deviceType = "printer"

    private var prefs: SharedPreferences? = null

    init {
        advisor.initialize()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        prefs = PreferenceManager.getDefaultSharedPreferences(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        activity?.registerReceiver(receiver, filter)

        advisor.withNearby {
            it.startDiscovery()
        }

        deviceType = arguments?.getString("deviceType") ?: "printer"

        startResetCheck()

    }

    private fun startResetCheck() {
        Handler(Looper.getMainLooper()).postDelayed({
            activity?.runOnUiThread {
                view?.let { v ->
                    val snack = Snackbar.make(v, R.string.frag_device_chooser_help_message, Snackbar.LENGTH_LONG)
                    snack.setAction(R.string.frag_device_chooser_reset_message) {
                        advisor.withNearby {
                            it.cancelDiscovery()
                            it.startDiscovery()
                        }
                        startResetCheck()
                    }
                    snack.show()
                }
            }
        }, DELAY_TIME_FOR_CONNECTION_HELPER_MESSAGE)
    }

    override fun onDestroy() {
        super.onDestroy()

        activity?.unregisterReceiver(receiver)

        try {
            advisor.withNearby {
                it.cancelDiscovery()
            }
        } catch (e: IllegalStateException) {
            e.printStackTrace()
        }
    }

    override fun onItemClicked(model: BluetoothDeviceModel) {
        super.onItemClicked(model)

        prefs?.edit()?.putString(getString(when(deviceType) {
            "printer" -> {
                R.string.key_printer_device_id
            }
            else -> {
                R.string.key_scale_device_id
            }
        }), model.device.address)?.apply()

        findNavController().popBackStack()
    }

    override fun onRecyclerReady() {

        advisor.connectWith { bonded ->

            Toast.makeText(context, "Loading ${devices.size} devices...", Toast.LENGTH_SHORT).show()

            (mRecyclerView.adapter as? BluetoothDeviceAdapter)
                ?.submitList((bonded + devices).map { BluetoothDeviceModel(it) })

        }
    }

    // Create a BroadcastReceiver for ACTION_FOUND.
    private val receiver = object : BroadcastReceiver() {

        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {

            advisor.withNearby {

                when(intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {

                        // Discovery has found a device. Get the BluetoothDevice
                        // object and its info from the Intent.
                        val device: BluetoothDevice? =
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)

                        val deviceName = device?.name
                        val deviceHardwareAddress = device?.address // MAC address

                        if (deviceName != null && deviceHardwareAddress != null) {

                            if (deviceName !in devices.map { it.name }) {

                                devices.add(device)

                                onRecyclerReady()
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        (activity as MainActivity).reconnect()
    }
}