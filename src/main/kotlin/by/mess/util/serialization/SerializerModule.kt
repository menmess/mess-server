package by.mess.util.serialization

import kotlinx.serialization.modules.*

import by.mess.model.event.*
import kotlinx.serialization.json.Json

object SerializerModule {
    private val module = SerializersModule {
        polymorphic(AbstractEvent::class) {
            polymorphic(MessengerEvent::class)
        }
    }
    val formatter: Json
        get() = Json { serializersModule = module }
}
