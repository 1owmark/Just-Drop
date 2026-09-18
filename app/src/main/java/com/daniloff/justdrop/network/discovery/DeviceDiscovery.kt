package com.daniloff.justdrop.network.discovery

import android.content.Context
import android.net.ConnectivityManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresExtension
import com.daniloff.justdrop.R
import com.daniloff.justdrop.model.DiscoveredDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address
import java.net.InetAddress

class DeviceDiscovery(val context: Context) {
    val nsdManager: NsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    val serviceInfo = NsdServiceInfo()
    val serviceCallbacks: MutableMap<NsdServiceInfo, NsdManager.ServiceInfoCallback> =
        mutableMapOf()
    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(listOf())
    val discoveredDevices = _discoveredDevices.asStateFlow()
    val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    init {
        val serviceInfo = this@DeviceDiscovery.serviceInfo.apply {
            serviceName = context.getString(R.string.app_name)
            serviceType = "_http._tcp."
            port = 5000
        }

        nsdManager.registerService(
            serviceInfo,
            NsdManager.PROTOCOL_DNS_SD,
            object : NsdManager.RegistrationListener {
                override fun onRegistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                    TODO("Not yet implemented")
                }

                override fun onServiceRegistered(serviceInfo: NsdServiceInfo?) {
                    Log.d("Discovery", "Just Drop service registered")
                }

                override fun onServiceUnregistered(serviceInfo: NsdServiceInfo?) {
                    TODO("Not yet implemented")
                }

                override fun onUnregistrationFailed(
                    serviceInfo: NsdServiceInfo?,
                    errorCode: Int
                ) {
                    TODO("Not yet implemented")
                }
            })
    }

    fun getCurrentIp(): InetAddress? {
        val currentNetwork = connectivityManager.activeNetwork ?: return null
        val linkProperties = connectivityManager.getLinkProperties(currentNetwork) ?: return null
        val linkAddresses = linkProperties.linkAddresses
        for (address in linkAddresses) {
            if (address.address is Inet4Address) {
                return address.address
            }
        }
        return null
    }

    private val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(serviceType: String?) {
            Log.d("Discovery", "Discovery started")
        }

        override fun onDiscoveryStopped(serviceType: String?) {
            Log.d("Discovery", "Discovery stopped")
        }

        @RequiresExtension(extension = Build.VERSION_CODES.TIRAMISU, version = 7)
        @RequiresApi(Build.VERSION_CODES.P)
        override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
            if (serviceInfo == null) return

            val callback = createServiceInfoCallback()

            serviceCallbacks[serviceInfo] = callback

            nsdManager.registerServiceInfoCallback(
                serviceInfo,
                context.mainExecutor,
                callback
            )
        }

        @RequiresExtension(extension = Build.VERSION_CODES.TIRAMISU, version = 7)
        override fun onServiceLost(serviceInfo: NsdServiceInfo?) {
            if (serviceInfo == null) return

            val callback = serviceCallbacks[serviceInfo] ?: return

            nsdManager.unregisterServiceInfoCallback(callback)

            serviceCallbacks.remove(serviceInfo)
        }

        override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
            Log.d("Discovery", "Start discovery failed: $errorCode")
        }

        override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
            Log.d("Discovery", "Stop discovery failed: $errorCode")
        }

    }

    @RequiresExtension(extension = Build.VERSION_CODES.TIRAMISU, version = 7)
    private fun createServiceInfoCallback() =
        object : NsdManager.ServiceInfoCallback {
            override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                Log.d("ServiceCallback", "service reg failed")
            }

            override fun onServiceInfoCallbackUnregistered() {
                Log.d("ServiceCallback", "service reg unregistered")
            }

            override fun onServiceLost() {

            }

            override fun onServiceUpdated(serviceInfo: NsdServiceInfo) {
                val addressess = serviceInfo.hostAddresses

                for (address in addressess) {
                    if (address !is Inet4Address) {
                        continue
                    }

                    if (getCurrentIp() == address) {
                        return
                    }

                    val device = DiscoveredDevice(
                        serviceInfo.serviceName,
                        address,
                        serviceInfo.port
                    )

                    _discoveredDevices.value += device
                }
            }
        }

    fun discover() {
        nsdManager.discoverServices(
            "_http._tcp.",
            NsdManager.PROTOCOL_DNS_SD,
            discoveryListener
        )
    }
}