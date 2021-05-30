package by.mess.p2p

import by.mess.event.EventBus
import by.mess.event.NetworkEvent
import by.mess.model.Id
import by.mess.net.NetworkInterface
import by.mess.util.exception.ConnectionFailedException
import by.mess.util.logging.logger
import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.client.*
import io.ktor.client.features.websocket.*
import io.ktor.client.request.*
import io.ktor.features.origin
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import io.ktor.routing.routing
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.net.InetSocketAddress
import kotlin.coroutines.CoroutineContext
import io.ktor.client.features.websocket.WebSockets as ClientWebSockets

@ExperimentalCoroutinesApi
class DistributedNetwork(
    val selfId: Id,
    application: Application,
) {
    private val backendPort: Int

    val eventBus: EventBus = EventBus()
    private val networkEventHandler: NetworkEventHandler = NetworkEventHandler(this)
    private val eventHandlerScope: CoroutineScope = CoroutineScope(SupervisorJob())

    private val sharedDataContext: CoroutineContext = newSingleThreadContext("NetworkEventHandler")

    private val peers: MutableList<Peer> = mutableListOf()
    private val peerListenerScope: CoroutineScope = CoroutineScope(SupervisorJob())

    init {
        with(application) {
            backendPort = environment.config.property("ktor.deployment.port").getString().toInt()
            logger.info("Backend port = $backendPort")
            routing {
                webSocket("/network/{peerId}") {
                    handleConnection(this)
                }
            }
        }

        eventBus.events
            .onEach { networkEventHandler.handle(it) }
            .catch { cause -> logger.error("Exception occurred during handling event: $cause") }
            .flowOn(sharedDataContext)
            .launchIn(eventHandlerScope)
    }

    fun connect(peer: Peer) {
        try {
            runBlocking {
                if (findPeer(peer.id) != null) {
                    return@runBlocking
                }
                addPeer(peer)
                eventBus.events
                    .first { it is NetworkEvent.ConnectionOpenedEvent && it.producerId == peer.id }
                peer.connection!!.sendEvent(NetworkEvent.PeerListRequest(selfId))
            }
        } catch (e: Throwable) {
            logger.info("Connecting to peer ${peer.address.address} failed: $e")
            throw ConnectionFailedException()
        }
    }

    private suspend fun handleConnection(session: DefaultWebSocketServerSession) {
        logger.info("Receiving new connection...")
        val host: String = session.call.request.origin.host
        val port: Int = session.call.request.queryParameters["network_port"]?.toIntOrNull() ?: backendPort
        val peer = Peer(
            session.call.parameters["peerId"]!!.toLong(),
            InetSocketAddress(host, port),
            NetworkInterface(session)
        )
        logger.info("Connected peer id = '${peer.id}' from '${peer.address.address}'")
        addPeerAndListen(peer)
    }

    private suspend fun openConnection(peer: Peer) = withContext(sharedDataContext) {
        logger.info("Attempt to connect to peer at ${peer.address}")
        val client = HttpClient {
            install(ClientWebSockets)
        }
        client.ws(
            method = HttpMethod.Get,
            host = peer.address.hostName,
            port = peer.address.port,
            path = "/network/$selfId",
            request = { parameter("network_port", backendPort) }
        ) {
            val connection = NetworkInterface(this)
            peer.connection = connection
            eventBus.post(NetworkEvent.ConnectionOpenedEvent(peer.id))
            connection.broadcastEvents(eventBus)
        }
        logger.info("Connection to peer at ${peer.address} closed")
    }

    internal suspend fun findPeer(peerId: Id): Peer? = withContext(sharedDataContext) {
        peers.find { it.id == peerId }
    }

    internal suspend fun getPeerList(): List<Peer> = withContext(sharedDataContext) {
        peers.filter { it.online }.distinctBy { it.id }.toList()
    }

    internal suspend fun addPeer(peer: Peer) {
        peerListenerScope.launch {
            addPeerAndListen(peer)
        }
    }

    private suspend fun addPeerAndListen(peer: Peer) = withContext(sharedDataContext) {
        peerListenerScope.launch {
            try {
                findPeer(peer.id)?.let {
                    it.connection?.close(
                        CloseReason(
                            CloseReason.Codes.GOING_AWAY,
                            "Replaced with new connection"
                        )
                    )
                    removePeer(peer.id)
                }

                peers.add(peer)
                if (!peer.online) {
                    openConnection(peer)
                } else {
                    eventBus.post(NetworkEvent.ConnectionOpenedEvent(peer.id))
                    peer.connection!!.broadcastEvents(eventBus)
                }
            } catch (e: Throwable) {
                logger.warn("Uncaught exception $e")
                e.cause?.let { logger.warn("Caused by $it") }
            } finally {
                eventBus.post(
                    NetworkEvent.ConnectionClosedEvent(peer.id)
                )
            }
        }.join()
    }

    internal suspend fun removePeer(peerId: Id) = withContext(sharedDataContext) {
        peers.removeIf { !it.online && it.id == peerId }
    }

    companion object {
        private val logger by logger()
    }
}
