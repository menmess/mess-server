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
        if (javaClass != other?.javaClass) return false
        other as User
        if (id != other.id) return false
        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}
