package by.mess.storage

import by.mess.model.Chat
import by.mess.model.Id
import by.mess.model.Message
import by.mess.model.User
import java.lang.IllegalArgumentException
import java.util.concurrent.ConcurrentHashMap
import kotlin.streams.toList

class RAMStorage(val clientId: Id) : StorageInterface {
    var users: ConcurrentHashMap<Id, User> = ConcurrentHashMap()
    private var chats: ConcurrentHashMap<Id, Chat> = ConcurrentHashMap()
    private var messages: ConcurrentHashMap<Id, Message> = ConcurrentHashMap()
    private var userToChat: ConcurrentHashMap<Id, Id> = ConcurrentHashMap()

    private fun throwNoSuchElementException(missingId: Id, typeName: String = "Type"): Nothing =
        throw NoSuchElementException("$typeName with id=$missingId not found")

    private fun <Type> getInstanceFromStorage(id: Id, map: ConcurrentHashMap<Id, Type>): Type =
        map[id] ?: throwNoSuchElementException(id)

    override fun getUser(id: Id): User = getInstanceFromStorage(id, users)

    override fun getChat(id: Id): Chat = getInstanceFromStorage(id, chats)

    override fun getMessage(id: Id): Message = getInstanceFromStorage(id, messages)

    private fun <Type> addNewInstanceToStorage(id: Id, instance: Type, map: ConcurrentHashMap<Id, Type>) {
        if (map.containsKey(id)) {
            throw IllegalArgumentException("Instance with id=$id already present")
        }
        map[id] = instance
    }

    override fun addNewUser(user: User) = addNewInstanceToStorage(user.id, user, users)

    override fun addNewChat(chat: Chat) {
        addNewInstanceToStorage(chat.id, chat, chats)
        addNewInstanceToStorage(chat.getOther(clientId), chat.id, userToChat)
    }

    override fun addNewMessage(message: Message) {
        if (!isUserPresent(message.authorId)) throw IllegalArgumentException("Message's author doesn't exist")
        addNewInstanceToStorage(message.id, message, messages)
        chats[message.chatId]?.messages?.add(message.id)
            ?: throw IllegalArgumentException("Chat for the message with id=${message.id} doesn't exist")
    }

    override fun isUserPresent(id: Id): Boolean = users.containsKey(id)

    override fun isChatPresent(id: Id): Boolean = chats.containsKey(id)

    override fun isMessagePresent(id: Id): Boolean = messages.containsKey(id)

    override fun removeUser(id: Id) {
        users.remove(id)
        chats.remove(userToChat[id])
        userToChat.remove(id)
    }

    override fun removeChat(id: Id) {
        chats.remove(id)
    }

    override fun removeMessage(id: Id) {
        chats[messages[id]?.chatId]?.messages?.remove(id)
        messages.remove(id)
    }

    override fun getMessagesFromChat(chatId: Id): List<Message> {
        if (!isChatPresent(chatId)) throwNoSuchElementException(chatId, "Chat")
        return chats[chatId]!!.messages.parallelStream()
            .map { messages[it] ?: throwNoSuchElementException(it, "Message") }
            .toList()
    }

    override fun getLastMessageFromChat(chatId: Id): Message {
        if (!isChatPresent(chatId)) throwNoSuchElementException(chatId, "Chat")
        val messageId = chats[chatId]!!.messages.last()
        return messages[messageId] ?: throwNoSuchElementException(messageId, "Message")
    }

    override fun getOnlineUsers(): List<User> {
        return users.values.parallelStream()
            .filter { it.online }
            .toList()
    }

    override fun getChatForUser(userId: Id): Chat {
        val chatId = getInstanceFromStorage(userId, userToChat)
        return chats[chatId] ?: throwNoSuchElementException(chatId, "Chat")
    }
}
