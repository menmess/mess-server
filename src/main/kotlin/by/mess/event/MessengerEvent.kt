package by.mess.event

import by.mess.model.Chat
import by.mess.model.Id
import by.mess.model.Message
import by.mess.model.MessageStatus
import by.mess.model.User
import kotlinx.serialization.Serializable

@Serializable
sealed class MessengerEvent : AbstractEvent() {
    @Serializable
    data class NewMessageEvent(
        override val producerId: Id,
        val message: Message
    ) : MessengerEvent()

    @Serializable
    data class ChangeMessageStatusEvent(
        override val producerId: Id,
        val messageId: Id,
        val newStatus: MessageStatus
    ) : MessengerEvent()

    @Serializable
    data class IntroductionRequest(
        override val producerId: Id,
        val userId: Id
    ) : MessengerEvent()

    @Serializable
    data class IntroductionEvent(
        override val producerId: Id,
        val user: User
    ) : MessengerEvent()

    @Serializable
    data class NewChatEvent(
        override val producerId: Id,
        val chat: Chat
    ) : MessengerEvent()

    @Serializable
    data class NewChatRequest(
        override val producerId: Id,
        val chatId: Id
    ) : MessengerEvent()
}
