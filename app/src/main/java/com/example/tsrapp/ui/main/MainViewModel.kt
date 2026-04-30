package com.example.tsrapp.ui.main

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.tsrapp.data.model.TrafficSign
import com.example.tsrapp.data.repository.TSRRepository
import com.example.tsrapp.util.BoundingBoxSmoother
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

private const val DETECTION_PERSIST_MS = 1500L
private const val STABLE_FRAMES_REQUIRED = 2

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private var repository: TSRRepository? = null
    private val repositoryInitMutex = Mutex()

    private val _detectedSigns = MutableLiveData<List<TrafficSign>>()
    val detectedSigns: LiveData<List<TrafficSign>> = _detectedSigns

    private val _modelError = MutableLiveData<String?>()
    val modelError: LiveData<String?> = _modelError
    private var modelErrorEmitted = false

    private val _inferenceTimeMs = MutableLiveData<Long>()
    val inferenceTimeMs: LiveData<Long> = _inferenceTimeMs

    private val _fps = MutableLiveData<Float>()
    val fps: LiveData<Float> = _fps

    private var lastFrameTime = 0L
    private val isProcessingFrame = AtomicBoolean(false)

    private val labelSeenCount = mutableMapOf<String, Int>()
    private val labelLastSeenAt = mutableMapOf<String, Long>()
    private var lastRawDetections = listOf<TrafficSign>()

    private val smoother = BoundingBoxSmoother()

    private suspend fun getRepository(): TSRRepository {
        repository?.let { return it }
        return repositoryInitMutex.withLock {
            repository ?: run {
                val repo = withContext(Dispatchers.Default) { TSRRepository(getApplication()) }
                repository = repo
                repo
            }
        }
    }

    fun clearSmoothing() {
        smoother.clear()
        labelSeenCount.clear()
        labelLastSeenAt.clear()
        _detectedSigns.postValue(emptyList())
    }

    fun processFrame(bitmap: Bitmap) {
        val now = System.currentTimeMillis()
        if (lastFrameTime > 0) {
            val delta = now - lastFrameTime
            if (delta > 0) _fps.postValue(1000f / delta)
        }
        lastFrameTime = now

        if (!isProcessingFrame.compareAndSet(false, true)) {
            if (!bitmap.isRecycled) bitmap.recycle()
            return
        }

        viewModelScope.launch {
            try {
                val repo = getRepository()
                if (!modelErrorEmitted) {
                    modelErrorEmitted = true
                    if (!repo.isModelLoaded) {
                        _modelError.postValue("Model failed to load. Detection is disabled.")
                    }
                }
                val startTime = System.currentTimeMillis()
                val signs = withContext(Dispatchers.Default) {
                    repo.detectSignsInFrame(bitmap)
                }
                _inferenceTimeMs.postValue(System.currentTimeMillis() - startTime)
                _detectedSigns.postValue(smoother.smooth(stabilize(signs)))
            } finally {
                if (!bitmap.isRecycled) bitmap.recycle()
                isProcessingFrame.set(false)
            }
        }
    }

    private fun stabilize(fresh: List<TrafficSign>): List<TrafficSign> {
        val now = System.currentTimeMillis()
        val freshLabels = fresh.map { it.label }.toSet()

        val allTracked = (labelSeenCount.keys + freshLabels).toSet()
        for (label in allTracked) {
            labelSeenCount[label] = if (label in freshLabels) (labelSeenCount[label] ?: 0) + 1 else 0
        }
        for (sign in fresh) {
            if ((labelSeenCount[sign.label] ?: 0) >= STABLE_FRAMES_REQUIRED) {
                labelLastSeenAt[sign.label] = now
            }
        }

        val stableLabels = labelLastSeenAt.filter { (_, t) -> now - t < DETECTION_PERSIST_MS }.keys
        lastRawDetections = fresh
        return fresh.filter { it.label in stableLabels } +
            lastRawDetections.filter { it.label in stableLabels && it.label !in freshLabels }
    }

    override fun onCleared() {
        super.onCleared()
        repository?.close()
        repository = null
    }
}
