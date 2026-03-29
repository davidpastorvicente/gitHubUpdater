package com.davidpv.updatermanager.install

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

enum class InstallResultStatus {
    Success,
    Cancelled,
    Failed,
}

data class InstallResultEvent(
    val packageName: String,
    val status: InstallResultStatus,
    val cleanupFailed: Boolean = false,
)

object InstallResultEvents {
    private val _events = MutableSharedFlow<InstallResultEvent>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val events = _events.asSharedFlow()

    fun emit(event: InstallResultEvent) {
        _events.tryEmit(event)
    }
}
