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
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.lifecycle.Observer
import com.example.tsrapp.R
import com.example.tsrapp.databinding.ActivityMainBinding
import com.example.tsrapp.util.DriverAlertFeedback
import com.example.tsrapp.util.SettingsManager
import com.example.tsrapp.util.SignLabelToSpeech
import com.example.tsrapp.util.TextToSpeechHelper
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var ttsHelper: TextToSpeechHelper
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
        ttsHelper.initialize { success ->
            val isMuted = SettingsManager.getVoiceMode(this) == SettingsManager.VOICE_MUTED
            if (!success && !isMuted) {
                Toast.makeText(this, "Speech alerts unavailable.", Toast.LENGTH_SHORT).show()
            }
        }

        setupFloatingControls()
        setupBottomPanel()
        setupTopBanner()
        observeDetections()

        binding.backButton.setOnClickListener { finish() }
        showIdleState()
    }

    private fun setupFloatingControls() {
        // Voice settings trigger
        updateMuteButtonIcon()
        
        binding.muteButton.setOnClickListener {
            val sheet = VoiceSettingsBottomSheet()
            sheet.show(supportFragmentManager, VoiceSettingsBottomSheet.TAG)
        }

        // Stop detection (Quick access)
        binding.stopButton.setOnClickListener {
            if (isDetecting) stopCamera()
        }
    }

    private fun updateMuteButtonIcon() {
        val mode = SettingsManager.getVoiceMode(this)
        val iconRes = when(mode) {
            SettingsManager.VOICE_MUTED -> R.drawable.ic_mute
            SettingsManager.VOICE_ALERTS -> R.drawable.ic_alerts_only
            else -> R.drawable.ic_unmute
        }
        binding.muteButton.setImageResource(iconRes)
    }

    private fun setupBottomPanel() {
        // Threshold slider
        val initialThreshold = SettingsManager.getConfidenceThreshold(this)
        binding.thresholdSlider.progress = (initialThreshold * 100).toInt()
        binding.statusThreshold.text = "${(initialThreshold * 100).toInt()}%"

        binding.thresholdSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val floatVal = progress / 100f
                    SettingsManager.setConfidenceThreshold(this@MainActivity, floatVal)
                    binding.statusThreshold.text = "$progress%"
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Toggle detection
        binding.toggleDetectionButton.setOnClickListener {
            if (isDetecting) stopCamera() else startDetectionFlow()
        }
    }

    private fun setupTopBanner() {
        // Initially "Searching..."
        binding.signBadgeSymbol.text = "🔍"
    }
    private fun observeDetections() {
        viewModel.detectedSigns.observe(this, Observer { signs ->
            binding.detectionOverlay.updateDetections(signs)
            
            val topSign = signs.firstOrNull()
            if (topSign != null) {
                // Update top floating banner
                binding.signBadgeSymbol.text = SignLabelToSpeech.getSymbol(topSign.label)
                binding.detectedSignName.text = SignLabelToSpeech.toDisplayName(topSign.label)
                binding.detectedSignType.text = "Highest confidence detected"
                binding.detectedConfidence.text = "${(topSign.confidence * 100).toInt()}%"
                
                // Voice alert (Queue all signs; cooldowns handled by helper)
                signs.sortedByDescending { it.confidence }.forEach { sign ->
                    ttsHelper.speakDrivingSign(sign.label)
                }
            }
        })
        
        viewModel.inferenceTimeMs.observe(this, Observer { ms ->
            binding.statusInference.text = if (ms != null && ms > 0) "${ms}ms" else "--"
        })
        
        viewModel.fps.observe(this, Observer { valF ->
            binding.statusFps.text = if (valF != null) "%.1f".format(valF) else "--"
        })
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
        binding.scanGridOverlay.visibility = View.VISIBLE
        binding.toggleDetectionButton.text = "STOP"
        binding.toggleDetectionButton.alpha = 0.8f
    }

    private fun startDetectionFlow() {
        if (isInitializingCamera) return
        val hasPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
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
            val provider = future.get()
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
            try {
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis)
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

    private class FrameAnalyzer(private val onFrameAvailable: (Bitmap) -> Unit) : ImageAnalysis.Analyzer {
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
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: createBitmap(imageProxy.width, imageProxy.height, Bitmap.Config.ARGB_8888)
        }
    }
}

