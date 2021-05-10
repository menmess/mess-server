package by.mess.model

import java.sql.Timestamp
import kotlinx.serialization.Serializable

import by.mess.util.serialization.TimestampSerializer

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
