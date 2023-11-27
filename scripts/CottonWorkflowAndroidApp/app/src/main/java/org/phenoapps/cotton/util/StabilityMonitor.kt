import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import dagger.hilt.android.qualifiers.ActivityContext
import org.phenoapps.cotton.R
import org.phenoapps.cotton.interfaces.StabilityApi
import javax.inject.Inject
import kotlin.math.abs

/**
 * This class is used to monitor scale readings.
 * Used for scales that do not have stability settings for <= 0
 * This implementation uses a circular array to track the last STABLE_N readings of the scale,
 * if all values are within a threshold the reading is considered stable.
 */
class StabilityMonitor @Inject constructor(@ActivityContext private val context: Context): StabilityApi {

    companion object {

        val TAG = StabilityMonitor::class.simpleName

        const val STABLE_N = 30

    }

    //the current index of the circular array to save the monitored value
    private var valueIndex: Int = 0

    //the circular array memory
    private val values: Array<Double> = Array(STABLE_N) { 0.0 }

    //the shared user preferences
    private val prefs by lazy {
        PreferenceManager.getDefaultSharedPreferences(context)
    }

    /**
     * checks default system settings for the user enabled value
     */
    override fun isEnabled() =
        prefs.getBoolean(context.getString(R.string.key_preferences_stability_enabled), false)

    /**
     * Does a round-robin check of values to see if they all are within defined threshold
     */
    override fun isStable(): Boolean {

        val thresh = getThreshold()

        values.forEachIndexed { i, x ->

            values.forEachIndexed { j, y ->

                //don't compare same value
                if (i != j) {

                    //calculate diff
                    val diff = abs(x - y)

                    //check threshold, break early if it fails
                    if (diff > thresh) return false
                }
            }
        }

        //didn't return early, so it is stable!
        return true
    }

    /**
     * gets the latest saved reading into memory, does not check for stability internally
     */
    override fun getStableRead(): String {

        return values[valueIndex].toString()

    }

    /**
     * converts the monitored value to a double and stores it in the circular array
     */
    override fun monitor(value: String) {

        Log.d(TAG, "monitor: $value")

        try {

            values[getNextIndex()] = value.toDouble()

        } catch (ignore: java.lang.NumberFormatException) {

            Log.e(TAG, ignore.message ?: "monitored non-double value")

        } catch (e: Exception) {

            Log.e(TAG, "Error in stability monitor checking.")

            e.printStackTrace()
        }
    }

    /**
     * gets the current circular index
     */
    private fun getNextIndex(): Int {

        val index = valueIndex

        valueIndex = (valueIndex + 1) % STABLE_N

        return index
    }

    /**
     * get the preference stability threshold double value
     */
    private fun getThreshold() = (prefs.getString(
        context.getString(R.string.key_preferences_stability_threshold), "1.0") ?: "1.0").toDouble()
}