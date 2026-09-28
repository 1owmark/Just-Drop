package com.daniloff.justdrop.model

import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.Serializable

@Serializable
data class TransferRequest(
    val files: List<TransferRequestFile>
)

@Serializable
data class TransferRequestFile(
    val name: String,
    val size: Long
)

@Serializable
enum class TransferResponse {
    ACCEPTED,
    DECLINED
}

data class PendingTransferRequest(
    val request: TransferRequest,
    val response: CompletableDeferred<TransferResponse>
)

enum class TransferRequestState {
    IDLE,
    WAITING,
    ACCEPTED,
    DECLINED
}