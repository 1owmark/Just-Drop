package com.daniloff.justdrop.network.server

import android.content.Context
import android.os.Build
import android.util.Log
import com.daniloff.justdrop.data.DeviceIdProvider
import com.daniloff.justdrop.model.DeviceInfo
import com.daniloff.justdrop.model.TransferRequest
import com.daniloff.justdrop.model.TransferResponse
import com.daniloff.justdrop.data.storage.FileStorage
import com.daniloff.justdrop.data.storage.ProgressInputStream
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.Volatile
import kotlin.time.Duration.Companion.milliseconds

class HttpServer(
    private val context: Context,
    private val deviceIdProvider: DeviceIdProvider
) {
    private val fileStorage = FileStorage(context)
    private val maxFileSize = 512L * 1024 * 1024 * 1024
    private val transferCancelled = AtomicBoolean(false)
    private val uploadLock = Any()

    @Volatile
    private var currentUploadInputStream: InputStream? = null
    suspend fun start(
        onTransferRequest: (TransferRequest, CompletableDeferred<TransferResponse>) -> Unit,
        onUploadProgress: (Long) -> Unit,
        onFileReceived: () -> Unit,
        onTransferFinished: () -> Unit,
        onTransferCancelled: () -> Unit,
        onTransferError: () -> Unit
    ): Int {
        val server = embeddedServer(CIO, 0, "0.0.0.0") {

            install(ContentNegotiation) {
                json()
            }

            routing {
                get("/") {
                    call.respond("Hello from Just Drop")
                }

                get("/device") {
                    val deviceId = deviceIdProvider.getDeviceId()
                    Log.d("DEVICE_ID", "deviceId = $deviceId")
                    call.respond(
                        DeviceInfo(
                            deviceId,
                            Build.MANUFACTURER,
                            Build.MODEL
                        )
                    )
                }

                post("/transfer/request") {
                    val transferRequest = call.receive<TransferRequest>()
                    val response = CompletableDeferred<TransferResponse>()
                    onTransferRequest(transferRequest, response)
                    val result = response.await()
                    if (result == TransferResponse.ACCEPTED) {
                        transferCancelled.set(false)
                    }
                    call.respond(result)
                }

                post("/upload") {
                    if (transferCancelled.get()) {
                        Log.d("UPLOAD_SERVER", "transfer already cancelled")
                        call.respond(HttpStatusCode.Conflict)
                        return@post
                    }

                    var fileSaved = false

                    try {
                        call.receiveMultipart(
                            maxFileSize
                        ).forEachPart { part ->
                            if (part is PartData.FileItem) {
                                val fileName = part.originalFileName

                                if (fileName != null) {
                                    val mimeType =
                                        part.contentType?.toString() ?: "application/octet-stream"

                                    withContext(Dispatchers.IO) {
                                        part.provider().toInputStream().use { inputStream ->

                                            synchronized(uploadLock) {
                                                if (transferCancelled.get()) {
                                                    throw IOException("Transfer cancelled")
                                                }
                                                currentUploadInputStream = inputStream
                                            }

                                            try {
                                                coroutineScope {
                                                    val lastProgressTime =
                                                        AtomicLong(System.currentTimeMillis())

                                                    val progressInputStream =
                                                        ProgressInputStream(inputStream) { bytes ->
                                                            lastProgressTime.set(System.currentTimeMillis())
                                                            onUploadProgress(bytes)
                                                        }

                                                    val watchdogJob = launch {
                                                        while (isActive) {
                                                            delay(1_000.milliseconds)

                                                            val elapsed =
                                                                System.currentTimeMillis() - lastProgressTime.get()

                                                            if (elapsed >= 10_000) {
                                                                synchronized(uploadLock) {
                                                                    currentUploadInputStream?.close()
                                                                }
                                                                break
                                                            }
                                                        }
                                                    }

                                                    try {
                                                        fileStorage.save(
                                                            fileName,
                                                            mimeType,
                                                            progressInputStream
                                                        )

                                                        fileSaved = true
                                                        onFileReceived()
                                                    } finally {
                                                        watchdogJob.cancel()
                                                    }
                                                }
                                            } finally {
                                                synchronized(uploadLock) {
                                                    currentUploadInputStream = null
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            part.release()
                        }
                        call.respond(
                            if (fileSaved) {
                                HttpStatusCode.OK
                            } else {
                                HttpStatusCode.BadRequest
                            }
                        )
                    } catch (e: Exception) {
                        if (transferCancelled.get()) {
                            Log.d(
                                "UPLOAD_SERVER",
                                "upload cancelled"
                            )
                        } else {
                            Log.e(
                                "UPLOAD_SERVER",
                                "upload failed: ${e::class.simpleName}: ${e.message}",
                                e
                            )
                        }
                    }
                }
                post("transfer/finish") {
                    onTransferFinished()

                    call.respond(HttpStatusCode.OK)
                }
                post("/transfer/cancel") {
                    val inputStreamToClose = synchronized(uploadLock) {
                        transferCancelled.set(true)
                        currentUploadInputStream
                    }
                    inputStreamToClose?.close()

                    onTransferCancelled()

                    call.respond(HttpStatusCode.OK)
                }
            }
        }
        server.start()

        val connectors = server.engine.resolvedConnectors()
        val connector = connectors[0]
        val port = connector.port
        Log.d("httpServer", "port = ${connector.port}\nhost = ${connector.host}")
        Log.d(
            "HTTP_SERVER",
            "Returning port $port"
        )
        return port
    }
}