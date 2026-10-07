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
import kotlinx.coroutines.Job
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
    val lastProgressTime = AtomicLong(System.currentTimeMillis())

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
                    Log.d("HANDSHAKE", "request received")
                    val transferRequest = call.receive<TransferRequest>()
                    Log.d("HANDSHAKE", "request parsed")
                    val response = CompletableDeferred<TransferResponse>()
                    onTransferRequest(transferRequest, response)
                    val result = response.await()
                    if (result == TransferResponse.ACCEPTED) {
                        transferCancelled.set(false)
                    }
                    call.respond(result)
                }

                post("/upload") {
                    Log.d("UPLOAD_SERVER", "upload request received")

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
                            Log.d("UPLOAD_SERVER", "part received: ${part::class.simpleName}")

                            if (part is PartData.FileItem) {
                                val fileName = part.originalFileName

                                Log.d("UPLOAD_SERVER", "file: $fileName")

                                if (fileName != null) {
                                    val mimeType =
                                        part.contentType?.toString() ?: "application/octet-stream"

                                    Log.d("UPLOAD_SERVER", "saving started")

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
                                                                Log.d(
                                                                    "UPLOAD_SERVER",
                                                                    "no progress for 10 sec, closing input stream"
                                                                )
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
                                                Log.d(
                                                    "UPLOAD_SERVER",
                                                    "clearing current upload stream"
                                                )
                                                synchronized(uploadLock) {
                                                    currentUploadInputStream = null
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Log.d("UPLOAD_SERVER", "saving finished")
                            part.release()
                        }

                        Log.d(
                            "UPLOAD_SERVER",
                            "upload request finished, fileSaved=$fileSaved"
                        )
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
                    Log.d("TRANSFER", "finish request received")

                    onTransferFinished()

                    call.respond(HttpStatusCode.OK)
                }
                post("/transfer/cancel") {
                    Log.d("TRANSFER", "cancel request received")
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

        Log.d(
            "HTTP_SERVER",
            "Ktor server started"
        )

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