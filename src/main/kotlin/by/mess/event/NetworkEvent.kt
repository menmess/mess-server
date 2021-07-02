package by.mess.event

import by.mess.model.Id
import by.mess.p2p.Peer
import kotlinx.serialization.Serializable

@Serializable
sealed class NetworkEvent : AbstractEvent() {
    @Serializable
    data class PeerListRequest(override val producerId: Id) : NetworkEvent()

    @Serializable
    data class PeerListResponse(
        override val producerId: Id,
        val peers: List<Peer>
    ) : NetworkEvent()

    @Serializable
    data class SendToPeerEvent(
        override val producerId: Id,
        val receiverId: Id,
        val message: AbstractEvent
    ) : NetworkEvent()

    @Serializable
    data class SendFileToPeerEvent(
        override val producerId: Id,
        val receiverId: Id,
        val filename: String
    ) : NetworkEvent()

    @Serializable
    data class ConnectionOpenedEvent(
        override val producerId: Id
    ) : NetworkEvent()

    @Serializable
    data class ConnectionClosedEvent(
        override val producerId: Id
    ) : NetworkEvent()
}
