package com.example.tsrapp.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import android.util.Log
import org.json.JSONObject
import java.nio.FloatBuffer
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * Runs MobileNetV3-Small cascade classifiers to refine detector labels (US only).
 *
 * Three classifiers are supported: speed_limit, warning, regulatory.
 * Input:  [1, 3, 224, 224] float32, ImageNet-normalized RGB
 * Output: [1, num_classes] raw logits → softmax → argmax
 */
class CascadeClassifier(context: Context) {

    companion object {
        private const val TAG = "CascadeClassifier"
        private const val INPUT_SIZE = 224
        private const val CLF_CONF_THRESHOLD = 0.30f
        private const val CROP_PADDING = 0.20f

        // ImageNet normalization constants
        private val MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
        private val STD  = floatArrayOf(0.229f, 0.224f, 0.225f)

        private const val SPEED_LIMIT_MODEL  = "speed_limit_classifier.onnx"
        private const val WARNING_MODEL      = "warning_classifier.onnx"
        private const val REGULATORY_MODEL   = "regulatory_classifier.onnx"
        private const val CLASSIFIER_CONFIG  = "classifier_config.json"
    }

    private val ortEnv: OrtEnvironment = OrtEnvironment.getEnvironment()

    private var speedLimitSession:  OrtSession? = null
    private var warningSession:     OrtSession? = null
    private var regulatorySession:  OrtSession? = null

    private var speedLimitClasses:  List<String> = emptyList()
    private var warningClasses:     List<String> = emptyList()
    private var regulatoryClasses:  List<String> = emptyList()

    val isLoaded: Boolean
        get() = speedLimitSession != null && warningSession != null && regulatorySession != null

    init {
        loadConfig(context)
        speedLimitSession  = loadSession(context, SPEED_LIMIT_MODEL)
        warningSession     = loadSession(context, WARNING_MODEL)
        regulatorySession  = loadSession(context, REGULATORY_MODEL)

        // Log cascade loading results
        if (isLoaded) {
            Log.i(TAG, "All cascade classifiers loaded successfully")
        } else {
            Log.w(TAG, "One or more cascade classifiers failed to load — " +
                    "speed_limit=${speedLimitSession != null}, " +
                    "warning=${warningSession != null}, " +
                    "regulatory=${regulatorySession != null}")
        }
    }

    private fun loadConfig(context: Context) {
        try {
            val json   = context.assets.open(CLASSIFIER_CONFIG).bufferedReader().readText()
            val root   = JSONObject(json)
            val clfs   = root.getJSONObject("classifiers")
            speedLimitClasses  = clfs.getJSONObject("speed_limit").getJSONArray("classes").let { arr ->
                List(arr.length()) { arr.getString(it) }
            }
            warningClasses     = clfs.getJSONObject("warning").getJSONArray("classes").let { arr ->
                List(arr.length()) { arr.getString(it) }
            }
            regulatoryClasses  = clfs.getJSONObject("regulatory").getJSONArray("classes").let { arr ->
                List(arr.length()) { arr.getString(it) }
            }
            Log.i(TAG, "Loaded classifier config: speed_limit=${speedLimitClasses.size}, " +
                    "warning=${warningClasses.size}, regulatory=${regulatoryClasses.size} classes")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load $CLASSIFIER_CONFIG", e)
        }
    }

    private fun loadSession(context: Context, modelFile: String): OrtSession? {
        return try {
            val bytes = context.assets.open(modelFile).readBytes()
            val opts  = OrtSession.SessionOptions().apply { setIntraOpNumThreads(4) }
            val s     = ortEnv.createSession(bytes, opts)
            Log.i(TAG, "Loaded classifier: $modelFile")
            s
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load $modelFile", e)
            null
        }
    }

    /**
     * Determines which classifier (if any) should refine [label].
     * Returns "speed_limit", "warning", "regulatory", or null.
     */
    fun routeToClassifier(label: String): String? = when {
        "maximum-speed-limit" in label                                     -> "speed_limit"
        label.startsWith("warning--")                                      -> "warning"
        label.startsWith("regulatory--") && "maximum-speed-limit" !in label -> "regulatory"
        else                                                               -> null
    }

