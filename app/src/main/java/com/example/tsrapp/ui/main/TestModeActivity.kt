package com.example.tsrapp.ui.main

import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.tsrapp.R
import com.example.tsrapp.databinding.ActivityTestModeBinding

class TestModeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTestModeBinding

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { showImage(it) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTestModeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initial metric placeholders
        binding.metricsAccuracy.text = getString(R.string.test_mode_accuracy_default)
        binding.metricsPrecision.text = getString(R.string.test_mode_precision_default)
        binding.metricsRecall.text = getString(R.string.test_mode_recall_default)
        binding.resultsText.text = getString(R.string.test_mode_no_results)

        binding.backButton.setOnClickListener { finish() }

        binding.pickImageButton.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }
    }

    private fun showImage(uri: Uri) {
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(contentResolver, uri)
            ImageDecoder.decodeBitmap(source)
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(contentResolver, uri)
        }
        binding.previewImage.setImageBitmap(bitmap)
        binding.previewImage.visibility = View.VISIBLE
        binding.resultsText.text = getString(R.string.test_mode_no_results)
    }
}
