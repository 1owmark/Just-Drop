package com.daniloff.justdrop

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.daniloff.justdrop.data.DeviceCache
import com.daniloff.justdrop.data.DeviceIdProvider
import com.daniloff.justdrop.model.SelectedFile
import com.daniloff.justdrop.network.client.JustDropHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.daniloff.justdrop.data.TransferStateHolder
import com.daniloff.justdrop.model.PendingTransferRequest
import com.daniloff.justdrop.model.TransferStatus
import com.daniloff.justdrop.network.server.HttpServer

class TransferService : Service() {
    private val httpClient = JustDropHttpClient()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var httpServer: HttpServer

    override fun onCreate() {
        super.onCreate()

        Log.d("TRANSFER_SERVICE", "onCreate")

        httpServer = HttpServer(
            this,
            DeviceIdProvider(this)
        )
        Log.d(
            "TRANSFER_SERVICE",
            "HttpServer instance created"
        )

        serviceScope.launch {
            val port = httpServer.start(
                onTransferRequest = { request, response ->
                    Log.d(
                        "HANDSHAKE",
                        "transfer request received"
                    )

                    TransferStateHolder.setIncomingRequest(
                        PendingTransferRequest(
                            request,
                            response
                        )
                    )
                },

                onUploadProgress = { bytes ->
                    TransferStateHolder.updateIncomingTransfer { transfer ->
                        transfer?.copy(
                            receivedBytes =
                                transfer.receivedBytes + bytes
                        )
                    }
                },

                onFileReceived = {
                    TransferStateHolder.updateIncomingTransfer { transfer ->
                        transfer?.copy(
                            completedFiles =
                                transfer.completedFiles + 1
                        )
                    }
                },

                onTransferFinished = {
                    Log.d(
                        "INCOMING_TRANSFER",
                        "transfer finished"
                    )

                    TransferStateHolder.updateIncomingTransfer { transfer ->
                        transfer?.copy(
                            isFinished = true
                        )
                    }
                },

                onTransferCancelled = {
                    TransferStateHolder.updateIncomingTransfer { transfer ->
                        transfer?.copy(
                            isCancelled = true
                        )
                    }
                },

                onTransferError = {
                    TransferStateHolder.updateIncomingTransfer { transfer ->
                        transfer?.copy(
                            isError = true
                        )
                    }
                }
            )

            TransferStateHolder.setServerPort(port)

            Log.d(
                "TRANSFER_SERVICE",
                "Server started on port $port"
            )
        }
    }

    companion object {
        private const val CHANNEL_ID = "transfer_channel"
        private const val NOTIFICATION_ID = 1

        private const val EXTRA_DEVICE_ID = "device_id"
        private const val EXTRA_FILES = "files"
        private const val EXTRA_SERVER_MODE = "server_mode"

        fun startSending(
            context: Context,
            deviceId: String,
            files: List<SelectedFile>
        ) {
            val intent = Intent(context, TransferService::class.java).apply {
                putExtra(EXTRA_DEVICE_ID, deviceId)
                putParcelableArrayListExtra(
                    EXTRA_FILES,
                    ArrayList(files)
                )
            }

            ContextCompat.startForegroundService(context, intent)
        }

        fun startServer(context: Context) {
            val intent = Intent(context, TransferService::class.java).apply {
                putExtra(EXTRA_SERVER_MODE, true)
            }

            ContextCompat.startForegroundService(context, intent)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.file_transfer),
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.file_transfer))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        createNotificationChannel()

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            createNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )

        val serverMode =
            intent?.getBooleanExtra(EXTRA_SERVER_MODE, false) == true

        if (serverMode) {
            Log.d("TRANSFER_SERVICE", "Started in server mode")
            return START_STICKY
        }

        val deviceId = intent?.getStringExtra(EXTRA_DEVICE_ID)

        val files = intent
            ?.getParcelableArrayListExtra<SelectedFile>(EXTRA_FILES)
            .orEmpty()

        serviceScope.launch {
            for (file in files) {
                try {
                    val device = DeviceCache.devices[deviceId]

                    if (device == null) {
                        Log.d(
                            "TRANSFER_SERVICE",
                            "Device disappeared"
                        )
                        continue
                    }

                    TransferStateHolder.updateFile(
                        file,
                        TransferStatus.SENDING
                    )

                    val stream =
                        contentResolver.openInputStream(file.uri)

                    if (stream == null) {
                        Log.e(
                            "TRANSFER_SERVICE",
                            "Failed to open ${file.name}"
                        )

                        TransferStateHolder.updateFile(
                            file,
                            TransferStatus.ERROR
                        )

                        continue
                    }

                    stream.use {
                        httpClient.uploadFile(
                            device = device.networkInfo,
                            file = file,
                            stream = it,
                            onProgress = { bytesSent, _ ->
                                val progress =
                                    (bytesSent.toFloat() / file.size)
                                        .coerceIn(0f, 1f)

                                TransferStateHolder.updateFile(
                                    file,
                                    TransferStatus.SENDING,
                                    progress
                                )

                                Log.d(
                                    "TRANSFER_SERVICE",
                                    "sent=$bytesSent / ${file.size}"
                                )
                            }
                        )
                    }

                    TransferStateHolder.updateFile(
                        file,
                        TransferStatus.SUCCESS,
                        1f
                    )

                } catch (e: Exception) {
                    Log.e(
                        "TRANSFER_SERVICE",
                        "Failed to send ${file.name}",
                        e
                    )

                    TransferStateHolder.updateFile(
                        file,
                        TransferStatus.ERROR
                    )

                    continue
                }
            }

            try {
                val device = DeviceCache.devices[deviceId]

                if (device != null) {
                    httpClient.finishTransfer(
                        device.networkInfo
                    )
                }
            } catch (e: Exception) {
                Log.e(
                    "TRANSFER_SERVICE",
                    "Failed to finish transfer",
                    e
                )
            }

            stopSelf()
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}