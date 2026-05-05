package com.example.myapplication

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class PipelineViewModel: ViewModel() {
    val restartPipeline = MutableLiveData<Boolean>()
}