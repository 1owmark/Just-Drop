package com.daniloff.justdrop.model

enum class TransferStatus {
    WAITING,
    SENDING,
    SUCCESS,
    ERROR
}

data class TransferFile(
    val file: SelectedFile,
    val status: TransferStatus = TransferStatus.WAITING,
    val progress: Float = 0f,
    val speed: Long = 0L
)