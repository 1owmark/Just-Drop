package com.daniloff.justdrop.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.application
import androidx.lifecycle.viewModelScope
import com.daniloff.justdrop.data.DeviceIdProvider
import com.daniloff.justdrop.model.Device
import com.daniloff.justdrop.model.IncomingTransfer
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
import android.content.Intent
import androidx.core.content.ContextCompat
import com.daniloff.justdrop.TransferService
import com.daniloff.justdrop.data.DeviceCache
import com.daniloff.justdrop.data.TransferStateHolder


class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val deviceDiscovery = DeviceDiscovery(application)
    private val deviceIdProvider = DeviceIdProvider(application)
    private val httpServer = HttpServer(application, deviceIdProvider)
    private val httpClient = JustDropHttpClient()
    private val deviceCache = DeviceCache.devices
    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    val devices = _devices.asStateFlow()
    private val _searchState = MutableStateFlow<DeviceSearchState>(DeviceSearchState.Searching)
    val searchState = _searchState.asStateFlow()
    private var searchTimerJob: Job? = null
    private val _selectedFiles = MutableStateFlow<List<SelectedFile>>(emptyList())
    val selectedFiles = _selectedFiles.asStateFlow()
    val transferFiles = TransferStateHolder.transferFiles
    private val _events = MutableSharedFlow<UiEvent>()
    val events = _events.asSharedFlow()
    private val _transferRequest = MutableStateFlow<PendingTransferRequest?>(null)
    val transferRequest = _transferRequest.asStateFlow()
    private val _transferRequestState = MutableStateFlow(TransferRequestState.IDLE)
    val transferRequestState = _transferRequestState.asStateFlow()
    private var transferJob: Job? = null
    private var transferDeviceId: String? = null
    private val _incomingTransfer = MutableStateFlow<IncomingTransfer?>(null)
    val incomingTransfer = _incomingTransfer.asStateFlow()

    init {
        viewModelScope.launch {
            val port = httpServer.start(
                onTransferRequest = { request, response ->
                    Log.d("HANDSHAKE", "showing request dialog")
                    _transferRequest.value =
                        PendingTransferRequest(request, response)
                },
                onUploadProgress = { bytes ->
                    _incomingTransfer.update { transfer ->
                        transfer?.copy(
                            receivedBytes = transfer.receivedBytes + bytes
                        )
                    }
                },
                onFileReceived = {
                    _incomingTransfer.update { transfer ->
                        transfer?.copy(
                            completedFiles = transfer.completedFiles + 1
                        )
                    }
                },
                onTransferFinished = {
                    Log.d("INCOMING_TRANSFER", "transfer finished")

                    _incomingTransfer.update { transfer ->
                        transfer?.copy(
                            isFinished = true
                        )
                    }
                },
                onTransferCancelled = {
                    _incomingTransfer.update { transfer ->
                        transfer?.copy(isCancelled = true)
                    }
                },
                onTransferError = {
                    _incomingTransfer.update { transfer ->
                        transfer?.copy(isError = true)
                    }
                }
            )
            deviceDiscovery.start(port)
        }

        viewModelScope.launch {
            deviceDiscovery.discoveredDevices.collect { devices ->
                Log.d("Discovery", "discoveredDevices = $devices")
                val currentDeviceIds = mutableSetOf<String>()
                for (device in devices) {
                    try {
                        val deviceInfo = httpClient.getDevice(device)
                        currentDeviceIds += deviceInfo.deviceId
                        val foundDevice = Device(device, deviceInfo)
                        deviceCache[deviceInfo.deviceId] = foundDevice
                    } catch (e: Exception) {
                        Log.e(
                            "Discovery",
                            "Failed to connect to ${device.host}:${device.port}",
                            e
                        )
                    }
                }
                val devicesToRemove = deviceCache.keys.filterNot { it in currentDeviceIds }

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
        deviceId: String
    ): TransferResponse? {
        val device = deviceCache[deviceId]

        if (device == null) {
            _events.emit(UiEvent.DeviceUnavailable)
            return null
        }

        val transferRequestFileList = _selectedFiles.value
            .map {
                TransferRequestFile(it.name, it.size)
            }

        val transferRequest = TransferRequest(transferRequestFileList)

        _transferRequestState.value = TransferRequestState.WAITING

        try {
            val response = httpClient.sendTransferRequest(
                device.networkInfo,
                transferRequest
            )

            when (response) {
                TransferResponse.ACCEPTED -> _transferRequestState.value =
                    TransferRequestState.ACCEPTED

                TransferResponse.DECLINED -> _transferRequestState.value =
                    TransferRequestState.DECLINED
            }

            return response
        } catch (e: Exception) {
            _events.emit(UiEvent.TransferRequestError)
            return null
        }
    }

    // Отправка файлов
    fun sendFiles(deviceId: String) {
        transferDeviceId = deviceId
        val files = _selectedFiles.value

        transferJob = viewModelScope.launch {
            val device = deviceCache[deviceId]

            if (device == null) {
                return@launch
            }

            TransferService.start(
                getApplication(),
                deviceId,
                files
            )

            startTransfer()
        }
    }

    // SelectedFile -> TransferFile
    fun startTransfer() {
        val files = _selectedFiles.value.map { file ->
            TransferFile(file = file)
        }

        TransferStateHolder.setFiles(files)
    }

    fun deleteFile(file: SelectedFile) {
        _selectedFiles.value -= file
    }

    fun clearSelectedFiles() {
        _selectedFiles.value = emptyList()
    }

    fun acceptTransfer() {
        val request = _transferRequest.value ?: return

        request.response.complete(
            TransferResponse.ACCEPTED
        )

        val totalFiles = request.request.files.size
        val totalBytes = request.request.files.sumOf { it.size }

        _incomingTransfer.value = IncomingTransfer(
            totalFiles = totalFiles,
            totalBytes = totalBytes
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

    fun cancelTransfer() {
        val deviceId = transferDeviceId ?: return
        val device = deviceCache[deviceId] ?: return

        viewModelScope.launch {
            try {
                httpClient.cancelTransfer(device.networkInfo)
            } catch (e: Exception) {
                Log.e("TRANSFER", "Failed to cancel transfer", e)
            }
        }

        transferJob?.cancel()
    }

    fun clearIncomingTransfer() {
        _incomingTransfer.value = null
    }
}