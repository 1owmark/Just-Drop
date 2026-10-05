package com.daniloff.justdrop.model

data class IncomingTransfer(
    val totalFiles: Int,
    val totalBytes: Long,
    val completedFiles: Int = 0,
    val receivedBytes: Long = 0L,
    val isFinished: Boolean = false,
    val isCancelled: Boolean = false
)
