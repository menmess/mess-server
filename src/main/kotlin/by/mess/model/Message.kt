package by.mess.model

import by.mess.util.serialization.TimestampSerializer
import kotlinx.serialization.Serializable
import java.sql.Timestamp

@Serializable
data class Message(
    val id: Id,
    val authorId: Id,
    val chatId: Id,
    @Serializable(with = TimestampSerializer::class)
    val sent: Timestamp,
    var status: MessageStatus,
    val mediaId: Id?,
    val text: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Message
        if (id != other.id) return false
        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}
