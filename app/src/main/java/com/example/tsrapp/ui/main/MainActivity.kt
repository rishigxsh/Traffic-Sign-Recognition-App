package com.example.tsrapp.ui.main

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.graphics.createBitmap
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import com.example.tsrapp.R
import com.example.tsrapp.databinding.ActivityMainBinding
import com.example.tsrapp.util.SettingsManager
import com.example.tsrapp.util.TextToSpeechHelper
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var ttsHelper: TextToSpeechHelper
    private var lastSpokenSign: String? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var isDetecting: Boolean = false
    private var isInitializingCamera: Boolean = false
    private var pendingStartAfterPermission: Boolean = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            if (pendingStartAfterPermission) startCamera()
        } else {
            Toast.makeText(this, R.string.camera_permission_denied, Toast.LENGTH_LONG).show()
        }
        pendingStartAfterPermission = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()
        ttsHelper = TextToSpeechHelper(this)
        ttsHelper.initialize { /* continue even if TTS fails */ }

        // Status bar initial values
        val threshold = SettingsManager.getConfidenceThreshold(this)
        binding.statusThreshold.text = getString(R.string.status_threshold_value, (threshold * 100).toInt())
        val ttsEnabled = SettingsManager.isTtsEnabled(this)
        binding.statusVoice.text = getString(if (ttsEnabled) R.string.status_voice_on else R.string.status_voice_off)

        // HUD region label
        val region = SettingsManager.getRegion(this)
        binding.hudRegion.text = getString(R.string.hud_region_info, region)

        // Back button
        binding.backButton.setOnClickListener { finish() }

        // Start / Stop button
        binding.toggleDetectionButton.setOnClickListener {
            if (isDetecting) stopCamera() else startDetectionFlow()
        }

        // Observe detections
        viewModel.detectedSigns.observe(this, Observer { signs ->
            binding.detectionOverlay.updateDetections(signs)
            signs.firstOrNull()?.let { sign ->
                if (sign.label != lastSpokenSign) {
                    ttsHelper.speak(sign.label)
                    lastSpokenSign = sign.label
                }
            }
        })

        showIdleState()
    }

    // ── UI state helpers ──────────────────────────────────

    private fun showIdleState() {
        binding.idleHint.visibility = View.VISIBLE
        binding.scanningLabel.visibility = View.GONE
        binding.detectionResultRow.visibility = View.GONE
        binding.confidenceTrack.visibility = View.GONE
        binding.confidenceBarWrapper.visibility = View.GONE
        binding.criticalAlertBanner.visibility = View.GONE
        binding.hudStatus.text = getString(R.string.live_standby)
        binding.scanGridOverlay.visibility = View.GONE
    }

    private fun showScanningState() {
        binding.idleHint.visibility = View.GONE
        binding.scanningLabel.visibility = View.VISIBLE
        binding.detectionResultRow.visibility = View.GONE
        binding.confidenceTrack.visibility = View.GONE
        binding.confidenceBarWrapper.visibility = View.GONE
        binding.criticalAlertBanner.visibility = View.GONE
        binding.hudStatus.text = getString(R.string.live_rec)
        binding.scanGridOverlay.visibility = View.VISIBLE
    }

    // ── Camera control ────────────────────────────────────

    private fun startDetectionFlow() {
        if (isInitializingCamera) return
        val hasPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            pendingStartAfterPermission = true
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            return
        }
        startCamera()
    }

    private fun startCamera() {
        if (isDetecting || isInitializingCamera) return
        isInitializingCamera = true
        binding.toggleDetectionButton.isEnabled = false
        binding.toggleDetectionButton.text = getString(R.string.status_detection_starting)
        showScanningState()

        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.cameraPreview.surfaceProvider)
            }
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build().also {
                    it.setAnalyzer(cameraExecutor, FrameAnalyzer { bitmap ->
                        viewModel.processFrame(bitmap)
                    })
                }
            try {
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis)
                cameraProvider = provider
                isDetecting = true
                isInitializingCamera = false
                binding.toggleDetectionButton.isEnabled = true
                binding.toggleDetectionButton.text = getString(R.string.status_detection_stop)
            } catch (e: Exception) {
                isDetecting = false
                isInitializingCamera = false
                binding.toggleDetectionButton.isEnabled = true
                binding.toggleDetectionButton.text = getString(R.string.status_detection_start)
                showIdleState()
                Toast.makeText(this, "Camera failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopCamera() {
        cameraProvider?.unbindAll()
        isDetecting = false
        binding.toggleDetectionButton.text = getString(R.string.status_detection_start)
        showIdleState()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCamera()
        cameraExecutor.shutdown()
        ttsHelper.shutdown()
    }

    // ── Frame analyzer ────────────────────────────────────

    private class FrameAnalyzer(
        private val onFrameAvailable: (Bitmap) -> Unit
    ) : ImageAnalysis.Analyzer {
        override fun analyze(imageProxy: ImageProxy) {
            val bitmap = imageProxyToBitmap(imageProxy)
            onFrameAvailable(bitmap)
            imageProxy.close()
        }

        private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap {
            val yBuffer = imageProxy.planes[0].buffer
            val uBuffer = imageProxy.planes[1].buffer
            val vBuffer = imageProxy.planes[2].buffer
            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()
            val nv21 = ByteArray(ySize + uSize + vSize)
            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)
            val yuvImage = YuvImage(nv21, ImageFormat.NV21, imageProxy.width, imageProxy.height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, imageProxy.width, imageProxy.height), 80, out)
            val bytes = out.toByteArray()
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: createBitmap(imageProxy.width, imageProxy.height, Bitmap.Config.ARGB_8888)
        }
    }
}
