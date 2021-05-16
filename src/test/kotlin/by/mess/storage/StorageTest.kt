package by.mess.storage

import by.mess.model.*
import org.junit.Test
import java.sql.Timestamp
import kotlin.test.assertEquals
import kotlin.test.assertFails

class StorageTest {
    private fun createDummyUser(id: Id): User {
        return User(id, "Zhora", "", "", true)
    }

    private fun createDummyMessage(id: Id, chatId: Id): Message {
        return Message(id, randomId(), chatId, Timestamp(123), MessageStatus.DELIVERED, null, "")
    }

    private fun createDummyStorage(users: List<User>, clientId: Id = randomId()): RAMStorage {
        val storage = RAMStorage(clientId)
        for (user in users) {
            storage.addNewUser(user)
        }

        return storage
    }

    @Test
    fun basicRAMStorageTest() {
        val users: List<User> = listOf(createDummyUser(123), createDummyUser(234))
        val storage = createDummyStorage(users)

        assertFails { storage.addNewUser(users[0]) }

        assert(storage.isUserPresent(users[0].id))
        assert(!storage.isUserPresent(42))

        assertEquals(users[1], storage.getUser(users[1].id))
        assertFails { storage.getUser(42) }

        storage.getUser(users[0].id).online = false
        assert(!storage.getUser(users[0].id).online)
    }

    @Test
    fun extendedRAMStorageTest() {
        val users: List<User> = listOf(createDummyUser(123), createDummyUser(234), createDummyUser(111))
        val storage = createDummyStorage(users)

        storage.removeUser(users[0].id)
        assert(!storage.isUserPresent(users[0].id))
        storage.removeUser(42)
        assert(!storage.isUserPresent(42))

        assertEquals(users.slice(1..2), storage.getOnlineUsers())
        storage.getUser(users[2].id).online = false
        assertEquals(users.slice(1..1), storage.getOnlineUsers())

        storage.addNewChat(Chat(1, mutableListOf(), Pair(1, 2)))
        val messages = listOf(createDummyMessage(1, 1), createDummyMessage(2, 1))
        storage.addNewMessage(messages[0])
        storage.addNewMessage(messages[1])

        assertFails { storage.addNewMessage(createDummyMessage(3, 42)) }
        assertEquals(messages.sortedBy { it.id }, storage.getMessagesFromChat(1).sortedBy { it.id })
        assertFails { storage.getMessagesFromChat(42) }
        assertEquals(messages[1], storage.getLastMessageFromChat(1))
    }

    @Test
    fun userToChatTest() {
        val users: List<User> = listOf(createDummyUser(123), createDummyUser(234), createDummyUser(111))
        val storage = createDummyStorage(users, 123)

        storage.addNewChat(Chat(1, mutableListOf(), Pair(123, 234)))

        assertEquals(1, storage.getChatForUser(users[1].id).id)
        assertFails { storage.getChatForUser(users[2].id) }
        assertFails { storage.getChatForUser(42) }
    }
}
