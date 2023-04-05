package org.phenoapps.cotton.fragments

import android.annotation.SuppressLint
import android.content.*
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.preference.CheckBoxPreference
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import dagger.hilt.android.AndroidEntryPoint
import org.phenoapps.cotton.R
import org.phenoapps.cotton.activities.MainActivity
import org.phenoapps.cotton.models.SampleModel
import org.phenoapps.cotton.util.BarcodeUtil.Companion.toCode39
import org.phenoapps.cotton.viewmodels.SampleViewModel
import org.phenoapps.security.Security

@AndroidEntryPoint
class PreferencesFragment: PreferenceFragmentCompat() {

    companion object {

        const val PRINTER_DEVICE_CHOICE = 0
        const val SCALE_DEVICE_CHOICE = 1

    }

    private val advisor by Security().secureBluetooth()

    private val viewModel: SampleViewModel by viewModels()

    private var prefs: SharedPreferences? = null

    private var samples = listOf<SampleModel>()

    init {
        advisor.initialize()
    }

    private fun updateThresholdVisibility(value: Boolean? = null) {

        val pref = findPreference<CheckBoxPreference>(getString(R.string.key_preferences_error_threshold))

        if (pref != null) {

            findPreference<EditTextPreference>(getString(R.string.key_preferences_error_check_threshold))
                ?.isVisible = value ?: pref.isChecked

        }
    }

    private fun updatePersonSummary(value: String? = null) {

        val pref = findPreference<EditTextPreference>(getString(R.string.key_preferences_person))

        pref?.summary = value ?: prefs?.getString(getString(R.string.key_preferences_person),
            getString(R.string.pref_person_summary))
    }

    private fun updateDeviceAddressSummary(choice: Int) {

        val preference = findPreference<Preference>(getString(when (choice) {
            PRINTER_DEVICE_CHOICE -> {
                R.string.key_preferences_printer_device_id
            }
            else -> R.string.key_preferences_scale_device_id
        }))

        preference?.summary = prefs?.getString(getString(when(choice) {
            PRINTER_DEVICE_CHOICE -> {
                R.string.key_printer_device_id
            }
            else -> R.string.key_scale_device_id
        }), String())
    }

    private fun updateStartIdSummary() {

        val preference = findPreference<EditTextPreference>(getString(R.string.key_preferences_workflow_id_start))

        if (preference != null) {

            val id = prefs?.getString(getString(R.string.key_preferences_workflow_id_start), "0") ?: "0"

            preference.summary = getString(R.string.preferences_workflow_start_id_summary, id.toCode39())

        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        prefs = PreferenceManager.getDefaultSharedPreferences(context)
    }

    @SuppressLint("MissingPermission")
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {

        context?.let { ctx ->

            addPreferencesFromResource(R.xml.preferences)

            val clearDeviceIdPreference =
                findPreference<Preference>(getString(R.string.key_preferences_device_id_clear))

            findPreference<Preference>(getString(R.string.key_preferences_scale_config_help))?.setOnPreferenceClickListener {

                findNavController().navigate(
                    PreferencesFragmentDirections
                        .actionToScaleConfigHelp()
                )

                true
            }

            findPreference<CheckBoxPreference>(getString(R.string.key_preferences_error_threshold))?.setOnPreferenceChangeListener { _, value ->

                updateThresholdVisibility(value as Boolean)

                true
            }

            findPreference<Preference>(getString(R.string.key_preferences_printer_device_id))?.setOnPreferenceClickListener {

                findNavController().navigate(
                    PreferencesFragmentDirections
                        .globalActionToDeviceChooserFragment(deviceType = "printer")
                )

                true
            }

            findPreference<Preference>(getString(R.string.key_preferences_scale_device_id))?.setOnPreferenceClickListener {

                findNavController().navigate(
                    PreferencesFragmentDirections
                        .globalActionToDeviceChooserFragment(deviceType = "scale")
                )

                true
            }

            findPreference<EditTextPreference>(getString(R.string.key_preferences_workflow_id_start))?.setOnPreferenceChangeListener { preference, newValue ->

                if ((newValue as String).length > 10) {

                    Toast.makeText(context, R.string.preferences_workflow_id_to_long_error, Toast.LENGTH_LONG).show()

                    return@setOnPreferenceChangeListener false
                }

                val paddedId = newValue.toCode39()

                return@setOnPreferenceChangeListener try {

                    if (samples.any { it.code == paddedId }) throw Exception()

                    preference.summary = getString(R.string.preferences_workflow_start_id_summary, paddedId)

                    true

                } catch (e: Exception) {

                    showCodeExistsWarning(paddedId)

                    e.printStackTrace()

                    false
                }
            }

            if (clearDeviceIdPreference != null) {

                clearDeviceIdPreference.setOnPreferenceClickListener {

                    val printerId = prefs?.getString(getString(R.string.key_printer_device_id), null)
                    val scaleId = prefs?.getString(getString(R.string.key_scale_device_id), null)

                    if (printerId != null) clearDeviceConnection(printerId)
                    if (scaleId != null) clearDeviceConnection(scaleId)

                    prefs?.edit()
                        ?.putString(getString(R.string.key_printer_device_id), null)
                        ?.putString(getString(R.string.key_scale_device_id), null)
                        ?.apply()

                    updateDeviceAddressSummary(PRINTER_DEVICE_CHOICE)
                    updateDeviceAddressSummary(SCALE_DEVICE_CHOICE)

                    true
                }
            }

            val requirePersonPref = findPreference<Preference>(getString(R.string.key_preferences_person_require))
            if (requirePersonPref != null) {
                requirePersonPref.onPreferenceChangeListener =
                    Preference.OnPreferenceChangeListener { pref: Preference?, value: Any? ->
                        setupPersonUpdateUi(value as Boolean?)
                        true
                    }
            }

            findPreference<EditTextPreference>(getString(R.string.key_preferences_person))?.let { pref ->
                pref.setOnPreferenceChangeListener { _, newValue ->
                    updatePersonSummary(newValue as? String)
                    true
                }
            }

            setupPersonUpdateUi(null)
        }
    }

    private fun clearDeviceConnection(address: String) {

        (activity as MainActivity).disconnectGatt()

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.getSamples().observe(viewLifecycleOwner) { data ->

            if (data != null) {

                samples = data.map { SampleModel(it) }

            }
        }
    }

    private fun showCodeExistsWarning(code: String) {
        Toast.makeText(context, getString(R.string.pref_frag_code_exists_warning, code), Toast.LENGTH_LONG).show()
    }

    private fun setupPersonUpdateUi(explicitUpdate: Boolean?) {

        val updateFlag = explicitUpdate ?: prefs?.getBoolean(getString(R.string.key_preferences_person_require), false) ?: false

        val updateInterval = findPreference<Preference>(getString(R.string.key_preferences_person_require_interval))
        if (updateInterval != null) {
            updateInterval.isVisible = updateFlag
        }
    }

    @SuppressLint("MissingPermission")
    override fun onResume() {
        super.onResume()
        updateDeviceAddressSummary(PRINTER_DEVICE_CHOICE)
        updateDeviceAddressSummary(SCALE_DEVICE_CHOICE)
        updatePersonSummary()
        updateStartIdSummary()
        updateThresholdVisibility()
        setupPersonUpdateUi(null)
    }
}