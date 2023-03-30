package org.phenoapps.cotton.activities

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.Toolbar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.navigation.findNavController
import androidx.preference.PreferenceManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import dagger.hilt.android.AndroidEntryPoint
import org.phenoapps.cotton.NavigationRootDirections
import org.phenoapps.cotton.R
import org.phenoapps.cotton.interfaces.Connector
import org.phenoapps.cotton.models.SampleModel
import org.phenoapps.cotton.util.KeyboardListenerHelper
import org.phenoapps.cotton.util.VerifyPersonHelper
import org.phenoapps.cotton.viewmodels.OhausSampleViewModel
import org.phenoapps.cotton.viewmodels.SampleViewModel
import org.phenoapps.security.Security
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity: AppCompatActivity(), Connector {

    private var bottomNav: BottomNavigationView? = null

    private var topToolbar: Toolbar? = null

    private val advisor by Security().secureBluetoothActivity()

    private val keyboardListener = KeyboardListenerHelper()

    private var prefs: SharedPreferences? = null

    private var samples: List<SampleModel>? = null

    private var selectedSamples: List<SampleModel>? = null

    private val viewModel: SampleViewModel by viewModels()

    private val ohausViewModel: OhausSampleViewModel by viewModels()

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

                    //iterate over chosen parents
                    selectedSamples?.forEach { sample ->

                        writer.write(sample.toRowString())

                        writer.write("\n")

                        //iterate over its children and print as well
                        val children = samples?.filter { it.parent == sample.sid }?.sortedBy { it.type }

                        children?.forEach { child ->

                            writer.write(child.toRowString())

                            writer.write("\n")
                        }
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = PreferenceManager.getDefaultSharedPreferences(this)

        advisor.initialize()
        ohausViewModel.advisor = advisor

        setContentView(R.layout.activity_main)
        setupToolbar()
        verifyPersonHelper.setPrefNavigation {
            findNavController(R.id.nav_fragment)
                .navigate(NavigationRootDirections
                    .globalActionToPreferencesFragment(action = "person")
                )
        }

        verifyPersonHelper.checkLastOpened()

        loadData()

        val cl = findViewById<ConstraintLayout>(R.id.act_main_cl)

        keyboardListener.connect(cl) { vis -> onKeyboardVisibilityChanged(vis) }
    }

    private fun onKeyboardVisibilityChanged(vis: Boolean) {

        bottomNav?.visibility = if (vis) View.GONE else View.VISIBLE

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

                    showExportDialog()

                    false
                }

                else -> false
            }
        }

        topToolbar?.setOnMenuItemClickListener {

            when (it.itemId) {

//                R.id.action_menu_main_top_printer -> {
//
//                    findNavController(R.id.nav_fragment)
//                        .navigate(NavigationRootDirections.globalActionToDeviceChooserFragment("printer"))
//
//                    true
//                }

                R.id.action_menu_main_top_scale -> {

                    disconnectGatt()

                    findNavController(R.id.nav_fragment)
                        .navigate(NavigationRootDirections.globalActionToDeviceChooserFragment("scale"))

                    true
                }

                else -> false
            }
        }

        updateToolbarStatus(SCALE, false)
        updateToolbarStatus(PRINTER, false)

    }

    private fun showExportDialog() {

        val parents = samples?.filter { it.parent == null }

        val scanTime = Calendar.getInstance().timeInMillis

        val formatter = SimpleDateFormat.getDateTimeInstance()

        val timestamp = formatter.format(scanTime)
            .replace(Regex("[:/, ]"), "_")

        // Create AlertDialog
        val builder = AlertDialog.Builder(this)

        // Set title
        builder.setTitle(getString(R.string.act_main_select_sample_export_title))

        // Set multiple choice items
        val selectedModels = arrayListOf<SampleModel>()

        parents?.let { selectedModels.addAll(it) }

        val checked = parents?.map { true }?.toBooleanArray()
        builder.setMultiChoiceItems(parents?.map { it.code  }?.toTypedArray(), checked) { _, which, isChecked ->
            // Handle selection
            val selectedModel = selectedModels[which]
            if (isChecked) {
                selectedModels.add(selectedModel)
            } else {
                selectedModels.remove(selectedModel)
            }
        }

        builder.setPositiveButton(android.R.string.ok) { _, _ ->

            saveLauncher.launch("output_$timestamp.csv")

        }

        builder.setNegativeButton(android.R.string.cancel) { dialog, _ ->

            dialog.dismiss()

        }

        builder.setOnDismissListener {
            selectedSamples = selectedModels
        }

        val dialog = builder.create()
        dialog.show()
    }

    private fun startConnectionCheck(key: Int) {

        val scaleId = getScaleId()

        if (scaleId != null) {

            advisor.withNearby { adapter ->

                ohausViewModel.reach(this, adapter, scaleId).observe(this) { status: Boolean ->

                    updateToolbarStatus(SCALE, status)

                }
            }
        }
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

    private fun updateToolbarStatus(key: Int, connected: Boolean) {

        runOnUiThread {
            when (key) {
                SCALE -> {
                    topToolbar?.menu?.getItem(0)?.setIcon(
                        if (connected) R.drawable.scale else R.drawable.scale_off
                    )
                }
//                PRINTER -> {
//                    topToolbar?.menu?.getItem(1)?.setIcon(
//                        if (connected) R.drawable.printer_outline else R.drawable.printer_off
//                    )
//                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun isConnected(address: String?): Boolean {

        //val printerId = getPrinterId()
        val scaleId = getScaleId()

//        if (printerId == address) {
//
//            return (topToolbar?.menu?.getItem(1)?.icon == AppCompatResources.getDrawable(this, R.drawable.printer_outline))
//
//        }

        if (scaleId == address) {

            return (topToolbar?.menu?.getItem(0)?.icon == AppCompatResources.getDrawable(this, R.drawable.scale))

        }

        return false
    }

    override fun getPrinterId(): String? = prefs?.getString(getString(R.string.key_printer_device_id), null)
    override fun getScaleId(): String? = prefs?.getString(getString(R.string.key_scale_device_id), null)
    override fun getPerson(): String? = prefs?.getString(getString(R.string.key_preferences_person), "") ?: ""

    override fun onPause() {
        super.onPause()
        verifyPersonHelper.updateLastOpenedTime()
        ohausViewModel.disconnect()
        updateToolbarStatus(SCALE, false)
    }

    override fun onResume() {
        super.onResume()
        reconnect()
    }

    @SuppressLint("MissingPermission")
    fun disconnectGatt() {

        ohausViewModel.disconnect()

        updateToolbarStatus(SCALE, false)
    }

    fun reconnect() {

        startConnectionCheck(SCALE)

    }
}