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
    private val textRectPool = mutableListOf<RectF>()

    private var sourceWidth: Int = 0
    private var sourceHeight: Int = 0
    
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

    /**
     * Set the size of the source image/frame to scale detections correctly.
     */
    fun setSourceSize(width: Int, height: Int) {
        this.sourceWidth = width
        this.sourceHeight = height
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

        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()

        var scale = 1f
        var offsetX = 0f
        var offsetY = 0f

        if (sourceWidth > 0 && sourceHeight > 0) {
            val viewAspectRatio = viewWidth / viewHeight
            val imageAspectRatio = sourceWidth.toFloat() / sourceHeight

            if (imageAspectRatio > viewAspectRatio) {
                // Image is relatively wider than the view area
                scale = viewWidth / sourceWidth
                offsetY = (viewHeight - sourceHeight * scale) / 2f
            } else {
                // Image is relatively taller than the view area (or same)
                scale = viewHeight / sourceHeight
                offsetX = (viewWidth - sourceWidth * scale) / 2f
            }
        }

        detections.forEachIndexed { index, sign ->
            if (sign.confidence < threshold) {
                return@forEachIndexed
            }
            val box = sign.boundingBox
            val rect = rectPool[index]
            rect.set(
                box.left * scale + offsetX,
                box.top * scale + offsetY,
                box.right * scale + offsetX,
                box.bottom * scale + offsetY
            )

            // Draw bounding box
            if (showBoxes) {
                val paint = if (sign.isCritical) criticalPaint else normalPaint
                canvas.drawRect(rect, paint)
            }

            val displayName = SignLabelToSpeech.toDisplayName(sign.label)
            // Build label text
            val labelText = when {
                showLabels && showConfidence -> "$displayName (${(sign.confidence * 100).toInt()}%)"
                showLabels -> displayName
                showConfidence -> "${(sign.confidence * 100).toInt()}%"
                else -> null
            }

            if (labelText == null) {
                return@forEachIndexed
            }

            val textWidth = textPaint.measureText(labelText)
            val textHeight = textPaint.fontMetrics.let { it.descent - it.ascent }

            // Draw text background (use scaled rect so labels align with boxes)
            val textRect = textRectPool[index]
            textRect.set(
                rect.left,
                rect.top - textHeight - 10,
                rect.left + textWidth + 20,
                rect.top,
            )
            canvas.drawRect(textRect, textBackgroundPaint)

            canvas.drawText(
                labelText,
                rect.left + 10,
                rect.top - 10,
                textPaint,
            )
        }
    }
}

