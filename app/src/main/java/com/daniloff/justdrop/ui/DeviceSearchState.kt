package com.daniloff.justdrop.ui

sealed interface DeviceSearchState {
    data object Searching : DeviceSearchState
    data object NotFound : DeviceSearchState
}