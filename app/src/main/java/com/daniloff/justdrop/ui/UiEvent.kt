package com.daniloff.justdrop.ui

sealed interface UiEvent {
    data class FileAlreadyAdded(val fileName: String): UiEvent
}