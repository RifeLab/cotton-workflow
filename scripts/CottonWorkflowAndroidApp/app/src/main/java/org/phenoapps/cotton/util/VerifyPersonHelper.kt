package org.phenoapps.cotton.util

import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import androidx.appcompat.app.AlertDialog
import androidx.preference.PreferenceManager
import dagger.hilt.android.qualifiers.ActivityContext
import org.phenoapps.cotton.R
import javax.inject.Inject

class VerifyPersonHelper @Inject constructor(@ActivityContext private val context: Context) {

    val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    private var navigationCallback: (() -> Unit)? = null

    private fun Int.hourToNano() = this * 3600 * 1e9.toLong()

    fun setPrefNavigation(navigationFunction: () -> Unit) {
        navigationCallback = navigationFunction
    }

    /**
     * Simple function that checks if the collect activity was opened >24hrs ago.
     * If the condition is met, it asks the user to reenter the collector id.
     */
    fun checkLastOpened() {

        val lastOpen: Long = prefs.getLong(context.getString(R.string.key_last_time_opened), 0L)
        val systemTime = System.nanoTime()

        //number of hours to wait before asking for user, pref found in profile
        val interval = (prefs.getString(context.getString(R.string.key_preferences_person_require_interval), "-1") ?: "-1").toInt()

        val nanosToWait = 1e9.toLong() * 3600 * interval
        if (lastOpen != 0L && systemTime - lastOpen > nanosToWait) {
            val verify: Boolean = prefs.getBoolean(context.getString(R.string.key_preferences_person_require), true)
            if (verify) {
                val name: String = prefs.getString(context.getString(R.string.key_preferences_person), "") ?: ""
                if (name.isNotEmpty()) {
                    //person presumably has been set
                    showAskCollectorDialog(
                        context.getString(R.string.verify_collector_title, name),
                        context.getString(R.string.verify_collector_yes_button),
                        context.getString(R.string.verify_collector_neutral_button),
                        context.getString(R.string.verify_collector_no_button)
                    )
                } else {
                    //person presumably hasn't been set
                    showAskCollectorDialog(
                        context.getString(R.string.verify_collector_new_collector),
                        context.getString(R.string.verify_collector_no_button),
                        context.getString(R.string.verify_collector_neutral_button),
                        context.getString(R.string.verify_collector_yes_button)
                    )
                }
            }
        }
        updateLastOpenedTime()
    }

    private fun showAskCollectorDialog(
        message: String,
        positive: String,
        neutral: String,
        negative: String
    ) {
        AlertDialog.Builder(context)
            .setTitle(message) //yes button
            .setPositiveButton(
                positive
            ) { dialog: DialogInterface, _: Int -> dialog.dismiss() } //yes, don't ask again button
            .setNeutralButton(neutral) { dialog: DialogInterface, _: Int ->
                dialog.dismiss()
                prefs.edit().putBoolean(context.getString(R.string.key_preferences_person_require), false).apply()
            } //no (navigates to the person preference)
            .setNegativeButton(negative) { dialog: DialogInterface, _: Int ->
                dialog.dismiss()
                navigationCallback?.invoke()
            }
            .show()
    }

    fun updateLastOpenedTime() {
        prefs.edit().putLong(context.getString(R.string.key_last_time_opened), System.nanoTime()).apply()
    }
}