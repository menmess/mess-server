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

    private lateinit var frontConnection: WebSocketServerSession

    private val eventHandlerScope = CoroutineScope(SupervisorJob())
    private val frontendSenderScope = CoroutineScope(SupervisorJob())

    private fun sendToFront(map: Map<Any, Any>) {
        frontendSenderScope.launch {
            frontConnection.send(Frame.Text(JSONObject(map).toString()))
        }
    }

    private fun sendErrorToFront(errorMessage: String) {
        sendToFront(
            mapOf(
                "request" to "error_occurred",
                "message" to errorMessage
            )
        )
    }

    private fun sendToNetwork(receiverId: Id, event: MessengerEvent) {
        runBlocking {
            net.eventBus.post(NetworkEvent.SendToPeerEvent(clientId, receiverId, event))
        }
    }

    init {
        with(app) {
            routing {
                webSocket("/connection") {
                    frontConnection = this
                    sendToFront(
                        mapOf(
                            "request" to "require_registration",
                            "clientId" to clientId
                        )
                    )
                    for (frame in incoming) {
                        if (frame.frameType != FrameType.TEXT) {
                            continue
                        }
                        frame as Frame.Text
                        val json = JSONObject(frame.readText())
                        when (json.getString("request")) {
                            "register" -> register(json.getString("username"), json.getString("token"))
                            "send_message" -> sendMessage(
                                json.getLong("chatId"),
                                json.getString("text"),
                                json.getLong("time")
                            )
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
                sendToFront(
                    mapOf(
                        "request" to "invalid_token"
                    )
                )
                return
            } catch (e: ConnectionFailedException) {
                sendErrorToFront("Connection failed")
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
            .catch { cause -> sendErrorToFront("$cause") }
            .launchIn(eventHandlerScope)
    }

    private fun sendMessage(chatId: Id, text: String, time: Long) {
        val message: Message?
        try {
            message = Message(randomId(), clientId, chatId, Timestamp(time), MessageStatus.SENDING, null, text)
            storage.addNewMessage(message)
        } catch (e: Exception) {
            sendErrorToFront("Error creating message")
            return
        }
        sendToNetwork(
            storage.getChat(message.chatId).getOther(clientId),
            MessengerEvent.NewMessageEvent(clientId, message)
        )
    }

    private fun createChat(userId: Id) {
        sendToNetwork(userId, MessengerEvent.NewChatRequest(clientId))
    }

    private fun changeChat(chatId: Id) {
        for (message in storage.getMessagesFromChat(chatId)) {
            sendToFront(
                mapOf(
                    "request" to "receive_message",
                    "message" to message
                )
            )
        }
    }

    private fun readMessages(chatId: Id) {
        if (!storage.isChatPresent(chatId) || !storage.getChat(chatId).isMember(clientId)) {
            sendErrorToFront("Wrong chat")
            return
        }
        sendToNetwork(storage.getChat(chatId).getOther(clientId), MessengerEvent.ChatReadEvent(clientId, chatId))
    }

    private fun generateToken() {
        sendToFront(
            mapOf(
                "request" to "receive_token",
                "token" to net.getConnectionToken()
            )
        )
    }

    private fun handleNewMessageEvent(event: MessengerEvent.NewMessageEvent) {
        if (!storage.isMessagePresent(event.message.id)) {
            event.message.status = MessageStatus.DELIVERED
            storage.addNewMessage(event.message)
            sendToFront(
                mapOf(
                    "request" to "receive_message",
                    "message" to event.message
                )
            )
            sendToNetwork(
                event.producerId,
                MessengerEvent.ChangeMessageStatusEvent(
                    clientId,
                    event.message.id,
                    MessageStatus.DELIVERED
                )
            )
        }
    }

    private fun handleChangeMessageStatus(event: MessengerEvent.ChangeMessageStatusEvent) {
        if (storage.isMessagePresent(event.messageId)) {
            storage.getMessage(event.messageId).status = event.newStatus
        }
        // tbd: delivered messages?
    }

    private fun handleIntroductionRequest(event: MessengerEvent.IntroductionRequest) {
        if (event.userId == clientId) {
            sendToNetwork(event.producerId, MessengerEvent.IntroductionEvent(clientId, clientUser))
        }
    }

    private fun handleIntroductionEvent(event: MessengerEvent.IntroductionEvent) {
        if (!storage.isUserPresent(event.producerId)) {
            storage.addNewUser(event.user)
            sendToFront(
                mapOf(
                    "request" to "new_user",
                    "user" to event.user
                )
            )
        }
    }

    private fun handleNewChatEvent(event: MessengerEvent.NewChatEvent) {
        if (!storage.isChatPresent(event.chat.id)) {
            storage.addNewChat(event.chat)
            sendToFront(
                mapOf(
                    "request" to "add_chat",
                    "memberId" to event.chat.getOther(clientId),
                    "chatId" to event.chat.id
                )
            )
        } else {
            // tbd: sync chats
        }
    }

    private fun handleNewChatRequest(event: MessengerEvent.NewChatRequest) {
        try {
            val chat = storage.getChatForUser(event.producerId)
            sendToNetwork(event.producerId, MessengerEvent.NewChatEvent(clientId, chat))
        } catch (e: NoSuchElementException) {
            sendToNetwork(event.producerId, MessengerEvent.NoSuchChatEvent(clientId, event.producerId))
            // tbd: sync chats
        }
    }

    private fun handleChatReadEvent(event: MessengerEvent.ChatReadEvent) {
        if (storage.isChatPresent(event.chatId)) {
            for (message in storage.getMessagesFromChat(event.chatId)) {
                message.status = MessageStatus.READ
            }
            sendToFront(
                mapOf(
                    "request" to "read_chat",
                    "memberId" to storage.getChat(event.chatId).getOther(clientId)
                )
            )
        }
    }

    private fun handleNoSuchChatEvent(event: MessengerEvent.NoSuchChatEvent) {
        if (event.memberId == clientId) {
            var chatId = randomId()
            while (storage.isChatPresent(chatId)) {
                chatId = randomId()
            }
            val chat = Chat(chatId, clientId to event.producerId)
            storage.addNewChat(chat)
            sendToFront(
                mapOf(
                    "request" to "add_chat",
                    "memberId" to chat.getOther(clientId),
                    "chatId" to chatId
                )
            )
        }
    }

    private fun handleNewUserEvent(event: NetworkEvent.ConnectionOpenedEvent) {
        sendToNetwork(event.producerId, MessengerEvent.IntroductionRequest(clientId, event.producerId))
    }

    private fun handleRemoveUserEvent(event: NetworkEvent.ConnectionClosedEvent) {
        if (storage.isUserPresent(event.producerId)) {
            storage.getUser(event.producerId).online = false
            sendToFront(
                mapOf(
                    "request" to "offline_user",
                    "userId" to event.producerId
                )
            )
        }
    }

    private fun handlePeerList(event: NetworkEvent.PeerListResponse) {
        for (peer in event.peers) {
            sendToNetwork(peer.id, MessengerEvent.IntroductionRequest(clientId, peer.id))
        }
    }
}
