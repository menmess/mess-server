package by.mess.storage

import by.mess.model.Chat
import by.mess.model.Id
import by.mess.model.User
import java.lang.IllegalStateException

class RAMStorage(val clientId: Id) : StorageInterface {
    private var users: HashMap<Id, User> = HashMap()
    private var chats: HashMap<Id, Chat> = HashMap()

    private fun <Type> getInstanceFromStorage(id: Id, map: HashMap<Id, Type>): Type {
        return map[id] ?: throw NoSuchElementException("Type with id=$(id) not found")
    }

    override fun getUser(id: Id): User {
        return getInstanceFromStorage(id, users)
    }

    override fun getChat(id: Id): Chat {
        return getInstanceFromStorage(id, chats)
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

    override fun isUserPresent(id: Id): Boolean {
        return users.contains(id)
    }

    override fun isChatPresent(id: Id): Boolean {
        return chats.contains(id)
    }
}
