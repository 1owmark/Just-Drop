package com.daniloff.justdrop.model

import kotlinx.serialization.Serializable

@Serializable
data class DeviceInfo(val manufacturer: String, val model: String)
