package com.daniloff.justdrop.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.application
import androidx.lifecycle.viewModelScope
import com.daniloff.justdrop.model.Device
import com.daniloff.justdrop.model.DiscoveredDevice
import com.daniloff.justdrop.model.SelectedFile
import com.daniloff.justdrop.network.client.JustDropHttpClient
import com.daniloff.justdrop.network.discovery.DeviceDiscovery
import com.daniloff.justdrop.network.server.HttpServer
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.time.Duration.Companion.milliseconds
import kotlin.math.pow
import java.text.DecimalFormat


class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val deviceDiscovery = DeviceDiscovery(application)
    private val httpServer = HttpServer()
    private val httpClient = JustDropHttpClient()
    private val deviceCache: MutableMap<DiscoveredDevice, Device> = mutableMapOf()
    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    val devices = _devices.asStateFlow()
    private val _searchState = MutableStateFlow<DeviceSearchState>(DeviceSearchState.Searching)
    val searchState = _searchState.asStateFlow()
    private var searchTimerJob: Job? = null
    private val _selectedFiles = MutableStateFlow<List<SelectedFile>>(emptyList())
    val selectedFiles = _selectedFiles.asStateFlow()

    init {
        viewModelScope.launch {
            val port = httpServer.start()
            deviceDiscovery.start(port)
        }

        viewModelScope.launch {
            deviceDiscovery.discoveredDevices.collect { devices ->
                for (device in devices) {
                    if (device in deviceCache) {
                        continue
                    } else {
                        val deviceInfo = httpClient.getDevice(device)
                        val foundDevice = Device(device, deviceInfo)
                        _devices.value += foundDevice
                        deviceCache[device] = foundDevice
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

    fun onFilesSelected(device: Device, uris: List<Uri>) {
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
                    _selectedFiles.value += SelectedFile(uri, fileName, fileSize, mimeType)
                }
            }
        }
    }

    fun deleteFile(file: SelectedFile) {
        _selectedFiles.value -= file
    }

    fun clearSelectedFiles() {
        _selectedFiles.value = emptyList()
    }
}