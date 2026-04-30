package com.example.tsrapp.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.util.Log
import com.example.tsrapp.data.model.TrafficSign
import com.example.tsrapp.util.SignLabelToSpeech
import org.json.JSONObject
import java.nio.FloatBuffer

/**
 * Runs YOLOv8 ONNX inference on-device for traffic sign detection.
 *
 * Model and class list are loaded from: app/src/main/assets/US/
 *   - best.onnx          : exported YOLOv8 model
 *   - classes.json       : {"0": "class-name", "1": "class-name", ...}
 *
 * Model input:  [1, 3, 640, 640] float32, RGB, normalized [0, 1]
 * Model output: [1, 4+num_classes, 8400]
 *   Rows 0-3  → bounding box [cx, cy, w, h] in normalized 640x640 space
 *   Rows 4+   → class confidence scores
 */
class OnnxInferenceEngine(context: Context) {

    private val ortEnv: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val ortSession: OrtSession?

    /** Class names loaded from assets/US/classes.json, index == key */
    val classNames: Array<String>

    /** Number of classes derived from classNames */
    private val numClasses: Int

    companion object {
        private const val TAG = "OnnxInferenceEngine"
        private const val MODEL_FILE    = "US/best.onnx"
        private const val CLASSES_FILE  = "US/classes.json"
        private const val INPUT_SIZE    = 640
        private const val NUM_ANCHORS   = 8400
        private const val IOU_THRESHOLD = 0.45f
    }

    private data class PreprocessedFrame(
        val inputData: FloatArray,
        val padX: Int,
        val padY: Int,
        val scale: Float,
    )

    init {
        // --- Load class names ---
        classNames = try {
            val json = context.assets.open(CLASSES_FILE).bufferedReader().readText()
            val obj  = JSONObject(json)
            Array(obj.length()) { i -> obj.getString(i.toString()) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load $CLASSES_FILE", e)
            emptyArray()
        }
        numClasses = classNames.size
        Log.i(TAG, "Loaded $numClasses classes from $CLASSES_FILE")

        // --- Load ONNX model ---
        val modelBytes = try {
            context.assets.open(MODEL_FILE).readBytes()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read $MODEL_FILE", e)
            null
        }
        ortSession = if (modelBytes == null) null else {
            // Try NNAPI first for ~5-10x speedup; some models have ops NNAPI can't handle,
            // so fall back to CPU in that case.
            var session: OrtSession? = null
            if (!isEmulator()) {
                try {
                    val opts = OrtSession.SessionOptions().apply {
                        addNnapi()
                        setIntraOpNumThreads(4)
                    }
                    session = ortEnv.createSession(modelBytes, opts)
                    Log.i(TAG, "ONNX session created with NNAPI")
                } catch (e: Exception) {
                    Log.w(TAG, "NNAPI failed (${e.message}), retrying on CPU")
                }
            }
            if (session == null) {
                try {
                    val opts = OrtSession.SessionOptions().apply { setIntraOpNumThreads(4) }
                    session = ortEnv.createSession(modelBytes, opts)
                    Log.i(TAG, "ONNX session created on CPU")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load $MODEL_FILE on CPU", e)
                }
            }
            session
        }
    }

    val isModelLoaded: Boolean get() = ortSession != null

    /**
     * Detects traffic signs in [bitmap] above [confidenceThreshold].
     * Returns an empty list if the model has not been loaded yet.
     */
    fun detect(bitmap: Bitmap, confidenceThreshold: Float): List<TrafficSign> {
        val session = ortSession ?: return emptyList()
        if (numClasses == 0) return emptyList()

        // 1. Preprocess: letterbox to 640x640, normalize, NCHW float array
        val preprocessed = preprocess(bitmap)
        val shape     = longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong())

        // 2. Run inference
        val inputName  = session.inputNames.iterator().next()
        return OnnxTensor.createTensor(ortEnv, FloatBuffer.wrap(preprocessed.inputData), shape).use { inputTensor ->
            session.run(mapOf(inputName to inputTensor)).use { results ->
                // 3. Parse output: shape [1][4+numClasses][8400]
                @Suppress("UNCHECKED_CAST")
                val output = (results[0].value as Array<Array<FloatArray>>)[0]

                val detections = mutableListOf<FloatArray>() // [x1, y1, x2, y2, conf, classId]

                for (a in 0 until NUM_ANCHORS) {
                    val cx = output[0][a]
                    val cy = output[1][a]
                    val w  = output[2][a]
                    val h  = output[3][a]

                    var maxScore = 0f
                    var classId  = -1
                    for (c in 0 until numClasses) {
                        val score = output[4 + c][a]
                        if (score > maxScore) {
                            maxScore = score
                            classId  = c
                        }
                    }

                    if (maxScore < confidenceThreshold) continue

                    // Map from 640x640 letterbox space back to original bitmap coordinates
                    val x1 = (cx - w / 2f - preprocessed.padX) / preprocessed.scale
                    val y1 = (cy - h / 2f - preprocessed.padY) / preprocessed.scale
                    val x2 = (cx + w / 2f - preprocessed.padX) / preprocessed.scale
                    val y2 = (cy + h / 2f - preprocessed.padY) / preprocessed.scale

                    detections.add(floatArrayOf(x1, y1, x2, y2, maxScore, classId.toFloat()))
                }

                // 4. Non-Maximum Suppression
                val kept = nms(detections, IOU_THRESHOLD)

                // 5. Map to TrafficSign domain objects
                kept.map { det ->
                    val id    = det[5].toInt()
                    val label = classNames.getOrElse(id) { "Unknown ($id)" }
                    TrafficSign(
                        label      = label,
                        confidence = det[4],
                        boundingBox = TrafficSign.BoundingBox(
                            left   = det[0].coerceAtLeast(0f),
                            top    = det[1].coerceAtLeast(0f),
                            right  = det[2].coerceAtMost(bitmap.width.toFloat()),
                            bottom = det[3].coerceAtMost(bitmap.height.toFloat())
                        ),
                        isCritical = SignLabelToSpeech.isCriticalRoadAlert(label)
                    )
                }
            }
        }
    }

