package com.example.tsrapp.ml

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.example.tsrapp.data.model.TrafficSign
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

class VideoFileInference(
    private val engine: OnnxInferenceEngine,
    private val confidenceThreshold: Float = 0.5f
) {
    suspend fun processVideo(
        context: Context,
        uri: Uri,
        frameIntervalMs: Long = 1000L,
        onFrameResult: suspend (timestampMs: Long, frame: Bitmap, results: List<TrafficSign>) -> Unit
    ) = withContext(Dispatchers.Default) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)

            val durationMs = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: return@withContext

            var currentMs = 0L
            while (currentMs <= durationMs && isActive) { // isActive stops the loop when cancelled
                val bitmap = retriever.getFrameAtTime(
                    currentMs * 100L,
                    MediaMetadataRetriever.OPTION_CLOSEST
                )
                if (bitmap != null) {
                    if (isActive) { // check again right before detect()
                        try {
                            val results = engine.detect(bitmap, confidenceThreshold)
                            withContext(Dispatchers.Main) {   // Run UI updates on Main thread
                                onFrameResult(currentMs, bitmap, results)
                            }

                        } catch (_: IllegalStateException) {
                            // OrtSession was closed mid-inference (activity destroyed)
                            bitmap.recycle()
                            return@withContext
                        }
                    }
                    bitmap.recycle()
                }
                currentMs += frameIntervalMs
            }
        } finally {
            retriever.release()
        }
    }
}