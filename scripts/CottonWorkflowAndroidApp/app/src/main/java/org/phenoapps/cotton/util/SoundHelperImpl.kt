package org.phenoapps.cotton.util

import android.content.Context
import android.media.MediaPlayer
import androidx.preference.PreferenceManager
import dagger.hilt.android.qualifiers.ActivityContext
import org.phenoapps.cotton.R
import javax.inject.Inject

/**
 * Plays a sound from the raw resources folder.
 */
class SoundHelperImpl @Inject constructor(@ActivityContext private val context: Context) {

    companion object {
        const val PLONK = "plonk"
        const val ADVANCE = "advance"
        const val CYCLE = "cycle"
        const val ERROR = "error"
        const val CELEBRATE = "hero_simple_celebration"
        const val ALERT_ERROR = "alert_error"
        const val DELETE = "delete"
    }

    private val prefs by lazy {
        PreferenceManager.getDefaultSharedPreferences(context)
    }

    fun playPlonk() {
        playSound(PLONK)
    }

    fun playAdvance() {
        playSound(ADVANCE)
    }

    fun playCycle() {
        playSound(CYCLE)
    }

    fun playError() {
        playSound(ERROR)
    }

    fun playCelebrate() {
        playSound(CELEBRATE)
    }

    fun playAlertError() {
        playSound(ALERT_ERROR)
    }

    fun playDelete() {
        playSound(DELETE)
    }

    fun playSound(sound: String?) {

        if (prefs.getBoolean(context.getString(R.string.key_preferences_sound_enabled), true)) {
            try {
                val resID: Int = context.resources.getIdentifier(sound, "raw", context.packageName)
                val chimePlayer = MediaPlayer.create(context, resID)
                chimePlayer.start()
                chimePlayer.setOnCompletionListener { mp -> mp.release() }
            } catch (ignore: Exception) { }
        }
    }
}