package com.daniloff.justdrop.network.server

import android.content.Context
import android.os.Build
import android.util.Log
import com.daniloff.justdrop.model.DeviceInfo
import com.daniloff.justdrop.utils.FileStorage
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.jvm.javaio.toInputStream

class HttpServer(
    private val context: Context
) {
    private val fileStorage = FileStorage(context)
    suspend fun start(): Int {
        val server = embeddedServer(CIO, 0, "0.0.0.0") {

            install(ContentNegotiation) {
                json()
            }

            routing {
                get("/") {
                    call.respond("Hello from Just Drop")
                }
                get("/device") {
                    call.respond(
                        DeviceInfo(Build.MANUFACTURER, Build.MODEL)
                    )
                }
                post("/upload") {
                    val multipartData = call.receiveMultipart()

                    multipartData.forEachPart { part ->
                        when(part) {
                            is PartData.FileItem -> {
                                val fileName = part.originalFileName ?: return@forEachPart
                                val mimeType = part.contentType.toString()

                                part.provider().toInputStream().use { inputStream ->
                                    fileStorage.save(
                                        fileName,
                                        mimeType,
                                        inputStream
                                    )
                                }
                            }
                            else -> {}
                        }
                    }
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