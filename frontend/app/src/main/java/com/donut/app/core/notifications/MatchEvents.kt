package com.donut.app.core.notifications

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Tells the open app that a match push arrived, so the match pop-up refreshes instead of polling.
 * Pushes arriving in a burst collapse into one event, since one refresh catches up on all of them.
 */
object MatchEvents {
    private val _events = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: SharedFlow<Unit> = _events.asSharedFlow()

    // Never suspends, so it is safe from the thread FCM delivers pushes on
    fun emit() {
        _events.tryEmit(Unit)
    }
}
