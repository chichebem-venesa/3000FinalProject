package com.example.myapplication

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
@Parcelize
data class Prediction(
    val confidence: Double,
    val accuracy: Double,
    val label: String,

    //val reason: String
) : Parcelable
