package com.daniloff.justdrop.data

import com.daniloff.justdrop.model.IncomingTransfer
import com.daniloff.justdrop.model.PendingTransferRequest
import com.daniloff.justdrop.model.SelectedFile
import com.daniloff.justdrop.model.TransferFile
import com.daniloff.justdrop.model.TransferStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object TransferStateHolder {

    private val _transferFiles =
        MutableStateFlow<List<TransferFile>>(emptyList())

    val transferFiles = _transferFiles.asStateFlow()

    private val _transferRequest =
        MutableStateFlow<PendingTransferRequest?>(null)

    val transferRequest =
        _transferRequest.asStateFlow()

    private val _incomingTransfer =
        MutableStateFlow<IncomingTransfer?>(null)

    val incomingTransfer =
        _incomingTransfer.asStateFlow()

    private val _serverPort =
        MutableStateFlow<Int?>(null)

    val serverPort =
        _serverPort.asStateFlow()

    fun setFiles(files: List<TransferFile>) {
        _transferFiles.value = files
    }

    fun updateFile(
        targetFile: SelectedFile,
        status: TransferStatus,
        progress: Float? = null
    ) {
        _transferFiles.update { files ->
            files.map {
                if (it.file == targetFile) {
                    it.copy(
                        status = status,
                        progress = progress ?: it.progress
                    )
                } else {
                    it
                }
            }
        }
    }

    fun setIncomingRequest(
        request: PendingTransferRequest
    ) {
        _transferRequest.value = request
    }

    fun updateIncomingTransfer(
        update: (IncomingTransfer?) -> IncomingTransfer?
    ) {
        _incomingTransfer.update(update)
    }

    fun setServerPort(port: Int) {
        _serverPort.value = port
    }

    fun setIncomingTransfer(
        transfer: IncomingTransfer?
    ) {
        _incomingTransfer.value = transfer
    }

    fun clearIncomingRequest() {
        _transferRequest.value = null
    }
}