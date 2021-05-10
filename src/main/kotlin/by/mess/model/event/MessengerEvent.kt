package by.mess.model.event

import kotlinx.serialization.Serializable

import by.mess.model.*

@Serializable
sealed class MessengerEvent : AbstractEvent()

@Serializable
class NewMessageEvent(
    override val producerId: Id,
    val message: Message
) : MessengerEvent()

@Serializable
class ChangeMessageStatusEvent(
    override val producerId: Id,
    val messageId: Id,
    val newStatus: MessageStatus
) : MessengerEvent()

@Serializable
class IntroductionRequest(
    override val producerId: Id,
    val userId: Id
) : MessengerEvent()

@Serializable
class IntroductionEvent(
    override val producerId: Id,
    val user: User
) : MessengerEvent()

@Serializable
class NewChatEvent(
    override val producerId: Id,
    val chat: Chat
) : MessengerEvent()

@Serializable
class NewChatRequest(
    override val producerId: Id,
    val chatId: Id
) : MessengerEvent()
