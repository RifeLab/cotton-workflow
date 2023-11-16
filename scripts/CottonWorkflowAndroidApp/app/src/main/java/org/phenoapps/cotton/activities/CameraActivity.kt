package org.phenoapps.cotton.activities

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.Display
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import org.phenoapps.cotton.R
import java.util.concurrent.Executors

class CameraActivity : AppCompatActivity() {

    private lateinit var cameraSelector: CameraSelector
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var previewView: PreviewView
    private lateinit var titleTextView: TextView

    //barcode cache used to ensure scans are accurate, and mlkit is detecting a single barcode
    private var barcodeCache = Array(CACHE_SIZE) { String() }
    private var cacheIndex = 0

    companion object {

        val TAG = CameraActivity::class.simpleName
        const val EXTRA_BARCODE = "barcode"
        const val EXTRA_TITLE = "title"
        const val CACHE_SIZE = 3

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_camera)

        cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        previewView = findViewById(R.id.act_camera_pv)
        titleTextView = findViewById(R.id.act_camera_title_tv)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            bindLifecycle(cameraProvider)
        }, ContextCompat.getMainExecutor(this))

        cameraSelector = CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()

        setupCameraTitleView()
    }

    private fun setupCameraTitleView() {

        intent?.getStringExtra(EXTRA_TITLE)?.let { title ->

            titleTextView.text = title

        }
    }

    private fun bindLifecycle(cameraProvider: ProcessCameraProvider) {

        val preview = Preview.Builder()
            .setTargetResolution(Size(1080, 1920))
            .setTargetRotation(previewView.display?.rotation ?: 0)
            .build()

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        preview.setSurfaceProvider(previewView.surfaceProvider)

        val barcodeScanner: BarcodeScanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                .build()
        )

        val analysis = ImageAnalysis.Builder()
            .setTargetRotation(previewView.display?.rotation ?: 0)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        val cameraExecutor = Executors.newSingleThreadExecutor()

        analysis.setAnalyzer(cameraExecutor) { imageProxy ->

            processImageProxy(barcodeScanner, imageProxy)

        }

        val camera = cameraProvider.bindToLifecycle(this as LifecycleOwner, cameraSelector, preview, analysis)

        Log.d(TAG, "Camera lifecycle bound: ${camera.cameraInfo}")
    }

    private fun checkCache(code: String): Boolean {

        barcodeCache[cacheIndex] = code

        var passing = true
        for (i in cacheIndex downTo 0) {
            passing = barcodeCache[i] == code
        }

        if (!passing) {

            cacheIndex = 0

            barcodeCache = Array(CACHE_SIZE) { String() }

        } else {

            if (cacheIndex == CACHE_SIZE - 1) {

                return true

            } else cacheIndex++
        }

        return false
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun processImageProxy(
        scanner: BarcodeScanner,
        imageProxy: ImageProxy
    ){

        imageProxy.image?.let { img ->

            val inputImage = InputImage.fromMediaImage(img, imageProxy.imageInfo.rotationDegrees)

            scanner.process(inputImage).addOnSuccessListener { barcodes ->

                //barcodes.forEach { println(it.displayValue) }

                if (barcodes.isNotEmpty()) {

                    barcodes.first().displayValue?.let { code ->

                        if (checkCache(code)) {

                            setResult(Activity.RESULT_OK, Intent().also {

                                it.putExtra(EXTRA_BARCODE, barcodes.first().displayValue)

                            })

                            try {
                                imageProxy.close()
                            } catch (ignore: Exception) {}

                            finish()
                        }
                    }
                }

            }.addOnFailureListener {

                it.printStackTrace()

            }.addOnCompleteListener {

                try {
                    imageProxy.close()
                } catch (ignore: Exception) {}
            }
        }
    }
}