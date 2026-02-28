package com.example.tsrapp.data.repository

import android.content.Context
import android.graphics.Bitmap
import com.example.tsrapp.data.model.TrafficSign
import com.example.tsrapp.ml.OnnxInferenceEngine
import com.example.tsrapp.util.SettingsManager

class TSRRepository(context: Context) {

    private val engine = OnnxInferenceEngine(context)
    private val appContext = context.applicationContext

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
        return engine.detect(bitmap, threshold)
    }

    fun close() {
        engine.close()
    }
}
