package by.mess.model

import kotlinx.serialization.Serializable

@Serializable
data class Chat(
    val id: Id,
    val messages: MutableList<Id>,
    val members: MutableList<Id>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Chat
        if (id != other.id) return false
        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}
