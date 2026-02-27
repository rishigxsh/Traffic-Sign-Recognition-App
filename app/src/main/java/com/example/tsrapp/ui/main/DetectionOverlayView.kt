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

class DetectionOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    private var detections: List<TrafficSign> = emptyList()
    private val rectPool = mutableListOf<RectF>()
    private val textRectPool = mutableListOf<RectF>()
    
    private val criticalPaint = Paint().apply {
        color = resources.getColor(R.color.red, null)
        style = Paint.Style.STROKE
        strokeWidth = 8f
        isAntiAlias = true
    }
    
    private val normalPaint = Paint().apply {
        color = resources.getColor(R.color.green, null)
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
    }
    
    private val textPaint = Paint().apply {
        color = resources.getColor(R.color.white, null)
        textSize = 48f
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    
    private val textBackgroundPaint = Paint().apply {
        color = resources.getColor(R.color.black, null)
        style = Paint.Style.FILL
        alpha = 180
    }
    
    
    fun updateDetections(signs: List<TrafficSign>) {
        // Pre-allocate tracking objects
        if (signs.size > rectPool.size) {
            val needed = signs.size - rectPool.size
            for (i in 0 until needed) {
                rectPool.add(RectF())
                textRectPool.add(RectF())
            }
        }
        detections = signs
        invalidate()
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val showBoxes = SettingsManager.isShowBoxes(context)
        val showLabels = SettingsManager.isShowLabels(context)
        val showConfidence = SettingsManager.isShowConfidence(context)
        val threshold = SettingsManager.getConfidenceThreshold(context)

        if (!showBoxes && !showLabels && !showConfidence) {
            return
        }

        detections.forEachIndexed { index, sign ->
            if (sign.confidence < threshold) {
                return@forEachIndexed
            }
            val box = sign.boundingBox
            val rect = rectPool[index]
            rect.set(box.left, box.top, box.right, box.bottom)

            // Draw bounding box
            if (showBoxes) {
                val paint = if (sign.isCritical) criticalPaint else normalPaint
                canvas.drawRect(rect, paint)
            }

            // Build label text
            val labelText = when {
                showLabels && showConfidence -> "${sign.label} (${(sign.confidence * 100).toInt()}%)"
                showLabels -> sign.label
                showConfidence -> "${(sign.confidence * 100).toInt()}%"
                else -> null
            }

            if (labelText == null) {
                return@forEachIndexed
            }

            val label = labelText
            val textWidth = textPaint.measureText(label)
            val textHeight = textPaint.fontMetrics.let { it.descent - it.ascent }
            
            // Draw text background
            val textRect = textRectPool[index]
            textRect.set(
                box.left,
                box.top - textHeight - 10,
                box.left + textWidth + 20,
                box.top
            )
            canvas.drawRect(textRect, textBackgroundPaint)
            
            // Draw text
            canvas.drawText(
                label,
                box.left + 10,
                box.top - 10,
                textPaint
            )
        }
    }
}

