package com.example.tsrapp.ml

/**
 * Represents a supported traffic sign detection region.
 *
 * Each entry maps directly to a pair of asset files that must exist in assets/:
 *   - [modelFile]   : the exported YOLOv8 ONNX model
 *   - [classesFile] : the corresponding JSON class map {"0": "label", ...}
 */
enum class ModelRegion(
    val modelFile: String,
    val classesFile: String,
    val displayName: String
) {
    US(
        modelFile    = "us_best.onnx",
        classesFile  = "us_classes.json",
        displayName  = "US"
    ),
    EU(
        modelFile    = "eu_best.onnx",
        classesFile  = "eu_classes.json",
        displayName  = "EU"
    );

    companion object {
        /** Returns the [ModelRegion] matching [name] (case-insensitive), or [US] if not found. */
        fun fromString(name: String): ModelRegion =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: US
    }
}

