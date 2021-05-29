package by.mess.p2p

import by.mess.model.Id
import by.mess.net.NetworkInterface
import by.mess.util.serialization.InetSocketAddressSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.net.InetSocketAddress

@Serializable
class Peer(
    val id: Id,
    @Serializable(with = InetSocketAddressSerializer::class)
    val address: InetSocketAddress,
    @Transient
    var connection: NetworkInterface? = null
) {
    val online: Boolean
        get() = connection?.isAlive?.get() ?: false
}
