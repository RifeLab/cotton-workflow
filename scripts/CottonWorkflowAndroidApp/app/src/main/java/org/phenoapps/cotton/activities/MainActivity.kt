package org.phenoapps.cotton.activities

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.Toolbar
import androidx.core.content.res.ResourcesCompat
import androidx.core.os.bundleOf
import androidx.core.view.get
import androidx.fragment.app.viewModels
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import dagger.hilt.android.AndroidEntryPoint
import org.phenoapps.cotton.NavigationRootDirections
import org.phenoapps.cotton.R
import org.phenoapps.cotton.fragments.PrintFragmentDirections
import org.phenoapps.cotton.interfaces.Connector
import org.phenoapps.cotton.models.SampleModel
import org.phenoapps.cotton.util.PrintThread
import org.phenoapps.cotton.util.VerifyPersonHelper
import org.phenoapps.cotton.viewmodels.SampleListViewModel
import org.phenoapps.security.Security
import java.io.FileWriter
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity: AppCompatActivity(), Connector {

    private var bottomNav: BottomNavigationView? = null

    private var topToolbar: Toolbar? = null

    private val advisor by Security().secureBluetoothActivity()

    private var prefs: SharedPreferences? = null

    private val gatts = hashMapOf<String, BluetoothGatt>()

    private var reconnecting = false

    private val connectionHandlerThread = HandlerThread("connection status")

    private var samples: List<SampleModel>? = null

    private val viewModel: SampleListViewModel by viewModels()

    companion object {
        const val PRINTER = 0
        const val SCALE = 1
        const val CONNECTION_CHECK_INTERVAL = 1000L
    }

    @Inject
    lateinit var verifyPersonHelper: VerifyPersonHelper

    private val saveLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->

        uri?.let { fileUri ->

            contentResolver.openOutputStream(fileUri).use {

                OutputStreamWriter(it).use { writer ->

                    writer.write("id, barcode, weight, scan_time, scale_time, person, parent\n")

                    samples?.forEach { sample ->

                        writer.write(sample.toRowString())

                        writer.write("\n")
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        advisor.initialize()
        connectionHandlerThread.start()
        setContentView(R.layout.activity_main)
        setupToolbar()
        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        verifyPersonHelper.setPrefNavigation {
            findNavController(R.id.nav_fragment)
                .navigate(NavigationRootDirections
                    .globalActionToPreferencesFragment(action = "person")
                )
        }
        loadData()
    }

    private fun loadData() {

        viewModel.getSamples().observe(this) { samples ->

            if (samples != null) {

                //cache all samples
                this.samples = samples.map { SampleModel(it) }

            }
        }
    }

    //bottom toolbar
    private fun setupToolbar() {

        topToolbar = findViewById(R.id.act_main_top_tb)
        bottomNav = findViewById(R.id.act_main_bot_tb)

        bottomNav?.isSelected = false
        bottomNav?.setOnItemSelectedListener {

            when(it.itemId) {

                R.id.action_menu_main_bot_tb_home -> {

                    findNavController(R.id.nav_fragment)
                        .navigate(NavigationRootDirections.globalActionToHomeFragment())

                    true
                }
                R.id.action_menu_main_bot_tb_settings -> {

                    findNavController(R.id.nav_fragment)
                        .navigate(NavigationRootDirections.globalActionToPreferencesFragment())

                    true
                }

                R.id.action_menu_main_bot_tb_save -> {

                    val scanTime = Calendar.getInstance().timeInMillis

                    val formatter = SimpleDateFormat.getDateTimeInstance()

                    val timestamp = formatter.format(scanTime)
                        .replace(Regex("[:/, ]"), "_")

                    saveLauncher.launch("output_$timestamp.csv")

                    true
                }

                else -> false
            }
        }

        topToolbar?.setOnMenuItemClickListener {

            when (it.itemId) {

                R.id.action_menu_main_top_printer -> {

                    startReconnect(getPrinterId())

                    findNavController(R.id.nav_fragment)
                        .navigate(NavigationRootDirections.globalActionToDeviceChooserFragment("printer"))

                    true
                }

                R.id.action_menu_main_top_scale -> {

                    startReconnect(getScaleId())

                    findNavController(R.id.nav_fragment)
                        .navigate(NavigationRootDirections.globalActionToDeviceChooserFragment("scale"))

                    true
                }

                else -> false
            }
        }

        startConnectionCheck(PRINTER)
    }

    @SuppressLint("MissingPermission")
    private fun startReconnect(address: String?) {
        reconnecting = true
        gatts[address].also { gatt ->
            advisor.withNearby {
                gatt?.disconnect()
                gatt?.close()
            }
        }
    }

    private fun startConnectionCheck(key: Int) {

        val printerId = getPrinterId()
        val scaleId = getScaleId()

        Handler(connectionHandlerThread.looper).postDelayed({

            when (key) {

                PRINTER -> {

                    if (printerId != null && !reconnecting) {

                        checkConnection(PRINTER, printerId)

                    } else updateToolbarStatus(PRINTER, false)
                }

                SCALE -> {

                    if (scaleId != null && !reconnecting) {

                        checkConnection(SCALE, scaleId)

                    } else updateToolbarStatus(SCALE, false)
                }
            }

            startConnectionCheck(if (key == PRINTER) SCALE else PRINTER)

        }, CONNECTION_CHECK_INTERVAL)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when (findNavController(R.id.nav_fragment).currentDestination?.id ?: -1) {
            R.id.fragment_device_chooser -> {
                findNavController(R.id.nav_fragment).popBackStack()
            }
            R.id.fragment_sample_list -> {
                finish()
            }
            else -> {
                super.onBackPressed()
                bottomNav?.selectedItemId = R.id.action_menu_main_bot_tb_home
            }
        }
    }

    override fun onResumeFragments() {
        super.onResumeFragments()
        reconnecting = false
    }

    @SuppressLint("MissingPermission")
    private fun checkConnection(key: Int, address: String) {

        if (!reconnecting) {

            advisor.withNearby { adapter ->

                try {

                    val device = adapter.getRemoteDevice(address)

                    if (address in gatts) gatts[address]?.also {
                        it.disconnect()
                        it.close()
                    }

                    gatts[address] = device.connectGatt(this, false, object : BluetoothGattCallback() {

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

                                    updateToolbarStatus(key, false)

                                    gatt?.close()

                                }
                                BluetoothGatt.STATE_DISCONNECTED -> {

                                    updateToolbarStatus(key, true)
                                }
                            }
                        }
                    })

                } catch (e: Exception) {

                    e.printStackTrace()

                    updateToolbarStatus(key, false)
                }
            }
        }
    }

    private fun updateToolbarStatus(key: Int, connected: Boolean) {

        runOnUiThread {
            when (key) {
                SCALE -> {
                    topToolbar?.menu?.getItem(0)?.setIcon(
                        if (connected) R.drawable.scale else R.drawable.scale_off
                    )
                }
                PRINTER -> {
                    topToolbar?.menu?.getItem(1)?.setIcon(
                        if (connected) R.drawable.printer_outline else R.drawable.printer_off
                    )
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun isConnected(address: String?): Boolean {

        val printerId = getPrinterId()
        val scaleId = getScaleId()

        if (printerId == address) {

            return (topToolbar?.menu?.getItem(1)?.icon == AppCompatResources.getDrawable(this, R.drawable.printer_outline))

        }

        if (scaleId == address) {

            return (topToolbar?.menu?.getItem(0)?.icon == AppCompatResources.getDrawable(this, R.drawable.printer_outline))

        }

        return false
    }

    override fun reconnect() {
        reconnecting = false
    }

    override fun getPrinterId(): String? = prefs?.getString(getString(R.string.key_printer_device_id), null)
    override fun getScaleId(): String? = prefs?.getString(getString(R.string.key_scale_device_id), null)
    override fun getPerson(): String? = prefs?.getString(getString(R.string.key_preferences_person), "") ?: ""

    @SuppressLint("MissingPermission")
    fun disconnectGatt(address: String) {
        advisor.withNearby {
            gatts[address]?.disconnect()
            gatts[address]?.close()
            gatts.remove(address)
        }
    }

    override fun onPause() {
        super.onPause()
        verifyPersonHelper.updateLastOpenedTime()
    }

    override fun onResume() {
        super.onResume()
        verifyPersonHelper.checkLastOpened()
    }

    @SuppressLint("MissingPermission")
    override fun onDestroy() {
        super.onDestroy()
        advisor.withNearby {
            gatts.forEach { g ->
                g.value.disconnect()
                g.value.close()
            }
        }
    }
}