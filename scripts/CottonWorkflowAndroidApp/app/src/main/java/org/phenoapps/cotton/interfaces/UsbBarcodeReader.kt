package org.phenoapps.cotton.interfaces

interface UsbBarcodeReader {
    fun askUsbBarcodeScanner()
    fun resolveBarcode(code: String)
}