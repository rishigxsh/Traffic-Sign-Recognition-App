package com.example.tsrapp.ui.main

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.example.tsrapp.R
import com.example.tsrapp.data.model.TrafficSign

class DetectionOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    private var detections: List<TrafficSign> = emptyList()
    
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
        detections = signs
        invalidate()
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        detections.forEach { sign ->
            val box = sign.boundingBox
            val rect = RectF(box.left, box.top, box.right, box.bottom)
            
            // Draw bounding box
            val paint = if (sign.isCritical) criticalPaint else normalPaint
            canvas.drawRect(rect, paint)
            
            // Draw label with confidence
            val label = "${sign.label} (${(sign.confidence * 100).toInt()}%)"
            val textWidth = textPaint.measureText(label)
            val textHeight = textPaint.fontMetrics.let { it.descent - it.ascent }
            
            // Draw text background
            val textRect = RectF(
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

