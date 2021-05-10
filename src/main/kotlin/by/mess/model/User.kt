package by.mess.model

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: Id,
    val username: String,
    val name: String,
    val lastName: String,
    var online: Boolean
)
