package by.mess.p2p

import kotlinx.serialization.json.Json
import java.util.*

internal object TokenManager {
    @JvmStatic
    fun getToken(peer: Peer): String {
        val formatter = Json {}
        val contents = formatter.encodeToString(Peer.serializer(), peer)
        return Base64.getEncoder().encodeToString(contents.encodeToByteArray())
    }

    @JvmStatic
    fun decodeToken(token: String): Peer {
        val formatter = Json {}
        val contents = String(Base64.getDecoder().decode(token))
        return formatter.decodeFromString(Peer.serializer(), contents)
    }
}
