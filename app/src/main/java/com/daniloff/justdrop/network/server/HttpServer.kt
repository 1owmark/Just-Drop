package com.daniloff.justdrop.network.server

import android.content.Context
import android.os.Build
import android.util.Log
import com.daniloff.data.DeviceIdProvider
import com.daniloff.justdrop.model.DeviceInfo
import com.daniloff.justdrop.model.TransferRequest
import com.daniloff.justdrop.model.TransferResponse
import com.daniloff.justdrop.utils.FileStorage
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
import kotlinx.coroutines.withContext

class HttpServer(
    private val context: Context,
    private val deviceIdProvider: DeviceIdProvider
) {
    private val fileStorage = FileStorage(context)
    private val maxFileSize = 512L * 1024 * 1024 * 1024
    suspend fun start(
        onTransferRequest: (TransferRequest, CompletableDeferred<TransferResponse>) -> Unit
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
                            Build.MODEL)
                    )
                }

                post("/transfer/request") {
                    Log.d("HANDSHAKE", "request received")
                    val transferRequest = call.receive<TransferRequest>()
                    Log.d("HANDSHAKE", "request parsed")
                    val response = CompletableDeferred<TransferResponse>()
                    onTransferRequest(transferRequest, response)
                    val result = response.await()
                    call.respond(result)
                }

                post("/upload") {
                    Log.d("UPLOAD_SERVER", "upload request received")

                    var saved = false

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
                                        fileStorage.save(
                                            fileName,
                                            mimeType,
                                            inputStream
                                        )
                                    }
                                }

                                Log.d("UPLOAD_SERVER", "saving finished")

                                saved = true
                            }
                        }

                        part.release()
                    }

                    Log.d("UPLOAD_SERVER", "upload request finished")

                    call.respond(
                        if (saved) {
                            HttpStatusCode.OK
                        } else {
                            HttpStatusCode.BadRequest
                        }
                    )
                }
            }
        }
        server.start()

        val connectors = server.engine.resolvedConnectors()
        val connector = connectors[0]
        val port = connector.port
        Log.d("httpServer", "port = ${connector.port}\nhost = ${connector.host}")
        return port
    }
}