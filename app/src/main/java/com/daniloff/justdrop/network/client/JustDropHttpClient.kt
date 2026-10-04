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
import io.ktor.client.plugins.HttpTimeout
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.io.buffered
import java.io.IOException
import java.io.InputStream
import kotlin.time.Duration.Companion.milliseconds
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicBoolean


class JustDropHttpClient {
    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json()
        }

        install(HttpTimeout) {
            connectTimeoutMillis = 5_000
            socketTimeoutMillis = 10_000
        }
    }

    suspend fun getDevice(device: DiscoveredDevice): DeviceInfo {
        val url = "http://${device.host}:${device.port}/device"

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

        supervisorScope {
            val lastProgressTime = AtomicLong(System.currentTimeMillis())
            val stalled = AtomicBoolean(false)

            val uploadJob = async {
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
                                            file.mimeType
                                                ?: "application/octet-stream"
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
                        lastProgressTime.set(System.currentTimeMillis())
                        onProgress(
                            bytesSentTotal, contentLength
                        )
                    }
                }
            }

            val watchdogJob = launch {
                while (isActive) {
                    delay(1_000.milliseconds)

                    val elapsed = System.currentTimeMillis() - lastProgressTime.get()

                    if (elapsed >= 10_000) {
                        stalled.set(true)
                        uploadJob.cancel()
                        break
                    }
                }
            }

            try {
                uploadJob.await()
            } catch (e: CancellationException) {
                if (stalled.get() && currentCoroutineContext().isActive) {
                    throw IOException("Upload stalled", e)
                }

                throw e
            } finally {
                watchdogJob.cancel()
            }

        }
    }
    suspend fun finishTransfer(
        device: DiscoveredDevice
    ) {
        val url = "http://${device.host}:${device.port}/transfer/finish"

        Log.d("HTTP_CLIENT", "POST /transfer/finish -> $url")

        client.post(url) {
            expectSuccess = true
        }
    }
}