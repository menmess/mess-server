package by.mess

import by.mess.event.MessengerEvent
import by.mess.event.NetworkEvent
import by.mess.model.Message
import by.mess.model.MessageStatus
import by.mess.model.randomId
import by.mess.p2p.DistributedNetwork
import by.mess.p2p.Peer
import com.typesafe.config.ConfigFactory
import io.ktor.application.*
import io.ktor.config.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.sql.Timestamp
import java.time.Instant

fun main(args: Array<String>) {
    val id = args[0].toLong()
    val port = args[1].toInt()
    lateinit var network: DistributedNetwork

    println("Creating server... (args.size = ${args.size})")

    val server = embeddedServer(
        CIO,
        environment = applicationEngineEnvironment {
            config = HoconApplicationConfig(ConfigFactory.load())

            module {
                install(WebSockets) {
                    pingPeriodMillis = config.property("ktor.websocket.ping_period_ms").getString().toLong()
                    timeoutMillis = config.property("ktor.websocket.timeout_ms").getString().toLong()
                }
                network = DistributedNetwork(id, this)
            }
            connector {
                this.host = "localhost"
                this.port = port
            }
        }
    )

    val backgroundScope = CoroutineScope(SupervisorJob())
    backgroundScope.launch(Dispatchers.Default) {
        println("Starting server...")
        launch(Dispatchers.Default) {
            println("Start..")
            server.start(wait = true)
        }
        launch(Dispatchers.Default) {
            delay(1000)

            println("connect_to_peer = ${args.size == 4}")
            if (args.size == 4) {
                println("Connecting to ${args[2]} at ${args[3]}...")
                val peer = Peer(args[2].toLong(), InetSocketAddress("localhost", args[3].toInt()))
                println("connect param = ${peer.address}")
                try {
                    network.connect(peer)
                } catch (e: Throwable) {
                    println("Connecting to peer really failed")
                }
                println("Connected to network successfully. Network size = ${network.getPeerList().size}")
            }
        }
    }

    runBlocking {
        val input = BufferedReader(InputStreamReader(System.`in`))

        while (true) {
            val text: String = input.readLine() ?: continue
            if (text.startsWith("/pm")) {
                try {
                    val commandArgs: List<String> = text.split(' ')
                    val destId: Long = commandArgs[1].toLong()
                    val message = MessengerEvent.NewMessageEvent(
                        network.selfId,
                        Message(
                            randomId(),
                            network.selfId,
                            destId xor network.selfId,
                            Timestamp.from(Instant.now()),
                            MessageStatus.UNKNOWN,
                            null,
                            commandArgs.drop(2).joinToString(" ")
                        )
                    )
                    network.eventBus.post(
                        NetworkEvent.SendToPeerEvent(network.selfId, destId, message)
                    )
                } catch (exc: Throwable) {
                    println("> Failed to parse your command: $exc")
                }
            } else {
                println("> Undefined command")
            }
        }
    }
}
