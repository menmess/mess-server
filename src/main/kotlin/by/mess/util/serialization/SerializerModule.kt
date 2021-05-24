package by.mess.util.serialization

import by.mess.event.AbstractEvent
import by.mess.event.MessengerEvent
import by.mess.event.NetworkEvent
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

object SerializerModule {
    private val module = SerializersModule {
        polymorphic(AbstractEvent::class) {
            subclass(MessengerEvent.NewMessageEvent::class)
            subclass(MessengerEvent.ChangeMessageStatusEvent::class)
            subclass(MessengerEvent.IntroductionRequest::class)
            subclass(MessengerEvent.IntroductionEvent::class)
            subclass(MessengerEvent.NewChatRequest::class)
            subclass(MessengerEvent.NewChatEvent::class)
            subclass(NetworkEvent.PeerListRequest::class)
            subclass(NetworkEvent.PeerListResponse::class)
            subclass(NetworkEvent.SendToPeerEvent::class)
            subclass(NetworkEvent.ConnectionOpenedEvent::class)
            subclass(NetworkEvent.ConnectionClosedEvent::class)
        }
    }
    val formatter: Json
        get() = Json { serializersModule = module }
}
