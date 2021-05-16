package by.mess.model.event

import by.mess.model.Id
import kotlinx.serialization.Serializable

@Serializable
abstract class AbstractEvent {
    abstract val producerId: Id
}
