package com.example.tsrapp.util

import com.example.tsrapp.data.model.TrafficSign

/**
 * Exponential moving average applied to bounding box coordinates so boxes
 * glide smoothly instead of jumping frame-to-frame when the model outputs
 * slightly different positions for the same sign.
 *
 * alpha=0.35 balances responsiveness vs. smoothness. Lower = smoother but
 * slower to track a moving sign; higher = snappier but jitterier.
 */
class BoundingBoxSmoother(private val alpha: Float = 0.35f) {

    private data class SmoothBox(var l: Float, var t: Float, var r: Float, var b: Float)

    private val tracked = mutableMapOf<String, SmoothBox>()

    fun smooth(signs: List<TrafficSign>): List<TrafficSign> {
        tracked.keys.retainAll(signs.map { it.label }.toSet())
        return signs.map { sign ->
            val box = sign.boundingBox
            val prev = tracked[sign.label]
            if (prev == null) {
                tracked[sign.label] = SmoothBox(box.left, box.top, box.right, box.bottom)
                sign
            } else {
                prev.l = lerp(prev.l, box.left, alpha)
                prev.t = lerp(prev.t, box.top, alpha)
                prev.r = lerp(prev.r, box.right, alpha)
                prev.b = lerp(prev.b, box.bottom, alpha)
                sign.copy(
                    boundingBox = TrafficSign.BoundingBox(prev.l, prev.t, prev.r, prev.b)
                )
            }
        }
    }

    fun clear() = tracked.clear()

    private fun lerp(from: Float, to: Float, a: Float) = from + (to - from) * a
}
