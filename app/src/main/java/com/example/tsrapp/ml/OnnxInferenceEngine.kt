package com.example.tsrapp.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.example.tsrapp.data.model.TrafficSign
import java.nio.FloatBuffer

/**
 * Runs YOLOv8 ONNX inference on-device for traffic sign detection.
 *
 * Place the exported model at: app/src/main/assets/model.onnx
 * Export from the Python pipeline: python model/export_to_onnx.py
 *
 * Model input:  [1, 3, 640, 640] float32, RGB, normalized [0, 1]
 * Model output: [1, 47, 8400]    where 47 = 4 (cx,cy,w,h) + 43 class scores
 */
class OnnxInferenceEngine(context: Context) {

    private val ortEnv: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val ortSession: OrtSession?

    companion object {
        private const val MODEL_FILE = "model.onnx"
        private const val INPUT_SIZE = 640
        private const val NUM_CLASSES = 43
        private const val NUM_ANCHORS = 8400
        private const val IOU_THRESHOLD = 0.45f

        // GTSRB 43-class label names (class index matches training order)
        val CLASS_NAMES = arrayOf(
            "Speed limit (20km/h)",
            "Speed limit (30km/h)",
            "Speed limit (50km/h)",
            "Speed limit (60km/h)",
            "Speed limit (70km/h)",
            "Speed limit (80km/h)",
            "End of speed limit (80km/h)",
            "Speed limit (100km/h)",
            "Speed limit (120km/h)",
            "No passing",
            "No passing (>3.5t)",
            "Right-of-way at intersection",
            "Priority road",
            "Yield",
            "Stop",
            "No vehicles",
            "No vehicles (>3.5t)",
            "No entry",
            "General caution",
            "Dangerous curve left",
            "Dangerous curve right",
            "Double curve",
            "Bumpy road",
            "Slippery road",
            "Road narrows on right",
            "Road work",
            "Traffic signals",
            "Pedestrians",
            "Children crossing",
            "Bicycles crossing",
            "Beware of ice/snow",
            "Wild animals crossing",
            "End of all limits",
            "Turn right ahead",
            "Turn left ahead",
            "Ahead only",
            "Go straight or right",
            "Go straight or left",
            "Keep right",
            "Keep left",
            "Roundabout mandatory",
            "End of no passing",
            "End of no passing (>3.5t)"
        )

        // Class IDs that trigger urgent voice + visual alerts
        private val CRITICAL_CLASS_IDS = setOf(
            14, // Stop
            13, // Yield
            17, // No entry
            0, 1, 2, 3, 4, 5, 7, 8 // Speed limits
        )
    }

    init {
        ortSession = try {
            val modelBytes = context.assets.open(MODEL_FILE).readBytes()
            ortEnv.createSession(modelBytes, OrtSession.SessionOptions())
        } catch (e: Exception) {
            // Model not yet available — will return empty detections until placed in assets
            null
        }
    }

    val isModelLoaded: Boolean get() = ortSession != null

    /**
     * Detects traffic signs in [bitmap] above [confidenceThreshold].
     * Returns an empty list if the model has not been loaded yet.
     */
    fun detect(bitmap: Bitmap, confidenceThreshold: Float): List<TrafficSign> {
        val session = ortSession ?: return emptyList()

        // 1. Preprocess: resize to 640x640, normalize, convert to NCHW float array
        val inputData = preprocess(bitmap)
        val shape = longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong())

        // 2. Run inference
        val inputName = session.inputNames.iterator().next()
        val inputTensor = OnnxTensor.createTensor(ortEnv, FloatBuffer.wrap(inputData), shape)
        val results = session.run(mapOf(inputName to inputTensor))

        // 3. Parse output: shape [1][47][8400]
        //    Rows 0-3  → bounding box [cx, cy, w, h] in normalized 640x640 space
        //    Rows 4-46 → class confidence scores
        @Suppress("UNCHECKED_CAST")
        val output = (results[0].value as Array<Array<FloatArray>>)[0]

        val scaleX = bitmap.width.toFloat() / INPUT_SIZE
        val scaleY = bitmap.height.toFloat() / INPUT_SIZE

        val detections = mutableListOf<FloatArray>() // [x1, y1, x2, y2, conf, classId]

        for (a in 0 until NUM_ANCHORS) {
            val cx = output[0][a]
            val cy = output[1][a]
            val w  = output[2][a]
            val h  = output[3][a]

            // Find best class score across all 43 classes
            var maxScore = 0f
            var classId = -1
            for (c in 0 until NUM_CLASSES) {
                val score = output[4 + c][a]
                if (score > maxScore) {
                    maxScore = score
                    classId = c
                }
            }

            if (maxScore < confidenceThreshold) continue

            // Convert normalized center format → pixel corner format
            val x1 = (cx - w / 2f) * INPUT_SIZE * scaleX
            val y1 = (cy - h / 2f) * INPUT_SIZE * scaleY
            val x2 = (cx + w / 2f) * INPUT_SIZE * scaleX
            val y2 = (cy + h / 2f) * INPUT_SIZE * scaleY

            detections.add(floatArrayOf(x1, y1, x2, y2, maxScore, classId.toFloat()))
        }

        // 4. Non-Maximum Suppression to remove overlapping boxes
        val kept = nms(detections, IOU_THRESHOLD)

        // 5. Map to TrafficSign domain objects
        return kept.map { det ->
            val id = det[5].toInt()
            TrafficSign(
                label = CLASS_NAMES.getOrElse(id) { "Unknown ($id)" },
                confidence = det[4],
                boundingBox = TrafficSign.BoundingBox(
                    left   = det[0].coerceAtLeast(0f),
                    top    = det[1].coerceAtLeast(0f),
                    right  = det[2].coerceAtMost(bitmap.width.toFloat()),
                    bottom = det[3].coerceAtMost(bitmap.height.toFloat())
                ),
                isCritical = id in CRITICAL_CLASS_IDS
            )
        }
    }

    // Resize bitmap to 640x640, normalize to [0,1], return NCHW float array
    private fun preprocess(bitmap: Bitmap): FloatArray {
        val resized = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        resized.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        val floatArray = FloatArray(3 * INPUT_SIZE * INPUT_SIZE)
        val channelSize = INPUT_SIZE * INPUT_SIZE

        for (i in pixels.indices) {
            val px = pixels[i]
            floatArray[i]                      = Color.red(px)   / 255f  // R channel
            floatArray[channelSize + i]        = Color.green(px) / 255f  // G channel
            floatArray[2 * channelSize + i]    = Color.blue(px)  / 255f  // B channel
        }

        if (resized != bitmap) resized.recycle()
        return floatArray
    }

    // Greedy NMS: sort by confidence, suppress boxes with IoU >= threshold
    private fun nms(detections: MutableList<FloatArray>, iouThreshold: Float): List<FloatArray> {
        if (detections.isEmpty()) return emptyList()
        detections.sortByDescending { it[4] }

        val kept = mutableListOf<FloatArray>()
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
        val interX1 = maxOf(a[0], b[0])
        val interY1 = maxOf(a[1], b[1])
        val interX2 = minOf(a[2], b[2])
        val interY2 = minOf(a[3], b[3])
        val interArea = maxOf(0f, interX2 - interX1) * maxOf(0f, interY2 - interY1)
        val aArea = (a[2] - a[0]) * (a[3] - a[1])
        val bArea = (b[2] - b[0]) * (b[3] - b[1])
        val unionArea = aArea + bArea - interArea
        return if (unionArea <= 0f) 0f else interArea / unionArea
    }

    fun close() {
        ortSession?.close()
        ortEnv.close()
    }
}