    // Letterbox to 640x640, normalize [0,1], return NCHW float array.
    private fun preprocess(bitmap: Bitmap): PreprocessedFrame {
        val srcW = bitmap.width
        val srcH = bitmap.height
        val letterboxScale = minOf(INPUT_SIZE.toFloat() / srcW, INPUT_SIZE.toFloat() / srcH)
        val newW = (srcW * letterboxScale).toInt()
        val newH = (srcH * letterboxScale).toInt()
        val letterboxPadX = (INPUT_SIZE - newW) / 2
        val letterboxPadY = (INPUT_SIZE - newH) / 2

        val resized     = Bitmap.createScaledBitmap(bitmap, newW, newH, true)
        val letterboxed = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888)
        val canvas      = android.graphics.Canvas(letterboxed)
        canvas.drawColor(Color.rgb(114, 114, 114))
        canvas.drawBitmap(resized, letterboxPadX.toFloat(), letterboxPadY.toFloat(), null)

        val pixels      = IntArray(INPUT_SIZE * INPUT_SIZE)
        letterboxed.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        val floatArray  = FloatArray(3 * INPUT_SIZE * INPUT_SIZE)
        val channelSize = INPUT_SIZE * INPUT_SIZE
        for (i in pixels.indices) {
            val px = pixels[i]
            floatArray[i]                   = Color.red(px)   / 255f
            floatArray[channelSize + i]     = Color.green(px) / 255f
            floatArray[2 * channelSize + i] = Color.blue(px)  / 255f
        }

        if (resized != bitmap) resized.recycle()
        letterboxed.recycle()
        return PreprocessedFrame(
            inputData = floatArray,
            padX = letterboxPadX,
            padY = letterboxPadY,
            scale = letterboxScale,
        )
    }

    // Greedy NMS: sort by confidence descending, suppress boxes with IoU >= threshold
    private fun nms(detections: MutableList<FloatArray>, iouThreshold: Float): List<FloatArray> {
        if (detections.isEmpty()) return emptyList()
        detections.sortByDescending { it[4] }
        val kept       = mutableListOf<FloatArray>()
        val suppressed = BooleanArray(detections.size)
        for (i in detections.indices) {
            if (suppressed[i]) continue
            kept.add(detections[i])
            for (j in i + 1 until detections.size) {
                if (!suppressed[j] && iou(detections[i], detections[j]) >= iouThreshold) {
                    suppressed[j] = true
                }
            }
        }
        return kept
    }

    private fun iou(a: FloatArray, b: FloatArray): Float {
        val interX1   = maxOf(a[0], b[0])
        val interY1   = maxOf(a[1], b[1])
        val interX2   = minOf(a[2], b[2])
        val interY2   = minOf(a[3], b[3])
        val interArea = maxOf(0f, interX2 - interX1) * maxOf(0f, interY2 - interY1)
        val aArea     = (a[2] - a[0]) * (a[3] - a[1])
        val bArea     = (b[2] - b[0]) * (b[3] - b[1])
        val unionArea = aArea + bArea - interArea
        return if (unionArea <= 0f) 0f else interArea / unionArea
    }

    fun close() {
        ortSession?.close()
        ortEnv.close()
    }

    private fun isEmulator(): Boolean =
        Build.FINGERPRINT.startsWith("generic") ||
        Build.FINGERPRINT.startsWith("unknown") ||
        Build.MODEL.contains("Emulator", ignoreCase = true) ||
        Build.MODEL.contains("Android SDK", ignoreCase = true) ||
        Build.MANUFACTURER.contains("Genymotion", ignoreCase = true) ||
        Build.HARDWARE == "goldfish" ||
        Build.HARDWARE == "ranchu"
}
