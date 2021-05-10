package by.mess.net

import io.ktor.network.sockets.*
import io.ktor.utils.io.*

class Connection(private val socket: Socket) {
    val input : ByteReadChannel = socket.openReadChannel()
    val output: ByteWriteChannel = socket.openWriteChannel(autoFlush = true)

    val isClosed: Boolean
        get() = socket.isClosed
}
