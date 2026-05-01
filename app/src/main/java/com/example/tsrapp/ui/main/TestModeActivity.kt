package com.example.tsrapp.ui.main

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.graphics.scale
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.tsrapp.R
import com.example.tsrapp.data.model.TrafficSign
import com.example.tsrapp.databinding.ActivityTestModeBinding
import com.example.tsrapp.ml.OnnxInferenceEngine
import com.example.tsrapp.util.DriverAlertFeedback
import com.example.tsrapp.util.SettingsManager
import com.example.tsrapp.util.SignLabelToSpeech
import com.example.tsrapp.util.TextToSpeechHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min

class TestModeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTestModeBinding
    private lateinit var ttsHelper: TextToSpeechHelper
    private var inferenceEngine: OnnxInferenceEngine? = null

    private var lastVideoSpokenKey: String? = null
    private var lastVideoSpokenAt: Long = 0L

    private val mainHandler = Handler(Looper.getMainLooper())
    private var videoInferRunnable: Runnable? = null
    private var inferLoopGeneration = 0
    private val videoInferBusy = AtomicBoolean(false)

    private var mediaPlayer: MediaPlayer? = null

    private val boxPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
    }
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 36f
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    private val textBgPaint = Paint().apply {
        color = Color.BLACK
        alpha = 180
        style = Paint.Style.FILL
    }

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { runInference(it) }
        }

    private val pickVideoLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { runLiveVideo(it) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTestModeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Handle edge-to-edge and system bar insets
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, systemBars.top, 0, systemBars.bottom)
            insets
        }

        ttsHelper = TextToSpeechHelper(this)
        ttsHelper.initialize { success ->
            if (!success && SettingsManager.isTtsEnabled(this)) {
                Toast.makeText(this, "Speech alerts failed to start.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.metricsAccuracy.text = getString(R.string.test_mode_accuracy_default)
        binding.metricsPrecision.text = getString(R.string.test_mode_precision_default)
        binding.metricsRecall.text = getString(R.string.test_mode_recall_default)
        binding.resultsText.text = getString(R.string.test_mode_no_results)

        binding.backButton.setOnClickListener { finish() }
        binding.pickImageButton.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }
        binding.pickVideoButton.setOnClickListener {
            pickVideoLauncher.launch("video/*")
        }
    }

    private fun setTestInputsEnabled(enabled: Boolean) {
        binding.pickImageButton.isEnabled = enabled
        binding.pickVideoButton.isEnabled = enabled
    }

    private fun stopLiveVideo() {
        inferLoopGeneration++
        videoInferRunnable?.let { mainHandler.removeCallbacks(it) }
        videoInferRunnable = null
        videoInferBusy.set(false)
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {
        }
        mediaPlayer = null
    }

    private fun showImagePreviewMode() {
        stopLiveVideo()
        lastVideoSpokenKey = null
        lastVideoSpokenAt = 0L
        binding.previewImage.visibility = View.VISIBLE
        binding.videoTexture.visibility = View.GONE
        binding.detectionOverlay.visibility = View.GONE
        binding.detectionOverlay.updateDetections(emptyList())
    }

    private fun showLiveVideoMode() {
        stopLiveVideo()
        lastVideoSpokenKey = null
        lastVideoSpokenAt = 0L
        binding.previewImage.visibility = View.GONE
        binding.videoTexture.visibility = View.VISIBLE
        binding.detectionOverlay.visibility = View.VISIBLE
        binding.detectionOverlay.updateDetections(emptyList())
    }

    private fun runInference(uri: Uri) {
        showImagePreviewMode()
        binding.resultsText.text = getString(R.string.test_mode_running_inference)
        setTestInputsEnabled(false)

        lifecycleScope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) { decodeBitmap(uri) }
                val threshold = SettingsManager.getConfidenceThreshold(this@TestModeActivity)

                val start = System.currentTimeMillis()
                val engine = inferenceEngine ?: withContext(Dispatchers.Default) {
                    OnnxInferenceEngine(this@TestModeActivity).also { inferenceEngine = it }
                }
                if (!engine.isModelLoaded) {
                    binding.resultsText.text = getString(R.string.test_mode_model_failed)
                    return@launch
                }
                val signs = withContext(Dispatchers.Default) {
                    engine.detect(bitmap, threshold)
                }
                val inferenceMs = System.currentTimeMillis() - start

                val annotated = annotateBitmap(bitmap, signs)
                binding.previewImage.setImageBitmap(annotated)

                val topConf = signs.maxOfOrNull { it.confidence } ?: 0f
                binding.metricsAccuracy.text = getString(R.string.test_mode_detections_count, signs.size)
                binding.metricsPrecision.text = getString(R.string.test_mode_top_confidence, topConf * 100)
                binding.metricsRecall.text = getString(R.string.test_mode_inference_time, inferenceMs)

                if (signs.isEmpty()) {
                    binding.resultsText.text =
                        getString(R.string.test_mode_no_signs_above_threshold, (threshold * 100).toInt())
                } else {
                    val sb = StringBuilder()
                    signs.forEachIndexed { i, sign ->
                        val name = SignLabelToSpeech.toDisplayName(sign.label)
                        sb.append("${i + 1}. $name — ${"%.1f".format(sign.confidence * 100)}%")
                        if (sign.isCritical) sb.append(" ⚠️")
                        sb.append("\n")
                    }
                    binding.resultsText.text = sb.toString().trim()
                    announceAllSignsForTest(signs)
                }
            } catch (e: Exception) {
                binding.resultsText.text = getString(R.string.test_mode_error, e.message ?: "unknown")
            } finally {
                setTestInputsEnabled(true)
            }
        }
    }

    private fun runLiveVideo(uri: Uri) {
        showLiveVideoMode()
        binding.resultsText.text = getString(R.string.test_mode_live_video_hint)
        binding.metricsAccuracy.text = getString(R.string.test_mode_live_status)
        binding.metricsPrecision.text = "—"
        binding.metricsRecall.text = "—"
        setTestInputsEnabled(false)

        lifecycleScope.launch {
            val threshold = SettingsManager.getConfidenceThreshold(this@TestModeActivity)
            val engine = inferenceEngine ?: withContext(Dispatchers.Default) {
                OnnxInferenceEngine(this@TestModeActivity).also { inferenceEngine = it }
            }
            if (!engine.isModelLoaded) {
                binding.resultsText.text = getString(R.string.test_mode_model_failed)
                setTestInputsEnabled(true)
                return@launch
            }

            withContext(Dispatchers.Main) {
                val surfaceTexture = binding.videoTexture.surfaceTexture
                if (surfaceTexture != null) {
                    startMediaPlayer(Surface(surfaceTexture), uri, engine, threshold)
                } else {
                    binding.videoTexture.surfaceTextureListener =
                        object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(
                                surface: SurfaceTexture,
                                width: Int,
                                height: Int,
                            ) {
                                binding.videoTexture.surfaceTextureListener = null
                                startMediaPlayer(Surface(surface), uri, engine, threshold)
                            }

                            override fun onSurfaceTextureSizeChanged(
                                surface: SurfaceTexture,
                                width: Int,
                                height: Int,
                            ) {
                            }

                            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                                stopLiveVideo()
                                return true
                            }

                            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
                        }
                }
            }
        }
    }

    private fun startMediaPlayer(
        surface: Surface,
        uri: Uri,
        engine: OnnxInferenceEngine,
        threshold: Float,
    ) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                try {
                    // Try direct access first (fastest)
                    contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                        setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    }
                } catch (_: Exception) {
                    // Fallback: Copy to a local cache file (most reliable)
                    val tempFile = java.io.File(cacheDir, "temp_test_video.mp4")
                    contentResolver.openInputStream(uri)?.use { input ->
                        tempFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    setDataSource(tempFile.absolutePath)
                }
                setSurface(surface)
                setOnPreparedListener { mp ->
                    if (SettingsManager.isTtsEnabled(this@TestModeActivity)) {
                        mp.setVolume(0f, 0f)
                    }
                    mp.start()
                    startVideoInferLoop(engine, threshold)
                }
                setOnCompletionListener {
                    inferLoopGeneration++
                    videoInferRunnable?.let { mainHandler.removeCallbacks(it) }
                    videoInferRunnable = null
                    videoInferBusy.set(false)
                    lastVideoSpokenKey = null
                    lastVideoSpokenAt = 0L
                    setTestInputsEnabled(true)
                    binding.resultsText.text = getString(R.string.test_mode_video_ended)
                    binding.metricsAccuracy.text = getString(R.string.test_mode_video_ended)
                }
                setOnErrorListener { _, _, _ ->
                    setTestInputsEnabled(true)
                    binding.resultsText.text = getString(R.string.test_mode_video_no_frames)
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            setTestInputsEnabled(true)
            binding.resultsText.text = getString(R.string.test_mode_video_error, e.message ?: "unknown")
        }
    }

    private fun startVideoInferLoop(engine: OnnxInferenceEngine, threshold: Float) {
        inferLoopGeneration++
        videoInferRunnable?.let { mainHandler.removeCallbacks(it) }
        val gen = inferLoopGeneration
        val runnable = object : Runnable {
            override fun run() {
                if (gen != inferLoopGeneration) return
                val mp = mediaPlayer
                if (mp == null) return

                val playing = try {
                    mp.isPlaying
                } catch (_: Exception) {
                    false
                }
                if (!playing) {
                    mainHandler.postDelayed(this, 120L)
                    return
                }

                if (videoInferBusy.get()) {
                    mainHandler.postDelayed(this, VIDEO_INFER_POLL_MS)
                    return
                }

                val tw = binding.videoTexture
                if (!tw.isAvailable) {
                    mainHandler.postDelayed(this, 100L)
                    return
                }

                val vw = tw.width
                val vh = tw.height
                if (vw <= 0 || vh <= 0) {
                    mainHandler.postDelayed(this, 100L)
                    return
                }

                val scale = min(640.0 / vw, 480.0 / vh).coerceAtMost(1.0)
                val bw = max(1, (vw * scale).toInt())
                val bh = max(1, (vh * scale).toInt())

                val bmp = grabTextureBitmap(tw, bw, bh)

                if (bmp == null) {
                    mainHandler.postDelayed(this, VIDEO_INFER_POLL_MS)
                    return
                }

                if (!videoInferBusy.compareAndSet(false, true)) {
                    bmp.recycle()
                    mainHandler.postDelayed(this, VIDEO_INFER_POLL_MS)
                    return
                }

                lifecycleScope.launch(Dispatchers.Default) {
                    try {
                        val signs = engine.detect(bmp, threshold)
                        withContext(Dispatchers.Main) {
                            if (gen == inferLoopGeneration) {
                                binding.detectionOverlay.setSourceSize(bmp.width, bmp.height)
                                binding.detectionOverlay.updateDetections(signs)
                                updateLiveMetrics(signs)
                            }
                        }
                    } finally {
                        bmp.recycle()
                        videoInferBusy.set(false)
                    }
                }

                mainHandler.postDelayed(this, VIDEO_INFER_POLL_MS)
            }
        }
        videoInferRunnable = runnable
        mainHandler.post(runnable)
    }

    private fun updateLiveMetrics(signs: List<TrafficSign>) {
        binding.metricsAccuracy.text = getString(R.string.test_mode_live_status)
        binding.metricsPrecision.text =
            if (signs.isEmpty()) "No signs in this frame" else "Detections: ${signs.size}"
        val top = signs.maxByOrNull { it.confidence }
        binding.metricsRecall.text = top?.let { s ->
            val name = SignLabelToSpeech.toDisplayName(s.label)
            "Top: $name (${"%.0f".format(s.confidence * 100)}%)"
        } ?: "—"
        if (signs.isNotEmpty()) {
            maybeAnnounceLiveVideoSigns(signs)
        }
    }

    private fun announceAllSignsForTest(signs: List<TrafficSign>) {
        if (!SettingsManager.isTtsEnabled(this)) return
        // Sort by confidence to ensure the most reliable detections are queued first
        signs.sortedByDescending { it.confidence }.forEach { sign ->
            ttsHelper.speakDrivingSign(sign.label)
            if (sign.isCritical) {
                DriverAlertFeedback.vibrateShort(this)
            }
        }
    }

    private fun maybeAnnounceLiveVideoSigns(signs: List<TrafficSign>) {
        if (!SettingsManager.isTtsEnabled(this)) return
        // Iterate through all visible signs. deduplication and cooldowns are handled
        // by the TextToSpeechHelper's priority queue and spokenHistory.
        signs.sortedByDescending { it.confidence }.forEach { sign ->
            ttsHelper.speakDrivingSign(sign.label)
            if (sign.isCritical) {
                DriverAlertFeedback.vibrateShort(this)
            }
        }
    }

    private fun annotateBitmap(bitmap: Bitmap, signs: List<TrafficSign>): Bitmap {
        val annotated = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(annotated)
        for (sign in signs) {
            val box = sign.boundingBox
            boxPaint.color = if (sign.isCritical) Color.RED else Color.GREEN
            canvas.drawRect(box.left, box.top, box.right, box.bottom, boxPaint)

            val name = SignLabelToSpeech.toDisplayName(sign.label)
            val label = "$name ${"%.0f".format(sign.confidence * 100)}%"
            val textWidth = textPaint.measureText(label)
            val textHeight = textPaint.fontMetrics.let { it.descent - it.ascent }
            canvas.drawRect(
                box.left, box.top - textHeight - 8f,
                box.left + textWidth + 16f, box.top,
                textBgPaint,
            )
            canvas.drawText(label, box.left + 8f, box.top - 8f, textPaint)
        }
        return annotated
    }

    /** [TextureView.getBitmap] with max size exists from API 26; scale on API 24–25. */
    private fun grabTextureBitmap(tw: TextureView, targetW: Int, targetH: Int): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                tw.getBitmap(targetW, targetH)
            } else {
                val full = tw.getBitmap() ?: return null
                if (full.width == targetW && full.height == targetH) full
                else full.scale(targetW, targetH).also { full.recycle() }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeBitmap(uri: Uri): Bitmap {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(contentResolver, uri)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLiveVideo()
        inferenceEngine?.close()
        ttsHelper.shutdown()
    }

    companion object {
        private const val VIDEO_INFER_POLL_MS = 350L
    }
}
