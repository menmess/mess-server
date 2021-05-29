package by.mess.util.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.net.InetSocketAddress

class InetSocketAddressSerializer : KSerializer<InetSocketAddress> {
    @Serializable
    @SerialName("SocketAddress")
    private data class SocketAddressSurrogate(val host: String, val port: Int)

    override val descriptor: SerialDescriptor = SocketAddressSurrogate.serializer().descriptor

    override fun serialize(encoder: Encoder, value: InetSocketAddress) {
        val surrogate = SocketAddressSurrogate(value.hostName, value.port)
        encoder.encodeSerializableValue(SocketAddressSurrogate.serializer(), surrogate)
    }

    override fun deserialize(decoder: Decoder): InetSocketAddress {
        val surrogate = decoder.decodeSerializableValue(SocketAddressSurrogate.serializer())
        return InetSocketAddress.createUnresolved(surrogate.host, surrogate.port)
    }
}
