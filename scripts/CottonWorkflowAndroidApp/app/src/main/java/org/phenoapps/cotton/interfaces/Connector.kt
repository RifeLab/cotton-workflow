package org.phenoapps.cotton.interfaces

interface Connector {

    //check if a gatt address is connected
    fun isConnected(address: String?): Boolean

    //get the preference scale id
    fun getScaleId(): String?

    //get the preference printer id
    fun getPrinterId(): String?

    //get the current person from preferences
    fun getPerson(): String?
}