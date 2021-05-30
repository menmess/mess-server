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
import by.mess.util.exception.ConnectionFailedException
import by.mess.util.exception.InvalidTokenException
import by.mess.util.serialization.SerializerModule
import io.ktor.application.*
import io.ktor.http.cio.websocket.*
import io.ktor.routing.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.lang.Exception
import java.sql.Timestamp

@ExperimentalCoroutinesApi
class Controller(val clientId: Id, val app: Application) {
    private val storage = RAMStorage(clientId)
    private val net = DistributedNetwork(clientId, app)
    private lateinit var clientUser: User

    private val formatter = SerializerModule.formatter
    private lateinit var frontConnection: WebSocketServerSession

    private val eventHandlerScope = CoroutineScope(SupervisorJob())
    private val frontendSenderScope = CoroutineScope(SupervisorJob())
    // tbd: sync

    init {
        with(app) {
            routing {
                webSocket("/") {
                    frontConnection = this
                    send(Frame.Text(JSONObject(
                        mapOf(
                            "request" to "require_registration",
                            "clientId" to clientId
                        )).toString())
                    )
                    for (frame in incoming) {
                        if (frame.frameType != FrameType.TEXT) {
                            continue
                        }
                        frame as Frame.Text
                        var json = JSONObject(frame.readText())
                        when (json.getString("request")) {
                            "register" -> register(json.getString("username"), json.getString("token"))
                            "send_message" -> sendMessage(json.getLong("chatId"), json.getString("text"), json.getLong("time"))
                            "create_chat" -> createChat(json.getLong("userId"))
                            "change_chat" -> changeChat(json.getLong("chatId"))
                            "read_messages" -> readMessages(json.getLong("chatId"))
                            "generate_token" -> generateToken()
                        }
                    }
                }
            }
        }
    }

