package com.daniloff.justdrop.network.client

import android.util.Log
import com.daniloff.justdrop.model.DeviceInfo
import com.daniloff.justdrop.model.DiscoveredDevice
import com.daniloff.justdrop.model.SelectedFile
import com.daniloff.justdrop.model.TransferRequest
import com.daniloff.justdrop.model.TransferResponse
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.client.call.body
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.plugins.onUpload
import io.ktor.client.request.forms.InputProvider
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
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
        Log.d("HTTP_CLIENT", "GET /device -> $url")

        return client.get(url).body()
    }

    suspend fun sendTransferRequest(
        device: DiscoveredDevice,
        request: TransferRequest
    ): TransferResponse {
        val url = "http://${device.host}:${device.port}/transfer/request"

        Log.d("HTTP_CLIENT", "POST /transfer/request -> $url")
        return client.post(url) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun uploadFile(
        device: DiscoveredDevice,
        file: SelectedFile,
        stream: InputStream,
        onProgress: (Long, Long?) -> Unit
    ) {
        val url = "http://${device.host}:${device.port}/upload"
        Log.d("HTTP_CLIENT", "POST /upload -> $url")
        Log.d("UPLOAD", "uploadFile started")
        client.post(url) {
            expectSuccess = true

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

            onUpload { bytesSentTotal, contentLength ->
                onProgress(bytesSentTotal, contentLength)
            }
        }
    }
}