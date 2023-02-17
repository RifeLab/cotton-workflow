package org.phenoapps.cotton.interfaces

import org.phenoapps.cotton.models.SampleModel

/**
 * Fragments that scan barcodes implement this.
 * Other components can use this interface to get the scanned code,
 * force new/existing workflow
 */
interface ScanInteractor {

    // returns the scanned barcode, can be null if not scanned yet
    fun getScannedCode(): String?

    // forces the new code workflow
    fun onNewBarcode(code: String)

    // forces the code exists workflow
    fun onBarcodeExists(sample: SampleModel)

    // optional, user accepts the new code
    fun onAcceptNewBarcode(code: String)
}