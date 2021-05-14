package by.mess.storage

import by.mess.model.Chat
import by.mess.model.Id
import by.mess.model.User

interface StorageInterface {
    fun getUser(id: Id): User
    fun getChat(id: Id): Chat

    fun addNewUser(user: User)
    fun addNewChat(chat: Chat)

    fun isUserPresent(id: Id): Boolean
    fun isChatPresent(id: Id): Boolean
}
