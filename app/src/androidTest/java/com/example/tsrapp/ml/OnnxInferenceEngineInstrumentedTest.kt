package com.example.tsrapp.ml

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for [OnnxInferenceEngine].
 *
 * These run on a device/emulator so the real ONNX model and class assets are available.
 *
 * Run with: ./gradlew :app:connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class OnnxInferenceEngineInstrumentedTest {

    private lateinit var engine: OnnxInferenceEngine

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        engine = OnnxInferenceEngine(context, ModelRegion.US)
    }

    @After
    fun tearDown() {
        engine.close()
    }

    // Model Loading

    @Test
    fun modelLoadsSuccessfully() {
        assertTrue(
            "ONNX session should be loaded — check that us_best.onnx is in assets/",
            engine.isModelLoaded
        )
    }

    @Test
    fun classNamesAreLoadedFromAssets() {
        assertTrue(
            "classNames should not be empty — check that us_classes.json is in assets/",
            engine.classNames.isNotEmpty()
        )
    }

    @Test
    fun classCountMatchesExpectedUsModelSize() {
        // us_classes.json has 51 entries (keys 0–50)
        assertEquals(
            "US model should have 51 classes",
            51,
            engine.classNames.size
        )
    }

    @Test
    fun classNamesContainKnownLabels() {
        val names = engine.classNames.toList()
        assertTrue("Should contain stop sign", names.contains("regulatory--stop--g1"))
        assertTrue("Should contain yield sign", names.contains("regulatory--yield--g1"))
        assertTrue("Should contain no-entry sign", names.contains("regulatory--no-entry--g1"))
    }

    @Test
    fun euModelLoadsSuccessfully() {
        val euEngine = OnnxInferenceEngine(context, ModelRegion.EU)
        try {
            assertTrue(
                "EU ONNX session should be loaded — check that eu_best.onnx is in assets/",
                euEngine.isModelLoaded
            )
            assertTrue(
                "EU classNames should not be empty — check that eu_classes.json is in assets/",
                euEngine.classNames.isNotEmpty()
            )
        } finally {
            euEngine.close()
        }
    }

    // ── detect() on blank input — should return nothing ───────────────────────

    @Test
    fun detectOnBlankBitmapReturnsEmptyList() {
        // A solid grey 640x640 bitmap contains no sign features — model should produce nothing
        val blank = createSolidBitmap(640, 640, Color.GRAY)
        val results = engine.detect(blank, confidenceThreshold = 0.5f)
        blank.recycle()

        assertTrue("No detections expected for a blank grey image", results.isEmpty())
    }

    @Test
    fun detectOnSmallBitmapReturnsEmptyList() {
        // Engine should letterbox-scale small images without crashing
        val small = createSolidBitmap(64, 64, Color.WHITE)
        val results = engine.detect(small, confidenceThreshold = 0.5f)
        small.recycle()

        assertTrue("No detections expected for a tiny white image", results.isEmpty())
    }

    @Test
    fun detectOnLargeBitmapDoesNotCrash() {
        // Large bitmaps should be scaled down without error
        val large = createSolidBitmap(1920, 1080, Color.BLACK)
        val results = engine.detect(large, confidenceThreshold = 0.5f)
        large.recycle()

        assertNotNull("Result should not be null for a large bitmap", results)
    }

    // ── detect() result contract ───────────────────────────────────────────────

    @Test
    fun detectResultConfidenceIsWithinValidRange() {
        val bitmap = createSolidBitmap(640, 640, Color.GRAY)
        val results = engine.detect(bitmap, confidenceThreshold = 0.0f)
        bitmap.recycle()

        for (sign in results) {
            assertTrue(
                "Confidence ${sign.confidence} for '${sign.label}' must be >= 0",
                sign.confidence >= 0f
            )
            assertTrue(
                "Confidence ${sign.confidence} for '${sign.label}' must be <= 1",
                sign.confidence <= 1f
            )
        }
    }

    @Test
    fun detectResultBoundingBoxesAreWithinBitmapDimensions() {
        val width = 640
        val height = 480
        val bitmap = createSolidBitmap(width, height, Color.GRAY)
        val results = engine.detect(bitmap, confidenceThreshold = 0.0f)
        bitmap.recycle()

        for (sign in results) {
            val box = sign.boundingBox
            assertTrue("box.left must be >= 0 for '${sign.label}'", box.left >= 0f)
            assertTrue("box.top must be >= 0 for '${sign.label}'", box.top >= 0f)
            assertTrue("box.right must be <= $width for '${sign.label}'", box.right <= width.toFloat())
            assertTrue("box.bottom must be <= $height for '${sign.label}'", box.bottom <= height.toFloat())
            assertTrue("box.right must be > box.left for '${sign.label}'", box.right > box.left)
            assertTrue("box.bottom must be > box.top for '${sign.label}'", box.bottom > box.top)
        }
    }

    @Test
    fun detectResultLabelsAreFromKnownClassNames() {
        val bitmap = createSolidBitmap(640, 640, Color.GRAY)
        val results = engine.detect(bitmap, confidenceThreshold = 0.0f)
        bitmap.recycle()

        val knownNames = engine.classNames.toSet()
        for (sign in results) {
            assertTrue(
                "Label '${sign.label}' should be from classNames",
                knownNames.contains(sign.label) || sign.label.startsWith("Unknown (")
            )
        }
    }

    @Test
    fun higherThresholdProducesFewerOrEqualDetections() {
        val bitmap = createSolidBitmap(640, 640, Color.GRAY)
        val lowThresholdResults  = engine.detect(bitmap, confidenceThreshold = 0.1f)
        val highThresholdResults = engine.detect(bitmap, confidenceThreshold = 0.9f)
        bitmap.recycle()

        assertTrue(
            "Higher threshold should not produce MORE detections than lower threshold",
            highThresholdResults.size <= lowThresholdResults.size
        )
    }

    @Test
    fun detectAtThresholdOneReturnsEmpty() {
        // Nothing can be >= 100% confidence
        val bitmap = createSolidBitmap(640, 640, Color.GRAY)
        val results = engine.detect(bitmap, confidenceThreshold = 1.0f)
        bitmap.recycle()

        assertTrue("Threshold 1.0 should always return empty list", results.isEmpty())
    }

    // ── Session lifecycle ─────────────────────────────────────────────────────

    @Test
    fun closeDoesNotThrow() {
        // Calling close() on a loaded engine should not throw
        val localEngine = OnnxInferenceEngine(context, ModelRegion.US)
        localEngine.close()
        // No assertion needed — the test passes if no exception is thrown
    }

    @Test
    fun detectAfterCloseReturnsEmptyListGracefully() {
        val localEngine = OnnxInferenceEngine(context, ModelRegion.US)
        localEngine.close()

        val bitmap = createSolidBitmap(640, 640, Color.GRAY)
        // Should return emptyList() via the `ortSession ?: return emptyList()` guard,
        // not throw an IllegalStateException
        val results = try {
            localEngine.detect(bitmap, 0.5f)
        } catch (e: Exception) {
            null
        }
        bitmap.recycle()

        assertFalse(
            "detect() after close() should return empty list, not throw",
            results == null
        )
        assertTrue("detect() after close() should return empty list", results!!.isEmpty())
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun createSolidBitmap(width: Int, height: Int, color: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply { this.color = color }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        return bitmap
    }
}

