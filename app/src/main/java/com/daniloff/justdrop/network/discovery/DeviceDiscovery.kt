package com.daniloff.justdrop.network.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.daniloff.justdrop.R
import java.net.ServerSocket

class DeviceDiscovery(val context: Context) {
    val nsdManager: NsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    val serviceInfo = NsdServiceInfo()

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
}