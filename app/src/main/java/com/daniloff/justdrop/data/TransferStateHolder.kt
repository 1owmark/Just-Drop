package com.daniloff.justdrop.data

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
}