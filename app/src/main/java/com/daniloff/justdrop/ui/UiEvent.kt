package com.daniloff.justdrop.ui

sealed interface UiEvent {
    data class FileAlreadyAdded(val fileName: String) : UiEvent
    data class FileOpenError(val fileName: String) : UiEvent
    data class FileUploadError(val fileName: String) : UiEvent
}