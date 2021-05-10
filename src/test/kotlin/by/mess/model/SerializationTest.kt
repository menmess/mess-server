package by.mess.model

import java.sql.Timestamp
import kotlin.test.assertEquals
import kotlinx.serialization.encodeToString

import org.junit.*

import by.mess.util.serialization.SerializerModule
import by.mess.model.event.NewMessageEvent

class SerializationTest {

    @Test
    fun serializationTest() {
        val event = NewMessageEvent(
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

        val encodedValue: String = formatter.encodeToString(event)
        val decodedValue: NewMessageEvent = formatter.decodeFromString(NewMessageEvent.serializer(), encodedValue)

        for (field in decodedValue.javaClass.fields) {
            assertEquals(field.get(event), field.get(decodedValue))
        }
    }

}
