package org.phenoapps.cotton.viewmodels

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.liveData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import org.phenoapps.cotton.util.ScaleUtil
import org.phenoapps.interfaces.security.SecureBluetooth
import org.phenoapps.viewmodels.bluetooth.gatt.GattViewModel
import org.phenoapps.viewmodels.scales.OhausViewModel
import org.phenoapps.viewmodels.scales.SerialPortViewModel.Companion.SERIAL_IO_SCALE_CHAR
import org.phenoapps.viewmodels.scales.SerialPortViewModel.Companion.SERIAL_IO_WEIGHT_SERVICE
import javax.inject.Inject

@SuppressLint("MissingPermission")
@HiltViewModel
class OhausSampleViewModel @Inject constructor(): GattViewModel() {

    private var gattStack = ArrayList<BluetoothGatt>()

    var job = Job()
        get() {
            if (field.isCancelled) field = Job()
            return field
        }

    var advisor: SecureBluetooth? = null

    private val scaleReading: MutableLiveData<String> by lazy {
        MutableLiveData<String>()
    }

    var connected: Boolean? = null

    private var internalReadingCache = arrayListOf<String>()

    /**
     * Helper function that does it all, checks connection before outputting scale readings
     */
    fun readWeight() = liveData<String>(job) {

        emitSource(scaleReading)
    }

    fun clearScaleLastRead() {
        scaleReading.value = ""
    }

    fun reach(context: Context, adapter: BluetoothAdapter, address: String?) = liveData(job) {

        connected = false

        connect(context, adapter, address)

        while (!super.isGattConnected()) {
            delay(1000)
        }

        connected = true

        do {

            if (connected == null) {
                emit(super.isGattConnected())
            }
            else {
                emit(connected)
            }

            delay(1000)

        } while (connected == true || super.isGattConnected())

        emit(false)
    }

    fun connect(context: Context, adapter: BluetoothAdapter, address: String? = null) {

        if (!address.isNullOrBlank()) {

            try {

                register(adapter, context, address)

            } catch (e: java.lang.IllegalArgumentException) {

                e.printStackTrace()
            }
        }
    }

    fun disconnect() {
        connected = false
        super.unregister()

        //old devices <= Android 10 that track multiple gatt objects
        //without disconnecting all, the connected device might think its still connected
        try {
            gattStack.forEach { g ->
                g.close()
                g.disconnect()
                g.discoverServices()
            }
            gattStack.clear()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
        super.onServicesDiscovered(gatt, status)

        if (gatt?.services?.map { it.uuid }?.contains(SERIAL_IO_WEIGHT_SERVICE) == true) {
            connected = true
        }

        notify(SERIAL_IO_WEIGHT_SERVICE.toString(), SERIAL_IO_SCALE_CHAR.toString())
        notify(OhausViewModel.OHAUS_SERVICE_UUID.toString(), OhausViewModel.OHAUS_COMMAND_CHAR_UUID.toString())
        write(
            OhausViewModel.OHAUS_SERVICE_UUID.toString(), OhausViewModel.OHAUS_COMMAND_CHAR_UUID.toString(),
            OhausViewModel.SET2GRAM
        )
//        write(
//            OhausViewModel.OHAUS_SERVICE_UUID.toString(), OhausViewModel.OHAUS_COMMAND_CHAR_UUID.toString(),
//            "SP".toByteArray() //send print on stability settings pass
//        )
    }

    @Synchronized
    override fun onCharacteristicChanged(
        gatt: BluetoothGatt?,
        characteristic: BluetoothGattCharacteristic?,
    ) {
        super.onCharacteristicChanged(gatt, characteristic)

        //serial port setup
        if (characteristic?.uuid.toString() == SERIAL_IO_SCALE_CHAR.toString()) {

            //https://dmx.ohaus.com/WorkArea/showcontent.aspx?id=30234

            val bytes = characteristic!!.value

            //input on Defender 5000 looks like this: A0 A0 A0 A0 30 2E 30 36 36 A0 EB E7 8D 0A 8D 0A
            //A0 are null data 'spaces' (can also be 20), first token is UTF8 string encoded hex value: 30 2E 30 36 36 which when decoded from hex is 0e066 so 0.066
            //second token is probably an ohaus id for unit E7 is gram and EBE7 is kg
            //if starts with 2D its negative so actual output will skip "2" and just have "D" like "D11" for "-11"
            //final string has an "E" for the decimal places
            //if content -> num is selected parse E7 for g and EBE7 for kg (so depending on for loop below this will be 7 or b7 ending)

            //translate bytes to hex
            val hex = bytes.map { String.format("%02X", it.toInt() and 0xFF) }.map { if (it == "20") "A0" else it }

            //join to string and split by A0 null data, grab the first token of data (data is between A0's)
            //replace "20" with "A0" they are both "spaces"
            //ranger 3000 uses ascii 32 == "20" in hex for separator
            //other ranger seems to use "A0" which is space " "
            val readable = hex.joinToString("").split("A0").filter { it.isNotEmpty() }

            var measure = readable[0]

            // E7 -> (g), EB E7 -> (kg)
            //val units = readable[1].split("8D")[0]

            var isNegative = false
            if (measure.startsWith("2D")) {
                isNegative = true
                measure = measure.substring(2)
            }

            val builder = StringBuilder()
            for (i in 1 until measure.length step 2) {
                builder.append(measure[i])
            }

            //final string has an "E" for the decimal places
            measure = builder.toString()
            measure = measure.replace("E", ".")

            if (isNegative) {
                measure = "-$measure"
            }

            scaleReading.postValue(measure)

        } else { //ohaus bt interface setup

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
    }

    override fun onConnectionStateChange(
        gatt: BluetoothGatt?,
        status: Int,
        newState: Int
    ) {
        super.onConnectionStateChange(gatt, status, newState)

        //track gatt objects for device compatibility <= Android 10
        gatt?.let { g ->
            gattStack.add(g)
        }

        when (newState) {
            BluetoothGatt.STATE_DISCONNECTED -> {
                connected = false
                gatt?.close()
                gatt?.disconnect()
            }
        }
    }
}