package com.daniloff.justdrop.model

import java.net.InetAddress

data class DiscoveredDevice(val serviceName: String, val host: InetAddress, val port: Int)
