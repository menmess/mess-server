package by.mess.storage

import by.mess.model.Chat
import by.mess.model.Id
import by.mess.model.Message
import by.mess.model.MessageStatus
import by.mess.model.User
import by.mess.model.randomId
import org.junit.Test
import java.sql.Timestamp
import kotlin.test.assertEquals
import kotlin.test.assertFails

class StorageTest {
    private fun createDummyUser(id: Id): User {
        return User(id, "Zhora",  true)
    }

    private fun createDummyMessage(id: Id, userId: Id, chatId: Id): Message {
        return Message(id, userId, chatId, Timestamp(123), MessageStatus.DELIVERED, null, "")
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

        storage.addNewChat(Chat(1, Pair(storage.clientId, 2)))
        val messages = listOf(createDummyMessage(1, users[2].id, 1), createDummyMessage(2, users[1].id, 1))
        storage.addNewMessage(messages[0])
        storage.addNewMessage(messages[1])

        assertEquals(messages, storage.getMessagesFromChat(1))
        assertEquals(messages[1], storage.getLastMessageFromChat(1))
        assertFails { storage.addNewMessage(createDummyMessage(3, 42, 1)) }
        assertFails { storage.addNewMessage(createDummyMessage(3, users[1].id, 42)) }
        assertFails { storage.getMessagesFromChat(42) }
    }

    @Test
    fun userToChatTest() {
        val users: List<User> = listOf(createDummyUser(123), createDummyUser(234), createDummyUser(111))
        val storage = createDummyStorage(users, 123)

        storage.addNewChat(Chat(1, Pair(123, 234)))

        assertEquals(1, storage.getChatForUser(users[1].id).id)
        assertFails { storage.getChatForUser(users[2].id) }
        assertFails { storage.getChatForUser(42) }
    }
}
