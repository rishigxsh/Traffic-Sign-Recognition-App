package com.example.tsrapp.data.repository

import android.content.Context
import android.graphics.Bitmap
import com.example.tsrapp.data.model.TrafficSign
import com.example.tsrapp.ml.OnnxInferenceEngine
import com.example.tsrapp.util.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

class TSRRepository(context: Context) {

    private val appContext = context.applicationContext
    private val engine = OnnxInferenceEngine(
        context = appContext,
        region  = SettingsManager.getModelRegion(appContext)
    )

    /**
     * Detects traffic signs in a camera frame using on-device YOLOv8 ONNX inference.
     *
     * Returns an empty list if the model file (assets/model.onnx) has not been placed yet.
     * Confidence threshold is read from SettingsManager (user-configurable, default 0.5).
     *
     * @param bitmap The camera frame as a Bitmap
     * @return List of detected traffic signs with bounding boxes and labels
     */
    suspend fun detectSignsInFrame(bitmap: Bitmap): List<TrafficSign> {
        if (!engine.isModelLoaded) return emptyList()
        val threshold = SettingsManager.getConfidenceThreshold(appContext)
        return withContext(Dispatchers.Default) {
            if (!isActive) return@withContext emptyList()
            try {
                engine.detect(bitmap, threshold)
            } catch (_: IllegalStateException) {
                // Engine was closed mid-inference (activity/viewmodel destroyed)
                emptyList()
            }
        }
    }

    fun close() {
        engine.close()
    }
}
