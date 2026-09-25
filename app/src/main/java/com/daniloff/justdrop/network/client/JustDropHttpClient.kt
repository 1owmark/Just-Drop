package com.daniloff.justdrop.network.client

import android.content.ContentResolver
import com.daniloff.justdrop.model.DeviceInfo
import com.daniloff.justdrop.model.DiscoveredDevice
import com.daniloff.justdrop.model.SelectedFile
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.client.call.body
import io.ktor.client.request.forms.InputProvider
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.streams.asInput
import kotlinx.io.buffered
import java.io.InputStream


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

    suspend fun uploadFile(
        device: DiscoveredDevice,
        file: SelectedFile,
        stream: InputStream
    ) {
        val url = "http://${device.host}:${device.port}/upload"
        client.post(url) {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append(
                            key = "file",
                            value = InputProvider {
                                stream.asInput().buffered()
                            },
                            headers = Headers.build {
                                append(
                                    HttpHeaders.ContentType,
                                    file.mimeType ?: "application/octet-stream"
                                )
                                append(
                                    HttpHeaders.ContentDisposition,
                                    "form-data; name=\"file\"; filename=\"${file.name}\""
                                )
                            }
                        )
                    }
                )
            )
        }
    }
}