package com.example.tsrapp.util

import java.util.Locale

/**
 * Converts dataset-style class names (e.g. regulatory--stop--g1) into short spoken phrases
 * for text-to-speech while driving.
 */
object SignLabelToSpeech {

    private val categorySpoken = mapOf(
        "regulatory" to "Regulatory",
        "warning" to "Warning",
        "complementary" to "Complementary",
        "information" to "Information",
        "guide" to "Guide",
        "priority" to "Priority",
    )

    /**
     * Short, human-readable label for on-screen UI (not full TTS phrasing).
     * Example: [regulatory--stop--g1] → "Stop sign"
     */
    fun toDisplayName(raw: String): String {
        if (raw.isBlank()) return "Unknown sign"
        val collapsed = raw.trim().lowercase(Locale.US)
            .replace(Regex("--g\\d+$"), "")
            .trim()
        val segments = collapsed.split("--").map { it.trim() }.filter { it.isNotEmpty() }
        if (segments.isEmpty()) return humanizeToken(raw)

        val detailSlug = segments.drop(1).joinToString("-")
        if (detailSlug.isEmpty()) {
            return categorySpoken[segments[0]]?.let { "$it sign" } ?: humanizeToken(segments[0])
        }

        Regex("maximum-speed-limit-(\\d+)").find(detailSlug)?.let {
            return "Speed limit"
        }
        Regex("minimum-speed-limit-(\\d+)").find(detailSlug)?.let {
            return "Minimum speed limit"
        }

        return when {
            "railroad" in detailSlug -> "Railroad crossing"
            "school-zone" in detailSlug -> "School zone"
            "pedestrians-crossing" in detailSlug -> "Pedestrian crossing"
            "children" in detailSlug && segments[0] == "warning" -> "Children crossing"
            "stop" in detailSlug -> "Stop sign"
            "yield" in detailSlug -> "Yield"
            "no-entry" in detailSlug || "do-not-enter" in detailSlug -> "No entry"
            "wrong-way" in detailSlug -> "Wrong way"
            "curve-left" in detailSlug -> "Curve left"
            "curve-right" in detailSlug -> "Curve right"
            else -> humanizeDetail(detailSlug)
        }
    }

    fun toSpokenPhrase(raw: String): String {
        if (raw.isBlank()) return "Unknown traffic sign"
        val collapsed = raw.trim().lowercase(Locale.US)
            .replace(Regex("--g\\d+$"), "")
            .trim()
        val segments = collapsed.split("--").map { it.trim() }.filter { it.isNotEmpty() }
        if (segments.isEmpty()) return humanizeToken(raw)

        val detailSlug = segments.drop(1).joinToString("-")
        
        Regex("maximum-speed-limit-(\\d+)").find(detailSlug)?.let {
            return "Speed limit"
        }
        Regex("minimum-speed-limit-(\\d+)").find(detailSlug)?.let {
            return "Minimum speed limit"
        }

        // Return direct natural sign names
        return when {
            "railroad" in detailSlug -> "Railroad crossing"
            "school-zone" in detailSlug -> "School zone"
            "pedestrians-crossing" in detailSlug -> "Pedestrian crossing"
            "children" in detailSlug && segments[0] == "warning" -> "Children crossing"
            "stop" in detailSlug -> "Stop"
            "yield" in detailSlug -> "Yield"
            "no-entry" in detailSlug || "do-not-enter" in detailSlug -> "No entry"
            "wrong-way" in detailSlug -> "Wrong way"
            "curve-left" in detailSlug -> "Curve left"
            "curve-right" in detailSlug -> "Curve right"
            "keep-right" in detailSlug -> "Keep right"
            "keep-left" in detailSlug -> "Keep left"
            detailSlug.isNotEmpty() -> humanizeDetail(detailSlug)
            else -> humanizeToken(segments[0])
        }
    }

    private fun humanizeDetail(s: String): String =
        s.replace('-', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }

    private fun humanizeToken(raw: String): String =
        raw.replace('-', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }

    /** Heuristic: signs that warrant extra attention on the road. */
    fun isCriticalRoadAlert(label: String): Boolean {
        val l = label.lowercase(Locale.US)
        return l.contains("stop") ||
            l.contains("yield") ||
            l.contains("wrong-way") ||
            l.contains("no-entry") ||
            l.contains("railroad") ||
            l.contains("do-not-enter")
    }

    /** Mapping categories or specific signs to short symbols/emojis for the UI. */
    fun getSymbol(label: String): String {
        val l = label.lowercase(Locale.US)
        if (l.contains("stop")) return "🛑"
        if (l.contains("yield")) return "⚠️"
        if (l.contains("wrong-way")) return "⛔"
        if (l.contains("no-entry") || l.contains("do-not-enter")) return "🚫"
        if (l.contains("speed-limit-")) {
            val limit = Regex("speed-limit-(\\d+)").find(l)?.groupValues?.get(1) ?: ""
            return limit
        }
        if (l.startsWith("warning")) return "⚠️"
        if (l.startsWith("regulatory")) return "📋"
        if (l.startsWith("information") || l.startsWith("guide")) return "ℹ️"
        return "⚪"
    }
}
