package org.phenoapps.cotton.fragments

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import org.phenoapps.adapters.bluetooth.BluetoothDeviceAdapter
import org.phenoapps.cotton.R
import org.phenoapps.fragments.bluetooth.BluetoothListFragment
import org.phenoapps.models.bluetooth.BluetoothDeviceModel

@SuppressLint("MissingPermission")
class BluetoothDeviceChooserFragment: BluetoothListFragment() {

    private val devices = arrayListOf<BluetoothDevice>()

    private var deviceType = "printer"

    private var prefs: SharedPreferences? = null

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
    }

    override fun onDestroy() {
        super.onDestroy()

        activity?.unregisterReceiver(receiver)

        advisor.withNearby {
            it.cancelDiscovery()
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
}