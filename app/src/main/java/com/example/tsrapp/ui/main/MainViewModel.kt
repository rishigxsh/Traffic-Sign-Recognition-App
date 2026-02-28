package com.example.tsrapp.ui.main

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.tsrapp.data.model.TrafficSign
import com.example.tsrapp.data.repository.TSRRepository
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TSRRepository(application)

    private val _detectedSigns = MutableLiveData<List<TrafficSign>>()
    val detectedSigns: LiveData<List<TrafficSign>> = _detectedSigns

    fun processFrame(bitmap: Bitmap) {
        viewModelScope.launch {
            val signs = repository.detectSignsInFrame(bitmap)
            _detectedSigns.postValue(signs)
        }
    }

    override fun onCleared() {
        super.onCleared()
        repository.close()
    }
}
