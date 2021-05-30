package by.mess.controller

import by.mess.event.MessengerEvent
import by.mess.event.NetworkEvent
import by.mess.model.Chat
import by.mess.model.Id
import by.mess.model.Message
import by.mess.model.MessageStatus
import by.mess.model.User
import by.mess.model.randomId
import by.mess.p2p.DistributedNetwork
import by.mess.storage.RAMStorage
import by.mess.util.exception.InvalidTokenException
import by.mess.util.serialization.SerializerModule
import io.ktor.application.*
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.emitter.Emitter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import java.lang.Exception
import java.sql.Timestamp

@ExperimentalCoroutinesApi
class Controller(clientId: Id, app: Application) {
    private val storage = RAMStorage(clientId)
    private val formatter = SerializerModule.formatter
    private val frontConnection = IO.socket("localhost:8080")
    private val net = DistributedNetwork(clientId, app)
    private lateinit var clientUser: User
    private val eventHandlerScope = CoroutineScope(SupervisorJob())
    // tbd: sync

    private val connect = Emitter.Listener {
        frontConnection
            // arg: [username, token]: String[]
            .on("register", register)
            // arg: [chatId, text, time]: [Id, String, long]
            .on("send_message", sendMessage)
            // arg: chatId: Id (of target chat)
            .on("change_chat", changeChat)
            // arg: chatId: Id
            .on("read_messages", readMessages)
            // arg: userId: Id
            .on("create_chat", createChat)
            .on("generate_token", generateToken)

        frontConnection.emit("require_registration", storage.clientId)
    }

    init {
        frontConnection.connect()
        frontConnection.on(Socket.EVENT_CONNECT, connect)
    }

    private val register = Emitter.Listener {
        clientUser = User(clientId, it[0] as String, true)
        if (it[1] != "") {
            try {
                net.connectToNetwork(it[1] as String)
            } catch (e: InvalidTokenException) {
                frontConnection.emit("invalid_token")
            }
        }
        net.eventBus.events.onEach { event ->
            when (event) {
                is MessengerEvent.NewMessageEvent -> handleNewMessageEvent(event)
                is MessengerEvent.ChangeMessageStatusEvent -> handleChangeMessageStatus(event)
                is MessengerEvent.IntroductionRequest -> handleIntroductionRequest(event)
                is MessengerEvent.IntroductionEvent -> handleIntroductionEvent(event)
                is MessengerEvent.NewChatEvent -> handleNewChatEvent(event)
                is MessengerEvent.NewChatRequest -> handleNewChatRequest(event)
                is MessengerEvent.ChatReadEvent -> handleChatReadEvent(event)
                is MessengerEvent.NoSuchChatEvent -> handleNoSuchChatEvent(event)
                is NetworkEvent.ConnectionOpenedEvent -> handleNewUserEvent(event)
                is NetworkEvent.ConnectionClosedEvent -> handleRemoveUserEvent(event)
                is NetworkEvent.PeerListResponse -> handlePeerList(event)
            }
        }
            .catch { cause -> frontConnection.emit("error_occured", "$cause") }
            .launchIn(eventHandlerScope)
    }

    private val sendMessage = Emitter.Listener {
        var message: Message? = null
        try {
            message = Message(
                randomId(),
                clientId,
                it[0] as Id,
                Timestamp(it[2] as Long),
                MessageStatus.SENDING,
                null,
                it[1] as String
            )
            storage.addNewMessage(message)
        } catch (e: Exception) {
            frontConnection.emit("error_occured", "Error creating message")
        }
        runBlocking {
            net.eventBus.post(
                NetworkEvent.SendToPeerEvent(
                    clientId, storage.getChat(message!!.chatId).getOther(clientId),
                    MessengerEvent.NewMessageEvent(clientId, message)
                )
            )
        }
    }

    private val createChat = Emitter.Listener {
        runBlocking {
            net.eventBus.post(
                NetworkEvent.SendToPeerEvent(
                    clientId, it[0] as Id,
                    MessengerEvent.NewChatRequest(clientId)
                )
            )
        }
    }

    private val changeChat = Emitter.Listener {
        for (message in storage.getMessagesFromChat(it[0] as Id)) {
            frontConnection.emit("receive_message", formatter.encodeToString(Message.serializer(), message))
        }
    }