    private fun register(username: String, token: String) {
        clientUser = User(clientId, username, true)
        if (token != "") {
            try {
                net.connectToNetwork(token)
            } catch (e: InvalidTokenException) {
                frontendSenderScope.launch {
                    frontConnection.send(Frame.Text(JSONObject(
                        mapOf(
                            "request" to "invalid_token"
                        )
                    ).toString()))
                }
                return
            } catch (e: ConnectionFailedException) {
                frontendSenderScope.launch {
                    frontConnection.send(Frame.Text(JSONObject(
                        mapOf(
                            "request" to "error_occurred",
                            "message" to "Connection failed"
                        )
                    ).toString()))
                }
                return
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
            .catch { cause -> frontendSenderScope.launch {
                frontConnection.send(Frame.Text(JSONObject(
                    mapOf(
                        "request" to "error_occurred",
                        "message" to "$cause"
                    )
                ).toString()))
            } }
            .launchIn(eventHandlerScope)
    }

    private fun sendMessage(chatId: Id, text: String, time: Long) {
        val message: Message?
        try {
            message = Message(randomId(), clientId, chatId, Timestamp(time), MessageStatus.SENDING, null, text)
            storage.addNewMessage(message)
        } catch (e: Exception) {
            frontendSenderScope.launch {
                frontConnection.send(Frame.Text(JSONObject(
                    mapOf(
                        "request" to "error_occurred",
                        "message" to "Error creating message"
                    )
                ).toString()))
            }
            return
        }
        runBlocking {
            net.eventBus.post(
                NetworkEvent.SendToPeerEvent(
                    clientId, storage.getChat(message.chatId).getOther(clientId),
                    MessengerEvent.NewMessageEvent(clientId, message)
                )
            )
        }
    }

    private fun createChat(userId: Id) {
        runBlocking {
            net.eventBus.post(
                NetworkEvent.SendToPeerEvent(
                    clientId, userId,
                    MessengerEvent.NewChatRequest(clientId)
                )
            )
        }
    }

    private fun changeChat(chatId: Id) {
        for (message in storage.getMessagesFromChat(chatId)) {
            frontendSenderScope.launch {
                frontConnection.send(Frame.Text(JSONObject(
                    mapOf(
                        "request" to "receive_message",
                        "message" to message
                    )
                ).toString()))
            }
        }
    }

    private fun readMessages(chatId: Id) {
        if (!storage.isChatPresent(chatId) || !storage.getChat(chatId).isMember(clientId)) {
            frontendSenderScope.launch {
                frontConnection.send(Frame.Text(JSONObject(
                    mapOf(
                        "request" to "error_occurred",
                        "message" to "Wrong chat"
                    )
                ).toString()))
            }
            return
        }
        runBlocking {
            net.eventBus.post(
                NetworkEvent.SendToPeerEvent(
                    clientId, storage.getChat(chatId).getOther(clientId),
                    MessengerEvent.ChatReadEvent(clientId, chatId)
                )
            )
        }
    }

    private fun generateToken() {
        frontendSenderScope.launch {
            frontConnection.send(Frame.Text(JSONObject(
                mapOf(
                    "request" to "receive_token",
                    "token" to net.getConnectionToken()
                )
            ).toString()))
        }
    }

    private fun handleNewMessageEvent(event: MessengerEvent.NewMessageEvent) {
        if (!storage.isMessagePresent(event.message.id)) {
            event.message.status = MessageStatus.DELIVERED
            storage.addNewMessage(event.message)
            frontendSenderScope.launch {
                frontConnection.send(Frame.Text(JSONObject(
                    mapOf(
                        "request" to "receive_message",
                        "message" to event.message
                    )
                ).toString()))
            }
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
        if (storage.isMessagePresent(event.messageId)) {
            storage.getMessage(event.messageId).status = event.newStatus
        }
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
            frontendSenderScope.launch {
                frontConnection.send(Frame.Text(JSONObject(
                    mapOf(
                        "request" to "new_user",
                        "user" to event.user
                    )
                ).toString()))
            }
        }
    }

    private fun handleNewChatEvent(event: MessengerEvent.NewChatEvent) {
        if (!storage.isChatPresent(event.chat.id)) {
            storage.addNewChat(event.chat)
            frontendSenderScope.launch {
                frontConnection.send(Frame.Text(JSONObject(
                    mapOf(
                        "request" to "add_chat",
                        "memberId" to event.chat.getOther(storage.clientId),
                        "chatId" to event.chat.id
                    )
                ).toString()))
            }
        } else {
            // tbd: sync chats
        }
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
            // tbd: sync chats
        }
    }

    private fun handleChatReadEvent(event: MessengerEvent.ChatReadEvent) {
        if (storage.isChatPresent(event.chatId)) {
            for (message in storage.getMessagesFromChat(event.chatId)) {
                message.status = MessageStatus.READ
            }
            frontendSenderScope.launch {
                frontConnection.send(Frame.Text(JSONObject(
                    mapOf(
                        "request" to "read_chat",
                        "memberId" to storage.getChat(event.chatId).getOther(storage.clientId)
                    )
                ).toString()))
            }
        }
    }

    private fun handleNoSuchChatEvent(event: MessengerEvent.NoSuchChatEvent) {
        if (event.memberId == storage.clientId) {
            var chatId = randomId()
            while (storage.isChatPresent(chatId)) {
                chatId = randomId()
            }
            val chat = Chat(chatId, Pair(storage.clientId, event.producerId))
            storage.addNewChat(chat)
            frontendSenderScope.launch {
                frontConnection.send(Frame.Text(JSONObject(
                    mapOf(
                        "request" to "add_chat",
                        "memberId" to chat.getOther(storage.clientId),
                        "chatId" to chatId
                    )
                ).toString()))
            }
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
        if (storage.isUserPresent(event.producerId)) {
            storage.getUser(event.producerId).online = false
            frontendSenderScope.launch {
                frontConnection.send(Frame.Text(JSONObject(
                    mapOf(
                        "request" to "offline_user",
                        "userId" to event.producerId
                    )
                ).toString()))
            }
        }
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
