package org.phenoapps.cotton.viewmodels

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.liveData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import org.phenoapps.cotton.util.ScaleUtil
import org.phenoapps.interfaces.security.SecureBluetooth
import org.phenoapps.viewmodels.bluetooth.gatt.GattViewModel
import org.phenoapps.viewmodels.scales.OhausViewModel
import javax.inject.Inject

@SuppressLint("MissingPermission")
@HiltViewModel
class OhausSampleViewModel @Inject constructor(): GattViewModel() {

    var job = Job()
        get() {
            if (field.isCancelled) field = Job()
            return field
        }

    var gatts: ArrayList<BluetoothGatt?>? = null

    var advisor: SecureBluetooth? = null

    val reachabilityStatus: MutableLiveData<Boolean> by lazy {
        MutableLiveData<Boolean>()
    }

    val connectionStatus: MutableLiveData<Int> by lazy {
        MutableLiveData<Int>()
    }

    val scaleReading: MutableLiveData<String> by lazy {
        MutableLiveData<String>()
    }

    private var internalReadingCache = arrayListOf<String>()

    /**
     * Helper function that does it all, checks connection before outputting scale readings
     */
    fun readWeight(context: Context, adapter: BluetoothAdapter, address: String? = "C4:BE:84:1A:25:93") = liveData<String>(job) {

        connect(context, adapter, address)

        while (!super.isGattConnected()) {
            delay(1000)
        }

        emitSource(scaleReading)
    }

    fun reach(context: Context, adapter: BluetoothAdapter, address: String?) = liveData(job) {

        connect(context, adapter, address)

        while (!super.isGattConnected()) {
            delay(1000)
        }

        do {

            emit(super.isGattConnected())

            delay(1000)

        } while (super.isGattConnected())

    }

    fun connect(context: Context, adapter: BluetoothAdapter, address: String? = "C4:BE:84:1A:25:93") {

        register(adapter, context, address ?: "C4:BE:84:1A:25:93")

    }

    fun disconnect() {

        super.unregister()
//        gatts?.forEach { gatt ->
//            gatt?.disconnect()
//            gatt?.close()
//            //super.unregister()
//        }
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
        super.onServicesDiscovered(gatt, status)

        notify(OhausViewModel.OHAUS_SERVICE_UUID.toString(), OhausViewModel.OHAUS_COMMAND_CHAR_UUID.toString())
        write(
            OhausViewModel.OHAUS_SERVICE_UUID.toString(), OhausViewModel.OHAUS_COMMAND_CHAR_UUID.toString(),
            OhausViewModel.SET2GRAM
        )
        write(
            OhausViewModel.OHAUS_SERVICE_UUID.toString(), OhausViewModel.OHAUS_COMMAND_CHAR_UUID.toString(),
            "SP".toByteArray() //OhausViewModel.CONTINUOUS //TODO
        )
    }

    @Synchronized
    override fun onCharacteristicChanged(
        gatt: BluetoothGatt?,
        characteristic: BluetoothGattCharacteristic?,
    ) {
        super.onCharacteristicChanged(gatt, characteristic)

            val reading = characteristic?.getStringValue(0) ?: ""

            if (ScaleUtil.UNIT in reading) {

                internalReadingCache.add(reading)
                scaleReading.postValue(
                    internalReadingCache
                        .joinToString("")
                        .trim()
                        .replace(" ", "")
                        .split(ScaleUtil.UNIT)
                        .first()
                        .replace(ScaleUtil.UNIT, "")
                )
                internalReadingCache.clear()

            } else {

                internalReadingCache.add(reading)

            }

    }

//    override fun onConnectionStateChange(
//        gatt: BluetoothGatt?,
//        status: Int,
//        newState: Int
//    ) {
//        super.onConnectionStateChange(gatt, status, newState)
//
//        when (newState) {
//            BluetoothGatt.STATE_CONNECTED -> {
//                this.gatts?.add(gatt)
//                connectionStatus.postValue(newState)
//                gatt?.discoverServices()
//            }
//            BluetoothGatt.STATE_DISCONNECTED -> {
//                connectionStatus.postValue(newState)
//            }
//            else -> {
//                connectionStatus.postValue(status)
//            }
//        }
//    }
}