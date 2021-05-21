package by.mess.event

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class EventBus {
    private val _events: MutableSharedFlow<AbstractEvent> =
        MutableSharedFlow(replay = 32, extraBufferCapacity = Channel.UNLIMITED)
    val events: SharedFlow<AbstractEvent> = _events.asSharedFlow()

    suspend fun post(event: AbstractEvent) = _events.emit(event)
}
