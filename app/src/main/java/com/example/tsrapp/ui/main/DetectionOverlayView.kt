package com.example.tsrapp.ui.main

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.example.tsrapp.R
import com.example.tsrapp.data.model.TrafficSign
import com.example.tsrapp.util.SettingsManager
import com.example.tsrapp.util.SignLabelToSpeech

class DetectionOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var detections: List<TrafficSign> = emptyList()
    private val rectPool = mutableListOf<RectF>()
    private val labelRectPool = mutableListOf<RectF>()

    private var sourceWidth = 0
    private var sourceHeight = 0

    // Critical sign: softer red (#FF453A) with rounded stroke
    private val criticalPaint = Paint().apply {
        color = resources.getColor(R.color.detection_critical, null)
        style = Paint.Style.STROKE
        strokeWidth = 7f
        isAntiAlias = true
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    // Normal sign: softer green (#30D158) with rounded stroke
    private val normalPaint = Paint().apply {
        color = resources.getColor(R.color.detection_normal, null)
        style = Paint.Style.STROKE
        strokeWidth = 5f
        isAntiAlias = true
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    // Critical label background: semi-transparent dark red
    private val criticalLabelBgPaint = Paint().apply {
        color = resources.getColor(R.color.detection_critical, null)
        style = Paint.Style.FILL
        alpha = 220
        isAntiAlias = true
    }

    // Normal label background: semi-transparent dark (readable on any background)
    private val normalLabelBgPaint = Paint().apply {
        color = 0xCC1A1A2E.toInt()
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val labelTextPaint = Paint().apply {
        color = resources.getColor(R.color.white, null)
        textSize = 38f
        isAntiAlias = true
        isFakeBoldText = true
    }

    fun updateDetections(signs: List<TrafficSign>) {
        val needed = signs.size
        while (rectPool.size < needed) {
            rectPool.add(RectF())
            labelRectPool.add(RectF())
        }
        detections = signs
        invalidate()
    }

    fun setSourceSize(width: Int, height: Int) {
        sourceWidth = width
        sourceHeight = height
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val showBoxes = SettingsManager.isShowBoxes(context)
        val showLabels = SettingsManager.isShowLabels(context)
        val showConfidence = SettingsManager.isShowConfidence(context)
        val threshold = SettingsManager.getConfidenceThreshold(context)

        if (!showBoxes && !showLabels && !showConfidence) return
        if (detections.isEmpty()) return

        val viewW = width.toFloat()
        val viewH = height.toFloat()
        var scale = 1f
        var offsetX = 0f
        var offsetY = 0f

        if (sourceWidth > 0 && sourceHeight > 0) {
            val viewAspect = viewW / viewH
            val srcAspect = sourceWidth.toFloat() / sourceHeight
            // Match PreviewView FILL_CENTER
            if (srcAspect > viewAspect) {
                scale = viewH / sourceHeight
                offsetX = (viewW - sourceWidth * scale) / 2f
            } else {
                scale = viewW / sourceWidth
                offsetY = (viewH - sourceHeight * scale) / 2f
            }
        }

        // Draw bottom-to-top so highest-confidence boxes appear on top
        val sorted = detections.filter { it.confidence >= threshold }
            .sortedBy { it.confidence }

        sorted.forEachIndexed { index, sign ->
            val box = sign.boundingBox
            val rect = rectPool[index]
            rect.set(
                box.left  * scale + offsetX,
                box.top   * scale + offsetY,
                box.right * scale + offsetX,
                box.bottom * scale + offsetY
            )

            val cornerRadius = 10f

            if (showBoxes) {
                val paint = if (sign.isCritical) criticalPaint else normalPaint
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
            }

            val labelText = buildLabelText(sign, showLabels, showConfidence) ?: return@forEachIndexed

            val textW = labelTextPaint.measureText(labelText)
            val fm = labelTextPaint.fontMetrics
            val textH = fm.descent - fm.ascent
            val padH = 6f
            val padV = 4f

            val labelRect = labelRectPool[index]
            labelRect.set(
                rect.left,
                rect.top - textH - padV * 2,
                rect.left + textW + padH * 2,
                rect.top
            )

            // Keep label inside view bounds
            if (labelRect.top < 0) {
                val shift = -labelRect.top
                labelRect.top += shift
                labelRect.bottom += shift
            }

            val bgPaint = if (sign.isCritical) criticalLabelBgPaint else normalLabelBgPaint
            canvas.drawRoundRect(labelRect, 6f, 6f, bgPaint)
            canvas.drawText(
                labelText,
                labelRect.left + padH,
                labelRect.bottom - fm.descent - padV / 2f,
                labelTextPaint
            )
        }
    }

    private fun buildLabelText(
        sign: TrafficSign,
        showLabels: Boolean,
        showConfidence: Boolean
    ): String? {
        val name = SignLabelToSpeech.toDisplayName(sign.label)
        val conf = "${(sign.confidence * 100).toInt()}%"
        return when {
            showLabels && showConfidence -> "$name  $conf"
            showLabels -> name
            showConfidence -> conf
            else -> null
        }
    }
}
