package com.example.tsrapp.ml

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for [CascadeClassifier].
 *
 * Tests cover:
 *  - Loading and unloading all three classifier sessions
 *  - Routing logic: which labels go to which classifier (or none)
 *  - refine() returns the original label for non-routed signs (bypasses classifiers)
 *  - refine() runs without crashing for routed signs
 *  - refine() always returns a non-null, non-empty string
 *  - Session lifecycle: isLoaded state and graceful close
 *
 * Run with: ./gradlew :app:connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class CascadeClassifierInstrumentedTest {

    private lateinit var classifier: CascadeClassifier
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    // ── Routing label fixtures ────────────────────────────────────────────────

    /** Labels that should route to the speed_limit classifier. */
    private val speedLimitLabels = listOf(
        "regulatory--maximum-speed-limit-25--g2",
        "regulatory--maximum-speed-limit-35--g2",
        "regulatory--maximum-speed-limit-55--g2",
        "regulatory--maximum-speed-limit-65--g2"
    )

    /** Labels that should route to the warning classifier. */
    private val warningLabels = listOf(
        "warning--pedestrians-crossing--g4",
        "warning--curve-left--g2",
        "warning--curve-right--g2",
        "warning--stop-ahead--g9",
        "warning--school-zone--g2",
        "warning--railroad-crossing--g1",
        "warning--children--g2"
    )

    /** Labels that should route to the regulatory classifier. */
    private val regulatoryLabels = listOf(
        "regulatory--stop--g1",
        "regulatory--yield--g1",
        "regulatory--no-entry--g1",
        "regulatory--no-parking--g2",
        "regulatory--no-left-turn--g1",
        "regulatory--no-right-turn--g1",
        "regulatory--no-u-turn--g1",
        "regulatory--wrong-way--g1"
    )

    /**
     * Labels that should NOT route to any classifier — the detector label must be
     * returned unchanged.
     */
    private val nonRoutedLabels = listOf(
        "complementary--chevron-left--g1",
        "complementary--chevron-right--g3",
        "complementary--distance--g1",
        "information--highway-exit--g1",
        "other-sign",
        "",                         // empty string edge case
        "completely-unknown-label"
    )

    @Before
    fun setUp() {
        classifier = CascadeClassifier(context)
    }

    @After
    fun tearDown() {
        classifier.close()
    }

    // ── Loading ───────────────────────────────────────────────────────────────

    @Test
    fun allClassifiersLoadSuccessfully() {
        assertTrue(
            "CascadeClassifier.isLoaded should be true when all three models are present",
            classifier.isLoaded
        )
    }

    // ── Unloading / lifecycle ─────────────────────────────────────────────────

    @Test
    fun isLoadedIsFalseAfterClose() {
        val local = CascadeClassifier(context)
        assertTrue("Should be loaded before close()", local.isLoaded)
        local.close()
        assertFalse("isLoaded should be false after close()", local.isLoaded)
    }

    @Test
    fun closeDoesNotThrow() {
        val local = CascadeClassifier(context)
        local.close() // Should not throw
    }

    @Test
    fun doubleCloseDoesNotThrow() {
        val local = CascadeClassifier(context)
        local.close()
        local.close() // Second close on already-null sessions must not throw
    }

    // ── Routing logic ─────────────────────────────────────────────────────────

    @Test
    fun speedLimitLabelsRouteToSpeedLimitClassifier() {
        for (label in speedLimitLabels) {
            assertEquals(
                "Expected 'speed_limit' route for label: $label",
                "speed_limit",
                classifier.routeToClassifier(label)
            )
        }
    }

    @Test
    fun warningLabelsRouteToWarningClassifier() {
        for (label in warningLabels) {
            assertEquals(
                "Expected 'warning' route for label: $label",
                "warning",
                classifier.routeToClassifier(label)
            )
        }
    }

    @Test
    fun regulatoryLabelsRouteToRegulatoryClassifier() {
        for (label in regulatoryLabels) {
            assertEquals(
                "Expected 'regulatory' route for label: $label",
                "regulatory",
                classifier.routeToClassifier(label)
            )
        }
    }

    @Test
    fun nonRoutedLabelsReturnNullFromRouter() {
        for (label in nonRoutedLabels) {
            assertNull(
                "Expected null route (no classifier) for label: '$label'",
                classifier.routeToClassifier(label)
            )
        }
    }

    @Test
    fun speedLimitLabelsDoNotRouteToWarningOrRegulatory() {
        for (label in speedLimitLabels) {
            val route = classifier.routeToClassifier(label)
            assertFalse(
                "Speed limit label '$label' must not route to warning",
                route == "warning"
            )
            assertFalse(
                "Speed limit label '$label' must not route to regulatory",
                route == "regulatory"
            )
        }
    }

    @Test
    fun regulatorySpeedLimitIsNotDoubleRoutedAsRegulatory() {
        // A speed-limit regulatory label must go to speed_limit, NOT regulatory
        val label = "regulatory--maximum-speed-limit-35--g2"
        assertEquals(
            "Speed-limit regulatory label must route to speed_limit, not regulatory",
            "speed_limit",
            classifier.routeToClassifier(label)
        )
    }

    // ── refine() passthrough for non-routed labels ────────────────────────────

    @Test
    fun refineReturnsOriginalLabelForNonRoutedSigns() {
        val frame = createSolidBitmap(640, 480, Color.GRAY)
        val box   = RectF(100f, 100f, 200f, 200f)

        for (label in nonRoutedLabels) {
            val result = classifier.refine(frame, box, label)
            assertEquals(
                "refine() must return the original label unchanged for non-routed sign: '$label'",
                label,
                result
            )
        }
        frame.recycle()
    }

    // ── refine() for routed labels ────────────────────────────────────────────

    @Test
    fun refineDoesNotCrashForSpeedLimitLabels() {
        val frame = createSolidBitmap(640, 480, Color.GRAY)
        val box   = RectF(50f, 50f, 300f, 300f)

        for (label in speedLimitLabels) {
            val result = classifier.refine(frame, box, label)
            assertNotNull("refine() must not return null for speed limit label: $label", result)
            assertTrue("refine() must not return empty string for: $label", result.isNotEmpty())
        }
        frame.recycle()
    }

    @Test
    fun refineDoesNotCrashForWarningLabels() {
        val frame = createSolidBitmap(640, 480, Color.GRAY)
        val box   = RectF(50f, 50f, 300f, 300f)

        for (label in warningLabels) {
            val result = classifier.refine(frame, box, label)
            assertNotNull("refine() must not return null for warning label: $label", result)
            assertTrue("refine() must not return empty string for: $label", result.isNotEmpty())
        }
        frame.recycle()
    }

    @Test
    fun refineDoesNotCrashForRegulatoryLabels() {
        val frame = createSolidBitmap(640, 480, Color.GRAY)
        val box   = RectF(50f, 50f, 300f, 300f)

        for (label in regulatoryLabels) {
            val result = classifier.refine(frame, box, label)
            assertNotNull("refine() must not return null for regulatory label: $label", result)
            assertTrue("refine() must not return empty string for: $label", result.isNotEmpty())
        }
        frame.recycle()
    }

    @Test
    fun refineAlwaysReturnsAKnownClassNameOrOriginalLabel() {
        // For any routed label, the result must be either the original label (fallback)
        // or a label that exists in classifier_config.json classes.
        // Since we don't expose class lists directly, we assert it is a non-empty string
        // that starts with a known Mapillary prefix or is the original.
        val frame  = createSolidBitmap(640, 480, Color.GRAY)
        val box    = RectF(50f, 50f, 300f, 300f)
        val allRouted = speedLimitLabels + warningLabels + regulatoryLabels

        for (label in allRouted) {
            val result = classifier.refine(frame, box, label)
            val isOriginal    = result == label
            val isMapillary   = result.startsWith("regulatory--") ||
                                result.startsWith("warning--") ||
                                result.startsWith("complementary--") ||
                                result.startsWith("information--")
            assertTrue(
                "refine() result '$result' for '$label' must be the original label " +
                        "or a known Mapillary-format label",
                isOriginal || isMapillary
            )
        }
        frame.recycle()
    }

    @Test
    fun refineWithBoxAtFrameEdgeDoesNotCrash() {
        // Boxes near the edge test the padding-clamp logic in getPaddedCrop()
        val frame = createSolidBitmap(640, 480, Color.GRAY)
        val edgeBoxes = listOf(
            RectF(0f, 0f, 10f, 10f),           // top-left corner
            RectF(630f, 470f, 640f, 480f),      // bottom-right corner
            RectF(0f, 235f, 10f, 245f),         // left edge
            RectF(630f, 235f, 640f, 245f)       // right edge
        )

        for (box in edgeBoxes) {
            val result = classifier.refine(frame, box, "warning--curve-left--g2")
            assertNotNull("refine() must not return null for edge box $box", result)
        }
        frame.recycle()
    }

    @Test
    fun refineAfterCloseReturnsOriginalLabelGracefully() {
        // After close(), sessions are null — refine() must fall back to the original label
        // rather than throwing.
        val local = CascadeClassifier(context)
        local.close()

        val frame  = createSolidBitmap(640, 480, Color.GRAY)
        val box    = RectF(100f, 100f, 300f, 300f)
        val label  = "warning--curve-left--g2"

        val result = try {
            local.refine(frame, box, label)
        } catch (e: Exception) {
            null
        }
        frame.recycle()

        assertFalse(
            "refine() after close() must not throw an exception",
            result == null
        )
        assertEquals(
            "refine() after close() must return the original label as fallback",
            label,
            result
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun createSolidBitmap(width: Int, height: Int, color: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint  = Paint().apply { this.color = color }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        return bitmap
    }
}

