package by.mess.storage

import by.mess.model.Id
import by.mess.model.User
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class StorageTest {
    fun createDummyUser(id: Id): User {
        return User(id, "Zhora", "", "", true)
    }

    @Test
    fun basicRAMStorageTest() {
        var storage = RAMStorage()
        val user1 = createDummyUser(123)
        val user2 = createDummyUser(234)

        storage.addNewUser(user1)
        storage.addNewUser(user2)

        assertFails { storage.addNewUser(user1) }

        assert(storage.isUserPresent(user1.id))
        assert(!storage.isUserPresent(42))

        assertEquals(user2, storage.getUser(user2.id))
        assertFails { storage.getUser(42)  }

        storage.getUser(user1.id).online = false
        assert(!storage.getUser(user1.id).online)
    }
}