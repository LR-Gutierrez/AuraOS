package com.beta.auraos.data.local

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class SyncEvent(
    val changesApplied: Int,
    val hints: List<String>?
)

object SyncEventBus {

    private val _events = MutableSharedFlow<SyncEvent>(extraBufferCapacity = 1, replay = 0)
    val events: SharedFlow<SyncEvent> = _events.asSharedFlow()

    fun emit(changesApplied: Int, hints: List<String>?) {
        _events.tryEmit(SyncEvent(changesApplied, hints))
    }
}
