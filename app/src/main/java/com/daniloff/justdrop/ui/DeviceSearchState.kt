package com.daniloff.justdrop.ui

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged

sealed interface DeviceSearchState {
    data object Searching : DeviceSearchState
    data object NotFound : DeviceSearchState
}