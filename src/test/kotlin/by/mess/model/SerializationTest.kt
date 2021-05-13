package by.mess.model

import by.mess.model.event.AbstractEvent
import by.mess.model.event.MessengerEvent
import by.mess.util.serialization.SerializerModule
import kotlinx.serialization.encodeToString
import org.junit.Test
import java.sql.Timestamp
import kotlin.test.assertEquals

class SerializationTest {

    @Test
    fun serializationTest() {
        val event = MessengerEvent.NewMessageEvent(
            42,
            Message(
                42,
                1337,
                Timestamp(0),
                MessageStatus.UNKNOWN,
                null,
                "Hello, world!"
            )
        )

        val formatter = SerializerModule.formatter

        val encodedValue: String = formatter.encodeToString(event as AbstractEvent)
        val decodedValue: AbstractEvent = formatter.decodeFromString(AbstractEvent.serializer(), encodedValue)

        assertEquals(event, decodedValue)
    }
}
