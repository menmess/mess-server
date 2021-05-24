package by.mess.model

import kotlinx.serialization.Serializable

@Serializable
data class Chat(
    val id: Id,
    val members: Pair<Id, Id>,
    val messages: MutableList<Id> = mutableListOf()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Chat) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
