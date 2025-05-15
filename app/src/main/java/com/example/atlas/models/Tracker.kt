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
    val user1: String? = null,
    val user2: String? = null,
    val user3: String? = null,
    @Serializable(with = InstantSerializer::class)
    val last_connection: Instant? = null,
    val last_latitude: Double? = null,
    val last_longitude: Double? = null,
    val type: String
)

object InstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeString(value.toString())
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun deserialize(decoder: Decoder): Instant {
        return Instant.parse(decoder.decodeString())
    }
}