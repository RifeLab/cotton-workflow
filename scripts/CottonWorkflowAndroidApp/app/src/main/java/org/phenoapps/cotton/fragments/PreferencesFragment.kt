package org.phenoapps.cotton.fragments

import android.annotation.SuppressLint
import android.content.*
import android.os.Bundle
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import org.phenoapps.cotton.R

class PreferencesFragment: PreferenceFragmentCompat() {

    companion object {

        const val PRINTER_DEVICE_CHOICE = 0
        const val SCALE_DEVICE_CHOICE = 1

    }

    private var prefs: SharedPreferences? = null

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

    @SuppressLint("MissingPermission")
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {

        context?.let { ctx ->

            prefs = PreferenceManager.getDefaultSharedPreferences(ctx)

            addPreferencesFromResource(R.xml.preferences)

            val clearDeviceIdPreference =
                findPreference<Preference>(getString(R.string.key_preferences_device_id_clear))

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

            if (clearDeviceIdPreference != null) {

                clearDeviceIdPreference.setOnPreferenceClickListener {

                    prefs?.edit()
                        ?.putString(getString(R.string.key_printer_device_id), null)
                        ?.putString(getString(R.string.key_scale_device_id), null)
                        ?.apply()

                    updateDeviceAddressSummary(PRINTER_DEVICE_CHOICE)
                    updateDeviceAddressSummary(SCALE_DEVICE_CHOICE)

                    true
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun onResume() {
        super.onResume()
        updateDeviceAddressSummary(PRINTER_DEVICE_CHOICE)
        updateDeviceAddressSummary(SCALE_DEVICE_CHOICE)
    }
}