package org.phenoapps.cotton.activities

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
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
import org.phenoapps.cotton.fragments.SampleFragment
import org.phenoapps.cotton.fragments.SampleWorkflowFragment
import org.phenoapps.cotton.interfaces.Connector
import org.phenoapps.cotton.interfaces.MainToolbarManager
import org.phenoapps.cotton.models.SampleModel
import org.phenoapps.cotton.util.DateUtil
import org.phenoapps.cotton.util.DateUtil.Companion.toDateString
import org.phenoapps.cotton.util.KeyboardListenerHelper
import org.phenoapps.cotton.util.VerifyPersonHelper
import org.phenoapps.cotton.util.WorkflowUtil
import org.phenoapps.cotton.viewmodels.OhausSampleViewModel
import org.phenoapps.cotton.viewmodels.SampleViewModel
import org.phenoapps.security.Security
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import kotlin.math.abs

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), Connector, MainToolbarManager {

    private var bottomNav: BottomNavigationView? = null

    private var topToolbar: Toolbar? = null

    private val advisor by Security().secureBluetoothActivity()

    private val keyboardListener = KeyboardListenerHelper()

    private var prefs: SharedPreferences? = null

    private var samples: List<SampleModel>? = null

    private val viewModel: SampleViewModel by viewModels()

    private val ohausViewModel: OhausSampleViewModel by viewModels()

    private var menu: Menu? = null

    companion object {
        const val PRINTER = 0
        const val SCALE = 1
        const val CONNECTION_CHECK_INTERVAL = 1000L
        const val BACK_BUTTON_THRESH = 2000L
    }

    @Inject
    lateinit var verifyPersonHelper: VerifyPersonHelper

    private val saveLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->

            uri?.let { fileUri ->

                contentResolver.openOutputStream(fileUri).use {

                    OutputStreamWriter(it).use { writer ->

                        writer.write("Barcode, See Cotton Wt, Fuzzy Seed Wt, Lint Wt, HVI Sample No., Timestamp, Error, Notes\n")

                        //iterate over chosen parents
                        samples?.forEach { sample ->

                            //iterate over its children and print as well
                            samples?.filter { it.parent == sample.sid }?.sortedBy { it.type }?.let { children ->

                                if (children.size == WorkflowUtil.NumSubSamples) {

                                    val seed = children[0]
                                    val lint = children[1]
                                    val test = children[2]

                                    if (sample.weight != null && lint.weight != null && seed.weight != null) {

                                        val doubleError =
                                            abs(sample.weight!! - (lint.weight!! + seed.weight!!))
                                        var error = doubleError.toString()

                                        val length = error.length

                                        if (length > 5) {

                                            error = error.substring(0..5)
                                        }

                                        //TODO serial input preference
                                        //TODO Ohaus 3000 technical output
                                        writer.write("\"${sample.code}\", \"${sample.weight}\", \"${seed.weight}\", \"${lint.weight}\", \"${test.code}\", \"${sample.scanTime?.toDateString()}\", \"${error}\", \"${sample.note?.replace("\"", "\"\"")}\"\n")

                                    } else {

                                        writer.write("\"${sample.code}\", \"${sample.weight}\", \"${seed.weight}\", \"${lint.weight}\", \"${test.code}\", \"${sample.scanTime?.toDateString()}\", null, \"${sample.note?.replace("\"", "\"\"")}\"\n")

                                    }
                                }
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
                .navigate(
                    NavigationRootDirections
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

            when (it.itemId) {

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

        updateToolbarStatus(SCALE, false)
        updateToolbarStatus(PRINTER, false)

        setSupportActionBar(topToolbar)

        if (supportActionBar != null) {
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.setDisplayShowHomeEnabled(true)
            supportActionBar?.setDisplayShowTitleEnabled(false)
            topToolbar?.inflateMenu(R.menu.menu_main_top_toolbar)
        }
    }


    private fun resolveBackButtonPressed() {

        when (findNavController(R.id.nav_fragment).currentDestination?.id
            ?: R.id.fragment_sample_list) {

            R.id.fragment_sample_list -> {

                backPressedTime =
                    if (backPressedTime + BACK_BUTTON_THRESH > System.currentTimeMillis()) {

                        finish()

                        0L

                    } else {

                        Toast.makeText(
                            this,
                            getString(R.string.act_main_press_back_twice),
                            Toast.LENGTH_SHORT
                        ).show()

                        System.currentTimeMillis()
                    }
            }
            R.id.fragment_device_chooser -> {

                findNavController(R.id.nav_fragment)
                    .popBackStack()
            }
            R.id.fragment_sample -> {

                supportFragmentManager.findFragmentById(R.id.nav_fragment)
                    ?.childFragmentManager?.fragments?.find { it is SampleFragment }.let {

                        (it as? SampleFragment)?.resolveBackPress()

                    }
            }
            R.id.fragment_workflow -> {

                supportFragmentManager.findFragmentById(R.id.nav_fragment)
                    ?.childFragmentManager?.fragments?.find { it is SampleWorkflowFragment }.let {

                        (it as? SampleWorkflowFragment)?.resolveBackPress()

                    }
            }
            R.id.fragment_scale_config_help -> {

                findNavController(R.id.nav_fragment)
                    .popBackStack()

            }
            else -> {

                bottomNav?.selectedItemId = R.id.action_menu_main_bot_tb_home

            }
        }
    }

    //required to override this when using support action bar
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main_top_toolbar, menu)
        updateToolbarVisibility()
        return super.onCreateOptionsMenu(menu)
    }

    //track if back button is pressed twice to exit app
    var backPressedTime = 0L

    //support back button on toolbar to navigate back to home or close app
    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        when (item.itemId) {

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

            }

            R.id.action_menu_main_top_workflow -> {

                when (findNavController(R.id.nav_fragment).currentDestination?.id
                    ?: R.id.fragment_sample_list) {

                    R.id.fragment_workflow, R.id.fragment_sample -> {

                        supportFragmentManager.findFragmentById(R.id.nav_fragment)
                            ?.childFragmentManager?.fragments?.find { it is SampleFragment }.let {

                                (it as? SampleFragment)?.resolveWorkflow()

                            }
                    }
                }
            }

            R.id.action_menu_main_top_delete -> {

                when (findNavController(R.id.nav_fragment).currentDestination?.id
                    ?: R.id.fragment_sample_list) {

                    R.id.fragment_workflow, R.id.fragment_sample -> {

                        supportFragmentManager.findFragmentById(R.id.nav_fragment)
                            ?.childFragmentManager?.fragments?.find { it is SampleFragment }.let {

                                (it as? SampleFragment)?.resolveDelete()

                            }
                    }
                }
            }

            R.id.action_menu_main_top_note -> {

                when (findNavController(R.id.nav_fragment).currentDestination?.id
                    ?: R.id.fragment_sample_list) {

                    R.id.fragment_workflow, R.id.fragment_sample -> {

                        supportFragmentManager.findFragmentById(R.id.nav_fragment)
                            ?.childFragmentManager?.fragments?.find { it is SampleFragment }.let {

                                (it as? SampleFragment)?.resolveNote()

                            }
                    }
                }
            }

            android.R.id.home -> {

                resolveBackButtonPressed()

            }
        }

        return super.onOptionsItemSelected(item)
    }

    private fun showExportDialog() {

        val filePrefix = getString(R.string.cotton_file_prefix)

        val parents = samples?.filter { it.parent == null }

        val exportTime = Calendar.getInstance().timeInMillis

        val timestamp = exportTime.toDateString()

        // Create AlertDialog
        val builder = AlertDialog.Builder(this)

        // Set title
        builder.setTitle(getString(R.string.act_main_sample_export_title))

        builder.setMessage(
            getString(
                R.string.act_main_sample_export_message,
                (parents?.size ?: 0).toString()
            )
        )

        builder.setPositiveButton(R.string.save) { _, _ ->

            saveLauncher.launch("$filePrefix$timestamp.csv")

        }

        builder.setNegativeButton(android.R.string.cancel) { dialog, _ ->

            dialog.dismiss()

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

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        this.menu = menu
        return super.onPrepareOptionsMenu(menu)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {

        //press the support action back button
        resolveBackButtonPressed()
    }

    private fun updateToolbarStatus(key: Int, connected: Boolean) {

        runOnUiThread {
            when (key) {
                SCALE -> {
                    println(topToolbar?.menu?.size())
                    topToolbar?.menu?.findItem(R.id.action_menu_main_top_scale)?.setIcon(
                        if (connected) R.drawable.scale else R.drawable.scale_off
                    )
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun isConnected(address: String?): Boolean {

        val scaleId = getScaleId()

        if (scaleId == address) {

            return (topToolbar?.menu?.findItem(R.id.action_menu_main_top_scale)?.icon
                    == AppCompatResources.getDrawable(this, R.drawable.scale))

        }

        return false
    }

    override fun getPrinterId(): String? =
        prefs?.getString(getString(R.string.key_printer_device_id), null)

    override fun getScaleId(): String? =
        prefs?.getString(getString(R.string.key_scale_device_id), null)

    override fun getPerson(): String? =
        prefs?.getString(getString(R.string.key_preferences_person), "") ?: ""

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

    override fun updateToolbarVisibility() {

        invalidateOptionsMenu()

        when (findNavController(R.id.nav_fragment).currentDestination?.id) {

            R.id.fragment_sample_list -> {

                supportActionBar?.setDisplayHomeAsUpEnabled(false)
                menu?.findItem(R.id.action_menu_main_top_delete)?.isVisible = false
                menu?.findItem(R.id.action_menu_main_top_workflow)?.isVisible = false
                menu?.findItem(R.id.action_menu_main_top_note)?.isVisible = false

            }

            R.id.fragment_sample, R.id.fragment_workflow -> {

                supportActionBar?.setDisplayHomeAsUpEnabled(true)
                menu?.findItem(R.id.action_menu_main_top_delete)?.isVisible = true
                menu?.findItem(R.id.action_menu_main_top_workflow)?.isVisible = true
                menu?.findItem(R.id.action_menu_main_top_note)?.isVisible = true

            }

            else -> {

                supportActionBar?.setDisplayHomeAsUpEnabled(true)
                menu?.findItem(R.id.action_menu_main_top_delete)?.isVisible = false
                menu?.findItem(R.id.action_menu_main_top_workflow)?.isVisible = false
                menu?.findItem(R.id.action_menu_main_top_note)?.isVisible = false

            }
        }
    }
}