package by.mess.model

import kotlinx.serialization.Serializable

@Serializable
data class Chat(
    val id: Id,
    val messages: MutableList<Id>,
    val members: MutableList<Id>
)
