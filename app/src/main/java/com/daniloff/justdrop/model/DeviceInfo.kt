package com.daniloff.justdrop.model

import kotlinx.serialization.Serializable

@Serializable
data class DeviceInfo(
    val deviceId: String,
    val manufacturer: String,
    val model: String
)
