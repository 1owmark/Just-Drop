package com.daniloff.justdrop.model

import java.net.Inet4Address


data class DiscoveredDevice(val serviceName: String, val host: Inet4Address, val port: Int)
