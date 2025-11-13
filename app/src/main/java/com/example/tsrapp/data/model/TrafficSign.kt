package com.example.tsrapp.data.model

data class TrafficSign(
    val label: String,
    val confidence: Float,
    val boundingBox: BoundingBox,
    val isCritical: Boolean = false
) {
    data class BoundingBox(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    )
}

