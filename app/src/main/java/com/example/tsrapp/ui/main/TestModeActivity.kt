package com.example.tsrapp.ui.main

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.tsrapp.R
import com.example.tsrapp.data.model.TrafficSign
import com.example.tsrapp.databinding.ActivityTestModeBinding
import com.example.tsrapp.ml.OnnxInferenceEngine
import com.example.tsrapp.ml.VideoFileInference
import com.example.tsrapp.util.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.BufferOverflow

class TestModeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTestModeBinding
    private lateinit var inferenceEngine: OnnxInferenceEngine
    private lateinit var videoFileInference: VideoFileInference

    private var videoJob: Job? = null

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

    // Launcher that filters for video files
    private val pickVideoLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { runVideoInference(it) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTestModeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        inferenceEngine = OnnxInferenceEngine(
            context = this,
            region  = SettingsManager.getModelRegion(this)
        )

        binding.metricsAccuracy.text = getString(R.string.test_mode_accuracy_default)
        binding.metricsPrecision.text = getString(R.string.test_mode_precision_default)
        binding.metricsRecall.text = getString(R.string.test_mode_recall_default)
        binding.resultsText.text = getString(R.string.test_mode_no_results)

        binding.backButton.setOnClickListener { finish() }

        binding.pickImageButton.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        binding.pickVideoButton.setOnClickListener {
            // Cancel any running video job before starting a new one
            videoJob?.cancel()
            pickVideoLauncher.launch("video/*")
        }
    }

    // Run inference for image files
    private fun runInference(uri: Uri) {
        binding.resultsText.text = getString(R.string.test_mode_running_inference)
        binding.pickImageButton.isEnabled = false

        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) { decodeBitmap(uri) }
            val threshold = SettingsManager.getConfidenceThreshold(this@TestModeActivity)

            val start = System.currentTimeMillis()
            val signs = withContext(Dispatchers.Default) {
                inferenceEngine.detect(bitmap, threshold)
            }
            val inferenceMs = System.currentTimeMillis() - start

            // Draw bounding boxes on a copy of the bitmap
            val annotated = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(annotated)

            for (sign in signs) {
                val box = sign.boundingBox
                boxPaint.color = if (sign.isCritical) Color.RED else Color.GREEN
                canvas.drawRect(box.left, box.top, box.right, box.bottom, boxPaint)

                val label = "${sign.label} ${"%.0f".format(sign.confidence * 100)}%"
                val textWidth = textPaint.measureText(label)
                val textHeight = textPaint.fontMetrics.let { it.descent - it.ascent }
                canvas.drawRect(
                    box.left, box.top - textHeight - 8f,
                    box.left + textWidth + 16f, box.top,
                    textBgPaint
                )
                canvas.drawText(label, box.left + 8f, box.top - 8f, textPaint)
            }

            binding.previewImage.setImageBitmap(annotated)
            binding.previewImage.visibility = View.VISIBLE

            // Update metrics
            val topConf = signs.maxOfOrNull { it.confidence } ?: 0f
            binding.metricsAccuracy.text = getString(R.string.test_mode_metric_detections, signs.size)
            binding.metricsPrecision.text = getString(R.string.test_mode_metric_top_confidence, topConf * 100)
            binding.metricsRecall.text = getString(R.string.test_mode_metric_inference_time, inferenceMs)

            if (signs.isEmpty()) {
                binding.resultsText.text = getString(R.string.test_mode_no_signs_threshold, (threshold * 100).toInt())
            } else {
                val sb = StringBuilder()
                signs.forEachIndexed { i, sign ->
                    sb.append("${i + 1}. ${sign.label} — ${"%.1f".format(sign.confidence * 100)}%")
                    if (sign.isCritical) sb.append(" ⚠️")
                    sb.append("\n")
                }
                binding.resultsText.text = sb.toString().trim()
            }

            binding.pickImageButton.isEnabled = true
        }
    }

    // Run inference for video files
    private fun runVideoInference(uri: Uri) {
        binding.resultsText.text = getString(R.string.test_mode_processing_video)
        binding.pickImageButton.isEnabled = false
        binding.pickVideoButton.isEnabled = false
        binding.previewImage.visibility = View.VISIBLE

        val threshold = SettingsManager.getConfidenceThreshold(this)
        videoFileInference = VideoFileInference(inferenceEngine, threshold)

        // Channel buffers up to 2 annotated frames; drops old ones if UI is slow
        val frameChannel = Channel<Pair<Bitmap, String>>(capacity = 2, onBufferOverflow = BufferOverflow.DROP_OLDEST)

        videoJob = lifecycleScope.launch {
            // Producer: inference on Default dispatcher
            launch(Dispatchers.Default) {
                var frameCount = 0
                var fpsFrameCount = 0
                var lastFpsTime = System.currentTimeMillis()
                videoFileInference.processVideo(
                    context = this@TestModeActivity,
                    uri = uri,
                    frameIntervalMs = 1000L
                ) { timestampMs, frame, signs ->
                    frameCount++
                    fpsFrameCount++

                    val now = System.currentTimeMillis()
                    val elapsed = now - lastFpsTime

                    // Recalculate FPS every second
                    if (elapsed >= 1000L) {
                        val fps = fpsFrameCount * 1000f / elapsed
                        fpsFrameCount = 0
                        lastFpsTime = now

                        withContext(Dispatchers.Main) {
                            binding.fpsText.text = getString(R.string.test_mode_fps, fps)
                        }
                    }

                    val annotated = annotateFrame(frame, signs)
                    val label = buildLabel(frameCount, timestampMs, signs)
                    frameChannel.trySend(annotated to label) // non-blocking, drops if full
                }
                frameChannel.close()
            }

            // Consumer: UI updates on Main dispatcher
            launch(Dispatchers.Main) {
                for ((bitmap, label) in frameChannel) {
                    binding.previewImage.setImageBitmap(bitmap)
                    binding.resultsText.text = label
                }
                binding.resultsText.text = getString(R.string.test_mode_video_done)
                binding.pickImageButton.isEnabled = true
                binding.pickVideoButton.isEnabled = true
            }
        }
    }

    private fun annotateFrame(frame: Bitmap, signs: List<TrafficSign>): Bitmap {
        val annotated = frame.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(annotated)
        for (sign in signs) {
            val box = sign.boundingBox
            boxPaint.color = if (sign.isCritical) Color.RED else Color.GREEN
            canvas.drawRect(box.left, box.top, box.right, box.bottom, boxPaint)
            val label = "${sign.label} ${"%.0f".format(sign.confidence * 100)}%"
            val textWidth = textPaint.measureText(label)
            val textHeight = textPaint.fontMetrics.let { it.descent - it.ascent }
            canvas.drawRect(box.left, box.top - textHeight - 8f, box.left + textWidth + 16f, box.top, textBgPaint)
            canvas.drawText(label, box.left + 8f, box.top - 8f, textPaint)
        }
        return annotated
    }

    private fun buildLabel(frameCount: Int, timestampMs: Long, signs: List<TrafficSign>): String {
        return if (signs.isEmpty()) getString(R.string.test_mode_video_frame_label, frameCount, timestampMs)
        else signs.joinToString("\n") { "${it.label} ${"%.1f".format(it.confidence * 100)}%" }
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
        videoJob?.cancel() // Stop video processing if activity is destroyed
        inferenceEngine.close()
    }
}
