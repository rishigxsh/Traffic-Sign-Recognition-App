package com.example.tsrapp.ui.main

import android.graphics.Bitmap
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tsrapp.data.model.TrafficSign
import com.example.tsrapp.data.repository.TSRRepository
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {
    private val repository = TSRRepository()
    
    private val _detectedSigns = MutableLiveData<List<TrafficSign>>()
    val detectedSigns: LiveData<List<TrafficSign>> = _detectedSigns
    
    fun processFrame(bitmap: Bitmap) {
        viewModelScope.launch {
            val signs = repository.detectSignsInFrame(bitmap)
            _detectedSigns.postValue(signs)
        }
    }
}