    private val readMessages = Emitter.Listener {
        val chatId = it[0] as Id
        for (messageId in storage.getChat(chatId).messages) {
            runBlocking {
                net.eventBus.post(
                    NetworkEvent.SendToPeerEvent(
                        clientId, storage.getChat(chatId).getOther(clientId),
                        MessengerEvent.ChatReadEvent(clientId, chatId)
                    )
                )
            }
        }
    }

    private val generateToken = Emitter.Listener {
        frontConnection.emit("receive_token", net.getConnectionToken())
    }

    private fun handleNewMessageEvent(event: MessengerEvent.NewMessageEvent) {
        if (!storage.isMessagePresent(event.message.id)) {
            event.message.status = MessageStatus.DELIVERED
            storage.addNewMessage(event.message)
            frontConnection.emit("receive_message", formatter.encodeToString(Message.serializer(), event.message))
            runBlocking {
                net.eventBus.post(
                    NetworkEvent.SendToPeerEvent(
                        storage.clientId, event.producerId,
                        MessengerEvent.ChangeMessageStatusEvent(
                            storage.clientId,
                            event.message.id,
                            MessageStatus.DELIVERED
                        )
                    )
                )
            }
        }
    }

    private fun handleChangeMessageStatus(event: MessengerEvent.ChangeMessageStatusEvent) {
        storage.getMessage(event.messageId).status = event.newStatus
        // tbd: delivered messages?
    }

    private fun handleIntroductionRequest(event: MessengerEvent.IntroductionRequest) {
        if (event.userId == storage.clientId) {
            runBlocking {
                net.eventBus.post(
                    NetworkEvent.SendToPeerEvent(
                        storage.clientId,
                        event.producerId,
                        MessengerEvent.IntroductionEvent(storage.clientId, clientUser)
                    )
                )
            }
        }
    }

    private fun handleIntroductionEvent(event: MessengerEvent.IntroductionEvent) {
        if (!storage.isUserPresent(event.producerId)) {
            storage.addNewUser(event.user)
            frontConnection.emit("new_user", formatter.encodeToString(User.serializer(), event.user))
        }
    }

    private fun handleNewChatEvent(event: MessengerEvent.NewChatEvent) {
        storage.addNewChat(event.chat)
        frontConnection.emit("add_chat", event.chat.getOther(storage.clientId), event.chat.id)
    }

    private fun handleNewChatRequest(event: MessengerEvent.NewChatRequest) {
        try {
            val chat = storage.getChatForUser(event.producerId)
            runBlocking {
                net.eventBus.post(
                    NetworkEvent.SendToPeerEvent(
                        storage.clientId, event.producerId, MessengerEvent.NewChatEvent(storage.clientId, chat)
                    )
                )
            }
        } catch (e: NoSuchElementException) {
            runBlocking {
                net.eventBus.post(
                    NetworkEvent.SendToPeerEvent(
                        storage.clientId,
                        event.producerId,
                        MessengerEvent.NoSuchChatEvent(storage.clientId, event.producerId)
                    )
                )
            }
        }
    }

    private fun handleChatReadEvent(event: MessengerEvent.ChatReadEvent) {
        for (message in storage.getMessagesFromChat(event.chatId)) {
            message.status = MessageStatus.READ
        }
        frontConnection.emit("read_chat", storage.getChat(event.chatId).getOther(storage.clientId))
    }

    private fun handleNoSuchChatEvent(event: MessengerEvent.NoSuchChatEvent) {
        if (event.memberId == storage.clientId) {
            storage.addNewChat(Chat(randomId(), Pair(storage.clientId, event.producerId)))
            frontConnection.emit("add_chat") // @uuustrica args?
        }
    }

    private fun handleNewUserEvent(event: NetworkEvent.ConnectionOpenedEvent) {
        runBlocking {
            net.eventBus.post(
                NetworkEvent.SendToPeerEvent(
                    storage.clientId,
                    event.producerId,
                    MessengerEvent.IntroductionRequest(storage.clientId, event.producerId)
                )
            )
        }
    }

    private fun handleRemoveUserEvent(event: NetworkEvent.ConnectionClosedEvent) {
        storage.getUser(event.producerId).online = false
        frontConnection.emit("offline_user", event.producerId)
    }

    private fun handlePeerList(event: NetworkEvent.PeerListResponse) {
        for (peer in event.peers) {
            runBlocking {
                net.eventBus.post(
                    NetworkEvent.SendToPeerEvent(
                        storage.clientId,
                        peer.id,
                        MessengerEvent.IntroductionRequest(storage.clientId, peer.id)
                    )
                )
            }
        }
    }
}
