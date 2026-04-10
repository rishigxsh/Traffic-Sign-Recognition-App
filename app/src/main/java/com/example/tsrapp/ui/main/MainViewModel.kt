package com.example.tsrapp.ui.main

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.tsrapp.data.model.TrafficSign
import com.example.tsrapp.data.repository.TSRRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TSRRepository(application)

    private val _detectedSigns = MutableLiveData<List<TrafficSign>>()
    val detectedSigns: LiveData<List<TrafficSign>> = _detectedSigns

    // Track the latest inference job so it can be canceled before closing the engine
    private var inferenceJob: Job? = null

    fun processFrame(bitmap: Bitmap) {
        // Drop the frame if a previous inference is still running - no queue build-up
        if (inferenceJob?.isActive == true) return

        inferenceJob = viewModelScope.launch {
            val signs = repository.detectSignsInFrame(bitmap)
            _detectedSigns.postValue(signs)
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Cancel any in-flight inference BEFORE closing the engine
        inferenceJob?.cancel()
        repository.close()
    }
}
