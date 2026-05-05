package com.example.myapplication

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PipelineState {
    Idle, Countdown, Recording, Processing, Result
}

object PipelineController{

    private val _state = MutableStateFlow(PipelineState.Idle)
    private val _prediction=MutableStateFlow<Prediction?>(null)

    val state = _state.asStateFlow()

    val prediction = _prediction.asStateFlow()
    fun startPipeline() {
        if (_state.value != PipelineState.Idle) return
        _state.value = PipelineState.Countdown
    }

    fun setRecording() { _state.value = PipelineState.Recording }
    fun setProcessing() { _state.value = PipelineState.Processing }
    fun setResult() { _state.value = PipelineState.Result }


    fun reset() {
        _state.value = PipelineState.Idle
        _prediction.value = null
    }
    fun setPrediction(latestPrediction: Prediction){
        _prediction.value =latestPrediction
    }




}