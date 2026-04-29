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
import org.json.JSONObject
import java.nio.FloatBuffer
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Runs YOLOv8 ONNX inference on-device for traffic sign detection.
 *
 * Model and class list are loaded from: app/src/main/assets/
 *   Files are determined by [region] — see [ModelRegion] for asset name mappings.
 *
 * Model input:  [1, 3, 640, 640] float32, RGB, normalized [0, 1]
 * Model output: [1, 4+num_classes, 8400]
 *   Rows 0-3  → bounding box [cx, cy, w, h] in normalized 640x640 space
 *   Rows 4+   → class confidence scores
 */
class OnnxInferenceEngine(
    context: Context,
    region: ModelRegion = ModelRegion.US
) {

    private val ortEnv: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var ortSession: OrtSession?
    private val cascadeClassifier: CascadeClassifier? =
        if (region == ModelRegion.US) CascadeClassifier(context) else null

    /**
     * Guards [ortSession] access so [close] cannot destroy the session while
     * [detect] is executing native ONNX code. Multiple [detect] calls can run
     * concurrently (read lock); [close] waits for all of them to finish first
     * (write lock).
     */
    private val sessionLock = ReentrantReadWriteLock()

    /** Class names loaded from assets/classes.json, index == key */
    val classNames: Array<String>

    /** Number of classes derived from classNames */
    private val numClasses: Int

    companion object {
        private const val TAG = "OnnxInferenceEngine"
        private const val INPUT_SIZE    = 640
        private const val NUM_ANCHORS   = 8400
        private const val IOU_THRESHOLD = 0.45f

        // NNAPI requires API 27+ and a native ARM device.
        // GPU support is disabled for emulator
        fun supportsNnapi(): Boolean =
            Build.VERSION.SDK_INT >= 27 &&
                (Build.SUPPORTED_ABIS.firstOrNull()?.startsWith("arm") == true)
    }

    private val modelFile   = region.modelFile
    private val classesFile = region.classesFile

    init {
        // --- Load class names ---
        classNames = try {
            val json = context.assets.open(classesFile).bufferedReader().readText()
            val obj  = JSONObject(json)
            Array(obj.length()) { i -> obj.getString(i.toString()) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load $classesFile", e)
            emptyArray()
        }
        numClasses = classNames.size
        Log.i(TAG, "Loaded $numClasses classes from $classesFile")

        // --- Load ONNX model ---
        ortSession = try {
            val modelBytes = context.assets.open(modelFile).readBytes()
            val opts = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(4)
                if (supportsNnapi()) {
                    try {
                        addNnapi()
                        Log.i(TAG, "NNAPI execution provider enabled for $modelFile")
                    } catch (e: Exception) {
                        Log.w(TAG, "NNAPI unavailable, falling back to CPU: ${e.message}")
                    }
                }
            }
            val session = ortEnv.createSession(modelBytes, opts)
            Log.i(TAG, "ONNX session created from $modelFile (region=${region.displayName})")
            session
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load $modelFile", e)
            null
        }
    }

    val isModelLoaded: Boolean get() = ortSession != null

    /**
     * Detects traffic signs in [bitmap] above [confidenceThreshold].
     * Returns an empty list if the model has not been loaded yet.
     */
    fun detect(bitmap: Bitmap, confidenceThreshold: Float): List<TrafficSign> {
        return sessionLock.read {
            val session = ortSession ?: return emptyList()
            if (numClasses == 0) return emptyList()

            // 1. Preprocess: letterbox to 640x640, normalize, NCHW float array
            val inputData = preprocess(bitmap)
            val shape     = longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong())

            // 2. Run inference
            val inputName   = session.inputNames.iterator().next()
            val inputTensor = OnnxTensor.createTensor(ortEnv, FloatBuffer.wrap(inputData), shape)
            val results     = session.run(mapOf(inputName to inputTensor))

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
                val x1 = (cx - w / 2f - letterboxPadX) / letterboxScale
                val y1 = (cy - h / 2f - letterboxPadY) / letterboxScale
                val x2 = (cx + w / 2f - letterboxPadX) / letterboxScale
                val y2 = (cy + h / 2f - letterboxPadY) / letterboxScale

                detections.add(floatArrayOf(x1, y1, x2, y2, maxScore, classId.toFloat()))
            }

            // 4. Non-Maximum Suppression
            val kept = nms(detections)

            // 5. Map to TrafficSign domain objects — clamp boxes to bitmap bounds,
            //    enforce minimum size before clamping so coerce order never violates bounds
            return kept.mapNotNull { det ->
                val id             = det[5].toInt()
                val detectorLabel  = classNames.getOrElse(id) { "Unknown ($id)" }

                val bitmapW = bitmap.width.toFloat()
                val bitmapH = bitmap.height.toFloat()

                // Clamp all four edges to bitmap bounds
                val left   = det[0].coerceIn(0f, bitmapW)
                val top    = det[1].coerceIn(0f, bitmapH)
                val right  = det[2].coerceIn(0f, bitmapW)
                val bottom = det[3].coerceIn(0f, bitmapH)

                // Skip detections that are completely outside or have zero area after clamping
                if (right <= left || bottom <= top) return@mapNotNull null

                // Apply cascade classifier (US only) to refine the detector label
                val box   = android.graphics.RectF(left, top, right, bottom)
                val label = cascadeClassifier?.refine(bitmap, box, detectorLabel) ?: detectorLabel

                TrafficSign(
                    label       = label,
                    confidence  = det[4],
                    boundingBox = TrafficSign.BoundingBox(
                        left   = left,
                        top    = top,
                        right  = right,
                        bottom = bottom
                    ),
                    isCritical = false
                )
            }
        } // end sessionLock.read
    }

    // Letterbox to 640x640, normalize [0,1], return NCHW float array.
    private var letterboxPadX = 0
    private var letterboxPadY = 0
    private var letterboxScale = 1f

    private fun preprocess(bitmap: Bitmap): FloatArray {
        val srcW = bitmap.width
        val srcH = bitmap.height
        letterboxScale = minOf(INPUT_SIZE.toFloat() / srcW, INPUT_SIZE.toFloat() / srcH)
        val newW = (srcW * letterboxScale).toInt()
        val newH = (srcH * letterboxScale).toInt()
        letterboxPadX = (INPUT_SIZE - newW) / 2
        letterboxPadY = (INPUT_SIZE - newH) / 2

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
        return floatArray
    }

    // Greedy NMS: sort by confidence descending, suppress boxes with IoU >= threshold
    private fun nms(detections: MutableList<FloatArray>): List<FloatArray> {
        if (detections.isEmpty()) return emptyList()
        detections.sortByDescending { it[4] }
        val kept       = mutableListOf<FloatArray>()
        val suppressed = BooleanArray(detections.size)
        for (i in detections.indices) {
            if (suppressed[i]) continue
            kept.add(detections[i])
            for (j in i + 1 until detections.size) {
                if (!suppressed[j] && iou(detections[i], detections[j]) >= IOU_THRESHOLD) {
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
        sessionLock.write {
            ortSession?.close()
            ortSession = null
        }
        cascadeClassifier?.close()
        ortEnv.close()
    }
}
