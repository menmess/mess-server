package by.mess.storage

import by.mess.model.Chat
import by.mess.model.Id
import by.mess.model.Message
import by.mess.model.User
import java.lang.IllegalStateException
import kotlin.streams.toList

class RAMStorage(val clientId: Id) : StorageInterface {
    private var users: HashMap<Id, User> = HashMap()
    private var chats: HashMap<Id, Chat> = HashMap()
    private var messages: HashMap<Id, Message> = HashMap()

    private fun <Type> getInstanceFromStorage(id: Id, map: HashMap<Id, Type>): Type {
        return map[id] ?: throw NoSuchElementException("Type with id=$(id) not found")
    }

    override fun getUser(id: Id): User {
        return getInstanceFromStorage(id, users)
    }

    override fun getChat(id: Id): Chat {
        return getInstanceFromStorage(id, chats)
    }

    override fun getMessage(id: Id): Message {
        return getInstanceFromStorage(id, messages)
    }

    private fun <Type> addNewInstanceToStorage(id: Id, instance: Type, map: HashMap<Id, Type>) {
        if (map.contains(id)) {
            throw IllegalStateException("Instance with id=$(id) already present")
        }
        map[id] = instance
    }

    override fun addNewUser(user: User) {
        addNewInstanceToStorage(user.id, user, users)
    }

    override fun addNewChat(chat: Chat) {
        addNewInstanceToStorage(chat.id, chat, chats)
    }

    override fun addNewMessage(message: Message) {
        addNewInstanceToStorage(message.id, message, messages)
        chats[message.chatId]?.messages?.add(message.id)
            ?: throw IllegalStateException("Chat for the message with id=$(id) doesn't exist")
    }

    override fun isUserPresent(id: Id): Boolean {
        return users.contains(id)
    }

    override fun isChatPresent(id: Id): Boolean {
        return chats.contains(id)
    }

    override fun isMessagePresent(id: Id): Boolean {
        return messages.contains(id)
    }

    override fun removeUser(id: Id) {
        users.remove(id)
    }

    override fun removeChat(id: Id) {
        chats.remove(id)
    }

    override fun removeMessage(id: Id) {
        messages.remove(id)
    }

    override fun getMessagesFromChat(chatId: Id): List<Message> {
        if (!isChatPresent(chatId)) {
            throw NoSuchElementException("Type with id=$(chatId) not found")
        }
        return chats[chatId]!!.messages.parallelStream()
            .map { return@map messages[it] ?: throw NoSuchElementException("Type with id=$(it) not found") }
            .toList()
    }

    override fun getLastMessageFromChat(chatId: Id): Message {
        if (!isChatPresent(chatId)) {
            throw NoSuchElementException("Type with id=$(chatId) not found")
        }
        val messageId = chats[chatId]!!.messages.last()
        return messages[messageId] ?: throw NoSuchElementException("Type with id=$(messageId) not found")
    }

    override fun getOnlineUsers(): List<User> {
        return users.values.parallelStream()
            .filter { return@filter it.online }
            .toList()
    }
}
