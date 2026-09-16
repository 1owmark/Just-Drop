package com.daniloff.justdrop.network.server

import android.os.Build
import android.util.Log
import com.daniloff.justdrop.model.DeviceInfo
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.*

class HttpServer {
    private val scope = CoroutineScope(Dispatchers.IO)

    fun start() {
        val server = scope.embeddedServer(CIO, 0, "0.0.0.0") {

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
            }
        }
        server.start()

        scope.launch {
            val connectors = server.engine.resolvedConnectors()
            val connector = connectors[0]
            Log.d("httpServer", "port = ${connector.port}\nhost = ${connector.host}")
        }
    }
}