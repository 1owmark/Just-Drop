package com.daniloff.justdrop.data.storage

import java.io.FilterInputStream
import java.io.InputStream

class ProgressInputStream(
    inputStream: InputStream,
    private val onProgress: (Long) -> Unit
) : FilterInputStream(inputStream) {
    override fun read(b: ByteArray?, off: Int, len: Int): Int {
        val bytesRead = super.read(b, off, len)

        if (bytesRead > 0) {
            onProgress(bytesRead.toLong())
        }

        return bytesRead
    }
}