    /**
     * Refines a detector [label] using the appropriate cascade classifier.
     *
     * @param frame    The full camera frame bitmap (pixel coords)
     * @param box      Bounding box in pixel coords (left, top, right, bottom)
     * @param label    Detector label for this detection
     * @return         Refined label (from classifier) or original [label] as fallback
     */
    fun refine(frame: Bitmap, box: RectF, label: String): String {
        val classifierType = routeToClassifier(label) ?: return label

        val session = when (classifierType) {
            "speed_limit" -> speedLimitSession
            "warning"     -> warningSession
            "regulatory"  -> regulatorySession
            else          -> null
        } ?: return label

        val classes = when (classifierType) {
            "speed_limit" -> speedLimitClasses
            "warning"     -> warningClasses
            "regulatory"  -> regulatoryClasses
            else          -> emptyList()
        }

        if (classes.isEmpty()) return label

        return try {
            val crop   = getPaddedCrop(frame, box)
            val input  = preprocessForClassifier(crop)
            val shape  = longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong())
            val inputName = session.inputNames.iterator().next()
            val tensor = OnnxTensor.createTensor(ortEnv, FloatBuffer.wrap(input), shape)
            val result = session.run(mapOf(inputName to tensor))

            @Suppress("UNCHECKED_CAST")
            val logits = (result[0].value as Array<FloatArray>)[0]
            val probs  = softmax(logits)
            val maxIdx = probs.indices.maxByOrNull { probs[it] } ?: return label
            val maxProb = probs[maxIdx]
            val predicted = classes.getOrElse(maxIdx) { return label }

            if (maxProb >= CLF_CONF_THRESHOLD && predicted != "other") {
                Log.d(TAG, "[$classifierType] refined '$label' → '$predicted' (conf=${"%.2f".format(maxProb)})")
                predicted
            } else {
                Log.d(TAG, "[$classifierType] keeping detector label '$label' " +
                        "(predicted='$predicted', conf=${"%.2f".format(maxProb)})")
                label
            }
        } catch (e: Exception) {
            Log.e(TAG, "Classifier inference failed for $classifierType", e)
            label
        }
    }

    private fun getPaddedCrop(frame: Bitmap, box: RectF): Bitmap {
        val pw     = box.width()  * CROP_PADDING
        val ph     = box.height() * CROP_PADDING
        val left   = max(0f, box.left   - pw)
        val top    = max(0f, box.top    - ph)
        val right  = min(frame.width.toFloat(),  box.right  + pw)
        val bottom = min(frame.height.toFloat(), box.bottom + ph)
        val crop   = Bitmap.createBitmap(frame,
            left.toInt(), top.toInt(),
            (right - left).toInt().coerceAtLeast(1),
            (bottom - top).toInt().coerceAtLeast(1))
        return Bitmap.createScaledBitmap(crop, INPUT_SIZE, INPUT_SIZE, true)
    }

    private fun preprocessForClassifier(bitmap: Bitmap): FloatArray {
        val pixels      = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        val floatArray  = FloatArray(3 * INPUT_SIZE * INPUT_SIZE)
        val channelSize = INPUT_SIZE * INPUT_SIZE
        for (i in pixels.indices) {
            val px = pixels[i]
            floatArray[i]                   = (Color.red(px)   / 255f - MEAN[0]) / STD[0]
            floatArray[channelSize + i]     = (Color.green(px) / 255f - MEAN[1]) / STD[1]
            floatArray[2 * channelSize + i] = (Color.blue(px)  / 255f - MEAN[2]) / STD[2]
        }
        return floatArray
    }

    private fun softmax(logits: FloatArray): FloatArray {
        val maxVal = logits.max()
        val exps   = FloatArray(logits.size) { exp((logits[it] - maxVal).toDouble()).toFloat() }
        val sum    = exps.sum()
        return FloatArray(exps.size) { exps[it] / sum }
    }

    fun close() {
        speedLimitSession?.close()
        warningSession?.close()
        regulatorySession?.close()
        speedLimitSession  = null
        warningSession     = null
        regulatorySession  = null
        Log.i(TAG, "All cascade classifiers unloaded")
    }
}

