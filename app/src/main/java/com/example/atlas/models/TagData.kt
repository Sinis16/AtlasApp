package com.example.atlas.models

data class TagData(
    val id: String,
    val distance: Double, // In cm
    val angle: Double,    // In degrees
    val battery: Int      // In percentage
)