package by.mess.model

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: Id,
    val username: String,
    val name: String,
    val lastName: String,
    var online: Boolean
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is User) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
