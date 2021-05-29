package by.mess.model

import by.mess.event.AbstractEvent
import by.mess.event.MessengerEvent
import by.mess.util.serialization.SerializerModule
import kotlinx.serialization.encodeToString
import org.junit.Test
import java.sql.Timestamp
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SerializationTest {

    @Test
    fun serializationTest() {
        val event = MessengerEvent.NewMessageEvent(
            42,
            Message(
                randomId(),
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

        assertTrue { decodedValue is MessengerEvent.NewMessageEvent }
        for (field in event.javaClass.fields) {
            assertEquals(field.get(event), field.get(decodedValue))
        }
    }
}
