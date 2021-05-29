package by.mess.net

import by.mess.event.AbstractEvent
import by.mess.event.EventBus
import by.mess.util.serialization.SerializerModule
import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import java.util.concurrent.atomic.AtomicBoolean

class NetworkInterface(private val connection: WebSocketSession) {
    val isAlive: AtomicBoolean = AtomicBoolean(true)

    private val formatter = SerializerModule.formatter

    suspend fun sendEvent(event: AbstractEvent) {
        val contents: String = formatter.encodeToString(AbstractEvent.serializer(), event)
        connection.send(Frame.Text(contents))
    }

    suspend fun fetchEvent(): AbstractEvent? {
        try {
            val frame: Frame = connection.incoming.receive()
            if (frame.frameType != FrameType.TEXT) {
                return null
            }
            frame as Frame.Text
            return formatter.decodeFromString(AbstractEvent.serializer(), frame.readText())
        } catch (exc: ClosedReceiveChannelException) {
            close()
        } catch (exc: SerializationException) {
            close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Unable to parse the message"))
        }
        return null
    }

    suspend fun broadcastEvents(eventBus: EventBus) = withContext(Dispatchers.Default) {
        while (isAlive.get()) {
            val event: AbstractEvent? = fetchEvent()
            event?.let { eventBus.post(it) }
        }
    }

    suspend fun close(reason: CloseReason = CloseReason(CloseReason.Codes.NORMAL, "")) {
        isAlive.set(false)
        connection.close(reason)
    }
}
