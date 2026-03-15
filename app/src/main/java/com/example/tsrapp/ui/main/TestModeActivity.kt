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
import com.example.tsrapp.databinding.ActivityTestModeBinding
import com.example.tsrapp.ml.OnnxInferenceEngine
import com.example.tsrapp.util.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TestModeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTestModeBinding
    private lateinit var inferenceEngine: OnnxInferenceEngine

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTestModeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        inferenceEngine = OnnxInferenceEngine(this)

        binding.metricsAccuracy.text = getString(R.string.test_mode_accuracy_default)
        binding.metricsPrecision.text = getString(R.string.test_mode_precision_default)
        binding.metricsRecall.text = getString(R.string.test_mode_recall_default)
        binding.resultsText.text = getString(R.string.test_mode_no_results)

        binding.backButton.setOnClickListener { finish() }
        binding.pickImageButton.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }
    }

    private fun runInference(uri: Uri) {
        binding.resultsText.text = "Running inference..."
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
            binding.metricsAccuracy.text = "Detections: ${signs.size}"
            binding.metricsPrecision.text = "Top confidence: ${"%.1f".format(topConf * 100)}%"
            binding.metricsRecall.text = "Inference time: ${inferenceMs}ms"

            if (signs.isEmpty()) {
                binding.resultsText.text = "No signs detected above ${(threshold * 100).toInt()}% confidence."
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
        inferenceEngine.close()
    }
}
