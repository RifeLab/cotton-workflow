package org.phenoapps.cotton.util

import android.os.Looper
import com.zebra.sdk.comm.BluetoothConnection
import com.zebra.sdk.comm.ConnectionException
import com.zebra.sdk.printer.ZebraPrinterFactory
import com.zebra.sdk.printer.ZebraPrinterLanguageUnknownException

class PrintThread(private val address: String) : Thread() {

    private var barcodes: Array<String> = arrayOf()

    fun print(barcodes: Array<String>) {
        this.barcodes = barcodes
        start()
    }

    override fun run() {

        Looper.prepare()

        val bc = BluetoothConnection(address)

        try {

            bc.open()

            val printer = ZebraPrinterFactory.getInstance(bc)

            val linkOsPrinter = ZebraPrinterFactory.createLinkOsPrinter(printer)

            linkOsPrinter?.let { it ->

                barcodes.forEach { barcode ->
                    printer.sendCommand("^XA" +
                            "^PON^LH0,0^FWN" + //FWN resets field orientation, LH resets offsets, PO resets print orientation
                            "^FO90,75" +        //TODO this works only for 2in(w) by 1in(h) labels on 8dpmm (203dpi) printers
                            "^BY2,2,50" +      //sets bar width ratios, allows these longer codes to fit on small label
                            "^B3N,N,,Y,N" +
                            "^FD$barcode^FS" +
                            "^XZ")
                }
            }

        } catch (e: ConnectionException) {

            e.printStackTrace()

        } catch (e: ZebraPrinterLanguageUnknownException) {

            e.printStackTrace()

        } finally {

            bc.close()

        }
    }
}