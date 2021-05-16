package by.mess.model

import kotlinx.serialization.Serializable

@Serializable
data class Chat(
    val id: Id,
    val messages: MutableList<Id>,
    val members: Pair<Id, Id>
) {
    constructor(id: Id, members: Pair<Id, Id>) : this(id, mutableListOf(), members)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Chat) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
