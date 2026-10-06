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
import com.daniloff.justdrop.model.SelectedFile
import com.daniloff.justdrop.network.client.JustDropHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.daniloff.justdrop.data.TransferStateHolder
import com.daniloff.justdrop.model.TransferStatus

class TransferService : Service() {
    private val httpClient = JustDropHttpClient()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    companion object {
        private const val CHANNEL_ID = "transfer_channel"
        private const val NOTIFICATION_ID = 1

        private const val EXTRA_DEVICE_ID = "device_id"
        private const val EXTRA_FILES = "files"

        fun start(
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()

        val notification = createNotification()

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )

        val deviceId = intent?.getStringExtra(EXTRA_DEVICE_ID)
        val device = deviceId?.let { DeviceCache.devices[it] }

        val files = intent
            ?.getParcelableArrayListExtra<SelectedFile>(EXTRA_FILES)
            .orEmpty()

        serviceScope.launch {
            for (file in files) {
                try {
                    val device = DeviceCache.devices[deviceId]

                    if (device == null) {
                        Log.d("TRANSFER_SERVICE", "Device disappeared")
                        continue
                    }

                    TransferStateHolder.updateFile(
                        file,
                        TransferStatus.SENDING
                    )

                    val stream = contentResolver.openInputStream(file.uri)

                    if (stream == null) {
                        Log.e(
                            "TRANSFER_SERVICE",
                            "Failed to open ${file.name}"
                        )

                        TransferStateHolder.updateFile(
                            file,
                            TransferStatus.ERROR
                        )

                        return@launch
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
                httpClient.finishTransfer(device!!.networkInfo)
            } catch (e: Exception) {
                Log.e(
                    "TRANSFER_SERVICE",
                    "Failed to finish transfer",
                    e
                )
            }
            stopSelf()
        }

        Log.d("TRANSFER_SERVICE", "deviceId=$deviceId")
        Log.d("TRANSFER_SERVICE", "device=$device")
        Log.d("TRANSFER_SERVICE", "files=$files")

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}