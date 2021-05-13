package by.mess.model

import by.mess.util.serialization.TimestampSerializer
import kotlinx.serialization.Serializable
import java.sql.Timestamp

@Serializable
data class Message(
    val authorId: Id,
    val chatId: Id,
    @Serializable(with = TimestampSerializer::class)
    val sent: Timestamp,
    var status: MessageStatus,
    val mediaId: Id?,
    val text: String
)
