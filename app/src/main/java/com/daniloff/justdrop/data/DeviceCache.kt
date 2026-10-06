package com.daniloff.justdrop.data

import com.daniloff.justdrop.model.Device
import java.util.concurrent.ConcurrentHashMap

object DeviceCache {
    val devices = ConcurrentHashMap<String, Device>()
}