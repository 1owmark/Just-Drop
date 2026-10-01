package com.daniloff.justdrop.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.application
import androidx.lifecycle.viewModelScope
import com.daniloff.justdrop.model.Device
import com.daniloff.justdrop.model.DiscoveredDevice
import com.daniloff.justdrop.model.PendingTransferRequest
import com.daniloff.justdrop.model.SelectedFile
import com.daniloff.justdrop.model.TransferFile
import com.daniloff.justdrop.model.TransferRequest
import com.daniloff.justdrop.model.TransferRequestFile
import com.daniloff.justdrop.model.TransferRequestState
import com.daniloff.justdrop.model.TransferResponse
import com.daniloff.justdrop.model.TransferStatus
import com.daniloff.justdrop.network.client.JustDropHttpClient
import com.daniloff.justdrop.network.discovery.DeviceDiscovery
import com.daniloff.justdrop.network.server.HttpServer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlin.time.Duration.Companion.milliseconds


class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val deviceDiscovery = DeviceDiscovery(application)
    private val httpServer = HttpServer(application)
    private val httpClient = JustDropHttpClient()
    private val deviceCache: MutableMap<DiscoveredDevice, Device> = mutableMapOf()
    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    val devices = _devices.asStateFlow()
    private val _searchState = MutableStateFlow<DeviceSearchState>(DeviceSearchState.Searching)
    val searchState = _searchState.asStateFlow()
    private var searchTimerJob: Job? = null
    private val _selectedFiles = MutableStateFlow<List<SelectedFile>>(emptyList())
    val selectedFiles = _selectedFiles.asStateFlow()
    private val _transferFiles = MutableStateFlow<List<TransferFile>>(emptyList())
    val transferFiles = _transferFiles.asStateFlow()
    private val _events = MutableSharedFlow<UiEvent>()
    val events = _events.asSharedFlow()
    private val _transferRequest = MutableStateFlow<PendingTransferRequest?>(null)
    val transferRequest = _transferRequest.asStateFlow()
    private val _transferRequestState = MutableStateFlow(TransferRequestState.IDLE)
    val transferRequestState = _transferRequestState.asStateFlow()
    private var transferJob: Job? = null

    init {
        viewModelScope.launch {
            val port = httpServer.start { request, response ->
                Log.d("HANDSHAKE", "showing request dialog")
                _transferRequest.value = PendingTransferRequest(request, response)
            }
            deviceDiscovery.start(port)
        }

        viewModelScope.launch {
            deviceDiscovery.discoveredDevices.collect { devices ->
                for (device in devices) {
                    if (device in deviceCache) {
                        continue
                    } else {
                        try {
                            val deviceInfo = httpClient.getDevice(device)
                            val foundDevice = Device(device, deviceInfo)
                            _devices.value += foundDevice
                            deviceCache[device] = foundDevice
                        } catch (e: Exception) {
                            Log.e(
                                "Discovery",
                                "Failed to connect to ${device.host}:${device.port}",
                                e
                            )
                        }
                    }
                }
                val devicesToRemove = deviceCache.keys.filterNot { it in devices }

                for (device in devicesToRemove) {
                    deviceCache.remove(device)
                }
                _devices.value = deviceCache.values.toList()
            }
        }

        viewModelScope.launch {
            devices
                .map { it.isNotEmpty() }
                .distinctUntilChanged()
                .collect { hasDevices ->
                    if (hasDevices) {
                        searchTimerJob?.cancel()
                    } else {
                        searchTimerJob?.cancel()
                        _searchState.value = DeviceSearchState.Searching

                        searchTimerJob = launch {
                            delay(20_000.milliseconds)
                            _searchState.value = DeviceSearchState.NotFound
                        }
                    }
                }
        }
    }

    fun onFilesSelected(uris: List<Uri>) {
        val contentResolver = application.contentResolver
        for (uri in uris) {
            val cursor = contentResolver.query(
                uri,
                arrayOf(
                    OpenableColumns.DISPLAY_NAME,
                    OpenableColumns.SIZE
                ),
                null,
                null,
                null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameColumnIndex = it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)
                    val sizeColumnIndex = it.getColumnIndexOrThrow(OpenableColumns.SIZE)
                    val fileName = it.getString(nameColumnIndex)
                    val fileSize = it.getLong(sizeColumnIndex)
                    val mimeType = contentResolver.getType(uri)
                    val selectedFile = SelectedFile(uri, fileName, fileSize, mimeType)
                    if (selectedFile !in _selectedFiles.value) {
                        _selectedFiles.value += selectedFile
                    } else {
                        viewModelScope.launch {
                            _events.emit(
                                UiEvent.FileAlreadyAdded(fileName)
                            )
                        }
                    }
                }
            }
        }
    }

    suspend fun requestTransfer(
        device: Device
    ): TransferResponse {
        val transferRequestFileList = _selectedFiles.value
            .map {
                TransferRequestFile(it.name, it.size)
            }

        val transferRequest = TransferRequest(transferRequestFileList)

        _transferRequestState.value = TransferRequestState.WAITING

        Log.d("HANDSHAKE", "sending request")

        val response = httpClient.sendTransferRequest(
            device.networkInfo,
            transferRequest
        )

        Log.d("HANDSHAKE", "response = $response")

        when (response) {
            TransferResponse.ACCEPTED -> _transferRequestState.value = TransferRequestState.ACCEPTED
            TransferResponse.DECLINED -> _transferRequestState.value = TransferRequestState.DECLINED
        }

        return response
    }

    // Отправка файлов
    fun sendFiles(device: Device) {
        val files = _selectedFiles.value
        transferJob = viewModelScope.launch {
            startTransfer()

            for (file in files) {
                val stream = application.contentResolver.openInputStream(file.uri)

                if (stream == null) {
                    _events.emit(UiEvent.FileOpenError(file.name))
                    updateTransferFile(file, TransferStatus.ERROR)
                    continue
                }

                updateTransferFile(file, TransferStatus.SENDING)

                try {
                    stream.use {
                        Log.d("UPLOAD", "uploadFile call started")
                        httpClient.uploadFile(
                            device = device.networkInfo,
                            file = file,
                            stream = it,
                            onProgress = { bytesSent, _ ->
                                val progress = (bytesSent.toFloat() / file.size).coerceIn(0f, 1f)

                                if (bytesSent % (10 * 1024 * 1024) < 4096) {
                                    Log.d(
                                        "UPLOAD",
                                        "sent=$bytesSent / ${file.size}"
                                    )
                                }

                                updateTransferFile(
                                    file,
                                    TransferStatus.SENDING,
                                    progress
                                )
                            }
                        )
                        Log.d("UPLOAD", "uploadFile call finished")
                        updateTransferFile(file, TransferStatus.SUCCESS)
                    }
                }
                catch (e: CancellationException) {
                    throw e
                }
                catch (e: Exception) {
                    _events.emit(UiEvent.FileUploadError(file.name))
                    updateTransferFile(file, TransferStatus.ERROR)
                }
            }
            clearSelectedFiles()
        }
    }

    // SelectedFile -> TransferFile
    fun startTransfer() {
        _transferFiles.value = _selectedFiles.value.map { file ->
            TransferFile(file = file)
        }
    }

    private fun updateTransferFile(
        targetFile: SelectedFile,
        status: TransferStatus,
        progress: Float? = null
    ) {
        _transferFiles.update { files ->
            files.map {
                if (it.file == targetFile) {
                    it.copy(
                        status = status,
                        progress = progress ?: it.progress
                    )
                } else it
            }
        }
    }

    fun deleteFile(file: SelectedFile) {
        _selectedFiles.value -= file
    }

    fun clearSelectedFiles() {
        _selectedFiles.value = emptyList()
    }

    fun acceptTransfer() {
        _transferRequest.value?.response?.complete(
            TransferResponse.ACCEPTED
        )

        _transferRequest.value = null
    }

    fun declineTransfer() {
        _transferRequest.value?.response?.complete(
            TransferResponse.DECLINED
        )

        _transferRequest.value = null
    }

    fun resetTransferRequestState() {
        _transferRequestState.value = TransferRequestState.IDLE
    }

    fun cancelTransfer() = transferJob?.cancel()
}