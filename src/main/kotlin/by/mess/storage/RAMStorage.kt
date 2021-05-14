package by.mess.storage

import by.mess.model.Chat
import by.mess.model.Id
import by.mess.model.User

class RAMStorage : StorageInterface {
    private var users: HashMap<Id, User> = HashMap()
    private var chats: HashMap<Id, Chat> = HashMap()

    private fun <Type> getType(id: Id, map: HashMap<Id, Type>): Type {
        return map[id] ?: throw Exception("Type with id=$(id) not found")
    }

    override fun getUser(id: Id): User {
        return getType(id, users)
    }

    override fun getChat(id: Id): Chat {
        return getType(id, chats)
    }

    private fun <Type> addNewType(id: Id, instance: Type, map: HashMap<Id, Type>) {
        if (map.contains(id)) {
            throw Exception("Instance with id=$(id) already present")
        }
        map[id] = instance
    }

    override fun addNewUser(user: User) {
        addNewType(user.id, user, users)
    }

    override fun addNewChat(chat: Chat) {
        addNewType(chat.id, chat, chats)
    }

    override fun isUserPresent(id: Id): Boolean {
        return users.contains(id)
    }

    override fun isChatPresent(id: Id): Boolean {
        return chats.contains(id)
    }
}