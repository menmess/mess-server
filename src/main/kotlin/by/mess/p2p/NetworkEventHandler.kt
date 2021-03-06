package by.mess.p2p

import by.mess.event.AbstractEvent
import by.mess.event.NetworkEvent
import by.mess.util.logging.logger
import by.mess.util.serialization.SerializerModule
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.utils.io.streams.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import java.io.File
import java.lang.IllegalArgumentException

@ExperimentalCoroutinesApi
class NetworkEventHandler(
    private val network: DistributedNetwork,
) {
    companion object {
        private val logger by logger()
        private val formatter = SerializerModule.formatter
    }

    suspend fun handle(event: AbstractEvent) {
        logger.debug("Handling ${formatter.encodeToString(AbstractEvent.serializer(), event)}")
        when (event) {
            !is NetworkEvent -> return
            is NetworkEvent.PeerListRequest -> handlePeerListRequest(event)
            is NetworkEvent.PeerListResponse -> handlePeerListResponse(event)
            is NetworkEvent.SendToPeerEvent -> handleSendToPeerEvent(event)
            is NetworkEvent.SendFileToPeerEvent -> handleSendFileToPeerEvent(event)
            is NetworkEvent.ConnectionClosedEvent -> handleConnectionClosedEvent(event)
        }
    }

    private suspend fun handlePeerListRequest(event: NetworkEvent.PeerListRequest) {
        val peerListResponse = NetworkEvent.PeerListResponse(
            network.selfId,
            network.getPeerList()
        )
        network.eventBus.post(
            NetworkEvent.SendToPeerEvent(
                network.selfId,
                event.producerId,
                peerListResponse
            )
        )
    }

    private suspend fun handlePeerListResponse(event: NetworkEvent.PeerListResponse) {
        event.peers.filter { it.id != network.selfId }.forEach { network.addPeer(it) }
    }

    private suspend fun handleSendToPeerEvent(event: NetworkEvent.SendToPeerEvent) {
        val peer: Peer = network.findPeer(event.receiverId)
            ?: throw IllegalArgumentException("Id not found")
        if (!peer.online) {
            logger.debug("Failed to send message to peer ${peer.address}: peer offline")
        }
        try {
            peer.connection!!.sendEvent(event.message)
        } catch (cause: Throwable) {
            logger.debug("Failed to send message to peer ${peer.address}")
        }
    }

    private suspend fun handleSendFileToPeerEvent(event: NetworkEvent.SendFileToPeerEvent) {
        val peer: Peer? = network.findPeer(event.receiverId)
        if (peer == null || !peer.online) {
            return
        }
        delay(1000)
        try {
            val file = File("media/${event.filename}")
            network.httpClient.submitFormWithBinaryData(
                url = "http://" + peer.address.toString() + "/upload",
                formData = formData {
                    append(
                        "file", InputProvider { file.inputStream().asInput() },
                        Headers.build {
                            append(HttpHeaders.ContentType, ContentType.defaultForFile(file))
                            append(HttpHeaders.ContentDisposition, "filename=${event.filename}")
                        }
                    )
                }
            ) {
                parameter("filename", event.filename)
            }
        } catch (cause: Throwable) {
            logger.info("Failed to send file to peer ${peer.address}: $cause")
        }
    }

    private suspend fun handleConnectionClosedEvent(event: NetworkEvent.ConnectionClosedEvent) {
        network.removeOfflinePeer(event.producerId)
    }
}
