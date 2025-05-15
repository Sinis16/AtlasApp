package com.example.atlas.models

import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.serialization.Serializable

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant


@Serializable
data class Tracker(
    val id: String? = null,
    val ble_id: String,
    val name: String,
    val user1: String,
    val user2: String? = null,
    val user3: String? = null,
    val last_connection: String? = null,
    val last_latitude: Double? = null,
    val last_longitude: Double? = null,
    val type: String
)
