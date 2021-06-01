package by.mess.controller

import by.mess.event.AbstractEvent
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
import by.mess.util.logging.logger
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

    private lateinit var frontConnection: WebSocketServerSession

    private val eventHandlerScope = CoroutineScope(SupervisorJob())
    private val frontendSenderScope = CoroutineScope(SupervisorJob())

    private val logger by logger()

    private fun sendToFront(map: Map<Any, Any>) {
        logger.info("Sending $map to front")
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

    private fun sendToNetwork(receiverId: Id, event: AbstractEvent) {
        val formatter = SerializerModule.formatter
        logger.info("Sending ${formatter.encodeToString(AbstractEvent.serializer(), event)} to $receiverId")
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
                        try {
                            if (frame.frameType != FrameType.TEXT) {
                                continue
                            }
                            frame as Frame.Text
                            val json = JSONObject(frame.readText())
                            logger.info("Handling frontend request ${frame.readText()}")
                            when (json.getString("request")) {
                                "register" -> register(
                                    json.getString("username"),
                                    json.getString("token").trimEnd('\n')
                                )
                                "send_message" -> sendMessage(
                                    json.getLong("chatId"),
                                    json.getString("text"),
                                    json.getLong("time"),
                                    json.getString("attachmentUrl")
                                )
                                "create_chat" -> createChat(json.getLong("userId"))
                                "change_chat" -> changeChat(json.getLong("chatId"))
                                "read_messages" -> readMessages(json.getLong("chatId"))
                                "generate_token" -> generateToken()
                            }
                        } catch (e: Exception) {
                            logger.error("$e, cause ${e.cause}")
                        }
                    }
                }
            }
        }
    }

    private fun register(username: String, token: String) {
        clientUser = User(clientId, username, true)
        storage.addNewUser(clientUser)
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
                sendErrorToFront("Connection failed, cause $e")
                return
            }
        }
        val formatter = SerializerModule.formatter
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
                else -> return@onEach
            }
            logger.info("Handling ${formatter.encodeToString(AbstractEvent.serializer(), event)}, clientId=$clientId")
        }
            .catch { cause -> sendErrorToFront("$cause") }
            .launchIn(eventHandlerScope)
    }

    private fun sendMessage(chatId: Id, text: String, time: Long, attachmentUrl: String) {
        val message: Message?
        try {
            message = Message(randomId(), clientId, chatId, Timestamp(time), MessageStatus.SENDING, attachmentUrl, text)
            storage.addNewMessage(message)
        } catch (e: Exception) {
            logger.error("$e, cause ${e.cause}")
            sendErrorToFront("Error creating message")
            return
        }

        if (attachmentUrl != "null") {
            val partnerId = storage.getChat(message.chatId).getOther(clientId)
            runBlocking {
                net.eventBus.post(
                    NetworkEvent.SendFileToPeerEvent(clientId, partnerId, attachmentUrl)
                )
            }
        }

        sendToNetwork(
            storage.getChat(message.chatId).getOther(clientId),
            MessengerEvent.NewMessageEvent(clientId, message)
        )
        val formatter = SerializerModule.formatter
        sendToFront(
            mapOf(
                "request" to "receive_message",
                "message" to formatter.encodeToString(Message.serializer(), message)
            )
        )
    }

    private fun createChat(userId: Id) {
        sendToNetwork(userId, MessengerEvent.NewChatRequest(clientId))
    }

    private fun changeChat(chatId: Id) {
        val formatter = SerializerModule.formatter
        for (message in storage.getMessagesFromChat(chatId)) {
            sendToFront(
                mapOf(
                    "request" to "receive_message",
                    "message" to formatter.encodeToString(Message.serializer(), message)
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
            val formatter = SerializerModule.formatter
            event.message.status = MessageStatus.DELIVERED
            if (!storage.isChatPresent(event.message.chatId)) {
                val chat = Chat(event.message.chatId, clientId to event.producerId)
                storage.addNewChat(chat)
                sendToFront(
                    mapOf(
                        "request" to "add_chat",
                        "memberId" to chat.getOther(clientId),
                        "chatId" to chat.id
                    )
                )
            }
            storage.addNewMessage(event.message)
            sendToFront(
                mapOf(
                    "request" to "receive_message",
                    "message" to formatter.encodeToString(Message.serializer(), event.message),
                    "attachmentUrl" to event.message.attachmentUrl.toString()
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
            val formatter = SerializerModule.formatter
            storage.addNewUser(event.user)
            sendToFront(
                mapOf(
                    "request" to "new_user",
                    "user" to formatter.encodeToString(User.serializer(), event.user)
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
        if (event.producerId != clientId) {
            sendToNetwork(event.producerId, MessengerEvent.IntroductionRequest(clientId, event.producerId))
        }
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
}
