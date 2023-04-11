package org.phenoapps.cotton.fragments

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
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
import org.phenoapps.cotton.interfaces.MainToolbarManager
import org.phenoapps.fragments.bluetooth.BluetoothListFragment
import org.phenoapps.models.bluetooth.BluetoothDeviceModel

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

        val manager = activity?.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = manager.adapter

        if (adapter == null) {

            Toast.makeText(requireContext(), R.string.frag_device_chooser_no_bt, Toast.LENGTH_LONG).show()
            findNavController().popBackStack()

        } else if (!adapter.isEnabled) {

            startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))

        } else {

            advisor.withNearby {
                it.startDiscovery()
            }

            deviceType = arguments?.getString("deviceType") ?: "printer"

            startResetCheck()
        }
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

        } catch (e: Exception) {

            e.printStackTrace()

        }
    }

    override fun onItemClicked(model: BluetoothDeviceModel) {
        //super.onItemClicked(model)

        prefs?.edit()?.putString(getString(when(deviceType) {
            "printer" -> {
                R.string.key_printer_device_id
            }
            else -> {
                R.string.key_scale_device_id
            }
        }), model.device.address)?.apply()

        (activity as MainActivity).reconnect()

        findNavController().popBackStack()
    }

    override fun onRecyclerReady() {

        try {

            advisor.connectWith { bonded ->

                (mRecyclerView.adapter as? BluetoothDeviceAdapter)
                    ?.submitList((bonded + devices).map { BluetoothDeviceModel(it) })
            }

        } catch (_: NullPointerException) {}
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

    override fun onResume() {
        super.onResume()
        (activity as MainToolbarManager).updateToolbarVisibility()
    }
}