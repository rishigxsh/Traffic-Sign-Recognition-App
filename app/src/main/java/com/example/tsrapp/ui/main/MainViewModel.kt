package com.example.tsrapp.ui.main

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.tsrapp.data.model.TrafficSign
import com.example.tsrapp.data.repository.TSRRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {

    // Model loading is expensive (reads a large ONNX from assets and creates an OrtSession).
    // Keep it lazy so we don't block the main thread during ViewModel creation.
    private var repository: TSRRepository? = null
    private val repositoryInitMutex = Mutex()

    private val _detectedSigns = MutableLiveData<List<TrafficSign>>()
    val detectedSigns: LiveData<List<TrafficSign>> = _detectedSigns

    // --- Performance Metrics ---
    private val _inferenceTimeMs = MutableLiveData<Long>()
    val inferenceTimeMs: LiveData<Long> = _inferenceTimeMs

    private val _fps = MutableLiveData<Float>()
    val fps: LiveData<Float> = _fps

    private var lastFrameTime = 0L

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

    fun processFrame(bitmap: Bitmap) {
        val now = System.currentTimeMillis()
        if (lastFrameTime > 0) {
            val delta = now - lastFrameTime
            if (delta > 0) {
                _fps.postValue(1000f / delta)
            }
        }
        lastFrameTime = now

        viewModelScope.launch {
            val repo = getRepository()
            val startTime = System.currentTimeMillis()
            val signs = withContext(Dispatchers.Default) {
                repo.detectSignsInFrame(bitmap)
            }
            val endTime = System.currentTimeMillis()
            
            _inferenceTimeMs.postValue(endTime - startTime)
            _detectedSigns.postValue(signs)
        }
    }

    override fun onCleared() {
        super.onCleared()
        repository?.close()
        repository = null
    }
}
