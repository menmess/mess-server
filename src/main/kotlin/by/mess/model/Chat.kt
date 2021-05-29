package by.mess.model

import kotlinx.serialization.Serializable
import java.lang.IllegalArgumentException

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

    fun getOther(id: Id) = when (id) {
        members.first -> members.second
        members.second -> members.first
        else -> throw IllegalArgumentException("One of chat's members must be id ($id)")
    }
}
