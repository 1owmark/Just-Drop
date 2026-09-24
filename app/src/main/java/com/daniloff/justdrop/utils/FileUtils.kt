package com.daniloff.justdrop.utils

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