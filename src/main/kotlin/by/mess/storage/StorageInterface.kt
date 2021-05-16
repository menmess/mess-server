package by.mess.storage

import by.mess.model.Chat
import by.mess.model.Id
import by.mess.model.Message
import by.mess.model.User

interface StorageInterface {
    fun getUser(id: Id): User
    fun getChat(id: Id): Chat
    fun getMessage(id: Id): Message

    fun addNewUser(user: User)
    fun addNewChat(chat: Chat)
    fun addNewMessage(message: Message)

    fun isUserPresent(id: Id): Boolean
    fun isChatPresent(id: Id): Boolean
    fun isMessagePresent(id: Id): Boolean

    fun removeUser(id: Id)
    fun removeChat(id: Id)
    fun removeMessage(id: Id)

    fun getMessagesFromChat(chatId: Id): List<Message>
    fun getLastMessageFromChat(chatId: Id): Message
    fun getOnlineUsers(): List<User>
    fun getChatForUser(userId: Id): Chat
}
