package by.mess.model.event

import kotlinx.serialization.Serializable

import by.mess.model.Id

@Serializable
abstract class AbstractEvent {
    abstract val producerId: Id
}
