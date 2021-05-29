package by.mess.controller

import by.mess.event.MessengerEvent
import by.mess.event.NetworkEvent
import by.mess.model.Chat
import by.mess.model.Id
import by.mess.model.Message
import by.mess.model.MessageStatus
import by.mess.model.User
import by.mess.p2p.DistributedNetwork
import by.mess.storage.RAMStorage
import by.mess.util.serialization.SerializerModule
import io.ktor.application.*
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.emitter.Emitter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking

@ExperimentalCoroutinesApi
class Controller(clientId: Id, app: Application) {
    private val storage = RAMStorage(clientId)
    private val formatter = SerializerModule.formatter
    private val frontConnection = IO.socket("localhost:8080")
    private val net = DistributedNetwork(clientId, app)
    private var self: User? = null

    private val connect = Emitter.Listener {
        frontConnection
            // arg: token: String
            .on("register_token", registerToken)
            // arg: username: String
            .on("register_username", registerUsername)
            // arg: message: JSON<model.Message>
            .on("send_message", sendMessage)
            // arg: chatId: Id (of target chat)
            .on("change_chat", changeChat)
            // arg: chatId: Id
            .on("read_messages", readMessages)
            // arg: userId: Id
            .on("create_chat", createChat)

        frontConnection.emit("require_token")
    }

    init {
        frontConnection.connect()
        frontConnection.on(Socket.EVENT_CONNECT, connect)
    }

    private val registerToken = Emitter.Listener {
        // @aleexf tbd
        net.connect([0] as String)
        net.eventBus.events.onEach { event -> when(event) {
            is MessengerEvent.NewMessageEvent -> handleNewMessageEvent(event)
            is MessengerEvent.ChangeMessageStatusEvent -> handleChangeMessageStatus(event)
            is MessengerEvent.IntroductionRequest -> handleIntroductionRequest(event)
            is MessengerEvent.IntroductionEvent -> handleIntroductionEvent(event)
            is MessengerEvent.NewChatEvent -> handleNewChatEvent(event)
            is MessengerEvent.NewChatRequest -> handleNewChatRequest(event)
            // @aleexf add new user? remove him if he goes offline?
        } }
    }

    private val registerUsername = Emitter.Listener {
        self = User(clientId, it[0] as String, true)
        // @uuustrica response needed?
    }

    private val sendMessage = Emitter.Listener {
        // @aleexf, @uuustrica id problem
        val message = formatter.decodeFromString(Message.serializer(), it[0] as String)
        storage.addNewMessage(message)
        // @uuustrica is it needed?
        // frontConnection.emit("send_message", it[0])
        runBlocking {
            net.eventBus.post(
                NetworkEvent.SendToPeerEvent(
                    clientId, storage.getChat(message.chatId).getOther(clientId),
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
        // tbd: send many messages at once
        for (message in storage.getMessagesFromChat(it[0] as Id)) {
            frontConnection.emit("send_message", formatter.encodeToString(Message.serializer(), message))
        }
    }

    private val readMessages = Emitter.Listener {
        for (messageId in storage.getChat(it[0] as Id).messages) {
            runBlocking {
                net.eventBus.post(
                    NetworkEvent.SendToPeerEvent(
                        clientId, storage.getChat(it[0] as Id).getOther(clientId),
                        MessengerEvent.ChangeMessageStatusEvent(clientId, messageId, MessageStatus.READ)
                    )
                )
            }
        }
    }

    private fun handleNewMessageEvent(event: MessengerEvent.NewMessageEvent) {
        if (!storage.isMessagePresent(event.message.id)) {
            storage.addNewMessage(event.message)
            frontConnection.emit("receive_message", event.message)
        }
    }

    private fun handleChangeMessageStatus(event: MessengerEvent.ChangeMessageStatusEvent) {
        storage.getMessage(event.messageId).status = event.newStatus
        if (event.newStatus == MessageStatus.READ) {
            // @aleexf, @uuustrica backend reads every message separately, frontend reads all messages in chat,
            // we need to unify it
            frontConnection.emit("read_messages", storage.getMessage(event.messageId).chatId)
        }
    }

    private fun handleIntroductionRequest(event: MessengerEvent.IntroductionRequest) {
        // @aleexf ???
    }

    private fun handleIntroductionEvent(event: MessengerEvent.IntroductionEvent) {
        // @allexf ???
    }

    private fun handleNewChatEvent(event: MessengerEvent.NewChatEvent) {
        storage.addNewChat(event.chat)
        frontConnection.emit("add_chat", formatter.encodeToString(Chat.serializer(), event.chat)) // @uuustrica args?
    }

    private fun handleNewChatRequest(event: MessengerEvent.NewChatRequest) {
        try {
            val chat = storage.getChatForUser(event.producerId)
            runBlocking {
                net.eventBus.post(
                    NetworkEvent.SendToPeerEvent(
                        storage.clientId, event.producerId,  MessengerEvent.NewChatEvent(storage.clientId, chat)
                    )
                )
            }
        } catch (e: NoSuchElementException) {
            // tbd: sync chat creation
        }
    }
}
