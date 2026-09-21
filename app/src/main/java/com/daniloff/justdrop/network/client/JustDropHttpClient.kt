package com.daniloff.justdrop.network.client

import com.daniloff.justdrop.model.DeviceInfo
import com.daniloff.justdrop.model.DiscoveredDevice
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.client.call.body
import io.ktor.client.request.get


class JustDropHttpClient {
    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json()
        }
    }

    suspend fun getDevice(device: DiscoveredDevice): DeviceInfo {
        val url = "http://${device.host}:${device.port}/device"

        return client.get(url).body()
    }
}