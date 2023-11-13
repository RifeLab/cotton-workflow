package org.phenoapps.cotton.interfaces

/**
 * The interface to implement to for software based stability
 */
interface StabilityApi {

    /**
     * checks system preferences to see if the user enabled stability printing
     */
    fun isEnabled(): Boolean

    /**
     * checks whether the monitored values are stable using the preference threshold
     */
    fun isStable(): Boolean

    /**
     * returns the monitored stable value
     */
    fun getStableRead(): String

    /**
     * sends the raw printer reading to the stability api
     */
    fun monitor(value: String)
}