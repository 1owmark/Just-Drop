package com.daniloff.justdrop.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.daniloff.justdrop.model.Device
import com.daniloff.justdrop.model.DiscoveredDevice
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
}