package com.example.tsrapp.ui.main

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import com.example.tsrapp.R
import com.example.tsrapp.databinding.ActivityMainBinding
import com.example.tsrapp.util.DriverAlertFeedback
import com.example.tsrapp.util.SettingsManager
import com.example.tsrapp.util.SignLabelToSpeech
import com.example.tsrapp.util.TextToSpeechHelper
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat
import androidx.core.view.updateLayoutParams
import android.view.ViewGroup.MarginLayoutParams

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var ttsHelper: TextToSpeechHelper
    private var cameraProvider: ProcessCameraProvider? = null
    private var isDetecting = false
    private var isInitializingCamera = false
    private var pendingStartAfterPermission = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
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

        // Handle edge-to-edge and system bar insets
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            
            // Adjust top banner margin to avoid status bar
            binding.topBannerCard.updateLayoutParams<MarginLayoutParams> {
                topMargin = systemBars.top + (12 * resources.displayMetrics.density).toInt()
            }
            
            // Adjust bottom panel margin to avoid navigation bar
            binding.bottomPanelCard.updateLayoutParams<MarginLayoutParams> {
                bottomMargin = systemBars.bottom + (16 * resources.displayMetrics.density).toInt()
            }
            
            insets
        }

        binding.cameraPreview.implementationMode = PreviewView.ImplementationMode.COMPATIBLE

        cameraExecutor = Executors.newSingleThreadExecutor()
        ttsHelper = TextToSpeechHelper(this)
        ttsHelper.initialize { success ->
            val isMuted = SettingsManager.getVoiceMode(this) == SettingsManager.VOICE_MUTED
            if (!success && !isMuted) {
                Toast.makeText(this, "Speech alerts unavailable.", Toast.LENGTH_SHORT).show()
            }
        }

        setupFloatingControls()
        setupBottomPanel()
        observeDetections()

        binding.backButton.setOnClickListener { finish() }
        showIdleState()
    }

    private fun setupFloatingControls() {
        updateMuteButtonIcon()
        binding.muteButton.setOnClickListener {
            val sheet = VoiceSettingsBottomSheet()
            sheet.show(supportFragmentManager, VoiceSettingsBottomSheet.TAG)
        }
/*
        binding.stopButton.setOnClickListener {
            if (isDetecting) stopCamera()
        }
*/
    }

    private fun updateMuteButtonIcon() {
        val mode = SettingsManager.getVoiceMode(this)
        val iconRes = when (mode) {
            SettingsManager.VOICE_MUTED -> R.drawable.ic_mute
            SettingsManager.VOICE_ALERTS -> R.drawable.ic_alerts_only
            else -> R.drawable.ic_unmute
        }
        binding.muteButton.setImageResource(iconRes)
    }

    private fun setupBottomPanel() {
        val initialThreshold = SettingsManager.getConfidenceThreshold(this)
        binding.thresholdSlider.progress = (initialThreshold * 100).toInt()
        binding.statusThreshold.text = "${(initialThreshold * 100).toInt()}%"

        binding.thresholdSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    SettingsManager.setConfidenceThreshold(this@MainActivity, progress / 100f)
                    binding.statusThreshold.text = "$progress%"
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.toggleDetectionButton.setOnClickListener {
            if (isDetecting) stopCamera() else startDetectionFlow()
        }
    }

    private fun observeDetections() {
        viewModel.detectedSigns.observe(this) { signs ->
            binding.detectionOverlay.updateDetections(signs)

            val topSign = signs.firstOrNull()
            if (topSign != null) {
                binding.signBadgeSymbol.text = SignLabelToSpeech.getSymbol(topSign.label)
                binding.detectedSignName.text = SignLabelToSpeech.toDisplayName(topSign.label)
                binding.detectedSignType.text = if (topSign.isCritical) "⚠ Critical sign" else "Detected"
                binding.detectedConfidence.text = "${(topSign.confidence * 100).toInt()}%"

                if (topSign.isCritical) DriverAlertFeedback.vibrateShort(this)

                signs.sortedByDescending { it.confidence }.forEach { sign ->
                    ttsHelper.speakDrivingSign(sign.label)
                }
            } else if (isDetecting) {
                // Keep banner showing but update subtitle to "No signs detected"
                binding.detectedSignType.text = "No signs detected"
            }
        }

        viewModel.inferenceTimeMs.observe(this) { ms ->
            binding.statusInference.text = if (ms != null && ms > 0) "${ms}ms" else "--"
        }

        viewModel.fps.observe(this) { fps ->
            binding.statusFps.text = if (fps != null && fps > 0) "%.1f".format(fps) else "--"
        }

        viewModel.modelError.observe(this) { msg ->
            if (msg != null) Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        }
    }

    private fun showIdleState() {
        binding.signBadgeSymbol.text = "🔍"
        binding.detectedSignName.text = "Standby"
        binding.detectedSignType.text = "Start detection to see signs"
        binding.detectedConfidence.text = "--"
        binding.scanGridOverlay.visibility = View.GONE
        binding.toggleDetectionButton.text = "START"
        binding.toggleDetectionButton.alpha = 1.0f
    }

    private fun showScanningState() {
        binding.signBadgeSymbol.text = "📷"
        binding.detectedSignName.text = "Detecting…"
        binding.detectedSignType.text = "Looking for signs"
        binding.detectedConfidence.text = ""
        binding.scanGridOverlay.visibility = View.VISIBLE
        binding.toggleDetectionButton.text = "STOP"
        binding.toggleDetectionButton.alpha = 0.85f
    }

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
        showScanningState()

        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val provider = future.get()
                val selector = when {
                    provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ->
                        CameraSelector.DEFAULT_BACK_CAMERA
                    provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) ->
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    else -> throw IllegalStateException("No available camera on this device")
                }

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(binding.cameraPreview.surfaceProvider)
                }
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build().also {
                        it.setAnalyzer(cameraExecutor, FrameAnalyzer { bitmap ->
                            binding.detectionOverlay.setSourceSize(bitmap.width, bitmap.height)
                            viewModel.processFrame(bitmap)
                        })
                    }

                provider.unbindAll()
                provider.bindToLifecycle(this, selector, preview, imageAnalysis)
                cameraProvider = provider
                isDetecting = true
                isInitializingCamera = false
                binding.toggleDetectionButton.isEnabled = true
                binding.toggleDetectionButton.text = "STOP"
            } catch (e: Exception) {
                isDetecting = false
                isInitializingCamera = false
                binding.toggleDetectionButton.isEnabled = true
                showIdleState()
                Toast.makeText(this, "Camera failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopCamera() {
        cameraProvider?.unbindAll()
        isDetecting = false
        viewModel.clearSmoothing()
        showIdleState()
    }

    override fun onResume() {
        super.onResume()
        updateMuteButtonIcon()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCamera()
        cameraExecutor.shutdown()
        ttsHelper.shutdown()
    }

    // ── Frame conversion (unchanged) ──────────────────────────────────────────

    private class FrameAnalyzer(private val onFrameAvailable: (Bitmap) -> Unit) :
        ImageAnalysis.Analyzer {

        override fun analyze(imageProxy: ImageProxy) {
            val bitmap = imageProxyToBitmap(imageProxy)
            onFrameAvailable(bitmap)
            imageProxy.close()
        }

        private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap {
            val nv21 = yuv420888ToNv21(imageProxy)
            val yuvImage = YuvImage(nv21, ImageFormat.NV21, imageProxy.width, imageProxy.height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, imageProxy.width, imageProxy.height), 90, out)
            val bytes = out.toByteArray()
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: createBitmap(imageProxy.width, imageProxy.height, Bitmap.Config.ARGB_8888)
            return rotateBitmapIfNeeded(decoded, imageProxy.imageInfo.rotationDegrees)
        }

        private fun yuv420888ToNv21(imageProxy: ImageProxy): ByteArray {
            val width = imageProxy.width
            val height = imageProxy.height
            val nv21 = ByteArray(width * height + width * height / 2)
            imageProxy.planes[0].copyPlaneTo(nv21, 0, width, height)
            imageProxy.planes[2].copyChromaTo(nv21, width * height, width, height, imageProxy.planes[1])
            return nv21
        }

        private fun ImageProxy.PlaneProxy.copyPlaneTo(
            out: ByteArray, offset: Int, width: Int, height: Int
        ) {
            val buffer = buffer.duplicate().also { it.rewind() }
            var outIdx = offset
            for (row in 0 until height) {
                buffer.position(row * rowStride)
                if (pixelStride == 1) {
                    buffer.get(out, outIdx, width)
                    outIdx += width
                } else {
                    for (col in 0 until width) {
                        out[outIdx++] = buffer.get(row * rowStride + col * pixelStride)
                    }
                }
            }
        }

        private fun ImageProxy.PlaneProxy.copyChromaTo(
            out: ByteArray, offset: Int, width: Int, height: Int,
            uPlane: ImageProxy.PlaneProxy
        ) {
            val vBuf = buffer.duplicate().also { it.rewind() }
            val uBuf = uPlane.buffer.duplicate().also { it.rewind() }
            var outIdx = offset
            for (row in 0 until height / 2) {
                for (col in 0 until width / 2) {
                    out[outIdx++] = vBuf.get(row * rowStride + col * pixelStride)
                    out[outIdx++] = uBuf.get(row * uPlane.rowStride + col * uPlane.pixelStride)
                }
            }
        }

        private fun rotateBitmapIfNeeded(bitmap: Bitmap, degrees: Int): Bitmap {
            if (degrees == 0) return bitmap
            val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated != bitmap) bitmap.recycle()
            return rotated
        }
    }
}
