package com.daniloff.justdrop.utils

import android.health.connect.datatypes.units.Length
import java.text.DecimalFormat
import kotlin.math.pow

fun formatFileSize(size: Long): String {
    var unit = ""
    var divider = 1.0

    if (size < 1024) {
        unit = "B"
    } else if (size < 1024.0.pow(2.0)) {
        unit = "KB"
        divider = 1024.0
    } else if (size < 1024.0.pow(3.0)) {
        unit = "MB"
        divider = 1024.0.pow(2.0)
    } else {
        unit = "GB"
        divider = 1024.0.pow(3.0)
    }
    val value = size.toDouble() / divider
    val formattedValue = DecimalFormat("#.##").format(value)
    return "$formattedValue $unit"
}

fun truncateFileName(
    name: String,
    maxLength: Int = 30
): String {
    val dotIndex = name.lastIndexOf('.')
    if (dotIndex > 0 && dotIndex < name.lastIndex) {
        val extension = name.substring(dotIndex)
        val fileName = name.substring(0, dotIndex)
        val availableLength = maxLength - 3 - extension.length
        if (fileName.length > availableLength) {
            return "${fileName.take(availableLength)}...$extension"
        } else {
            return name
        }
    } else {
        if (name.length > maxLength) {
            return "${name.take(maxLength - 3)}..."
        } else {
            return name
        }
    }
}