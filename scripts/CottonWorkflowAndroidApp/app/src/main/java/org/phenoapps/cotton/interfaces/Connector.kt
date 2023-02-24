package org.phenoapps.cotton.interfaces

interface Connector {

    //check if a gatt address is connected
    fun isConnected(address: String?): Boolean

    //triggers attempts to reconnect to preference devices
    fun reconnect()

    //get the preference scale id
    fun getScaleId(): String?

    //get the preference printer id
    fun getPrinterId(): String?

    //get the current person from preferences
    fun getPerson(): String?
}