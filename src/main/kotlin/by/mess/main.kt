package by.mess

import by.mess.controller.Controller
import by.mess.model.Id
import by.mess.model.randomId
import io.ktor.application.*
import io.ktor.http.content.*
import io.ktor.routing.*
import io.ktor.websocket.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.io.File

@ExperimentalCoroutinesApi
fun Application.backendModule() {
    install(WebSockets) {
        pingPeriodMillis = environment.config.property("ktor.websocket.ping_period_ms").getString().toLong()
        timeoutMillis = environment.config.property("ktor.websocket.timeout_ms").getString().toLong()
    }
    var clientId: Id = try {
        File("id").readText().toLong()
    } catch (e: Exception) {
        randomId().also { File("id").writeText("$it") }
    }
    println("ClientId = $clientId")

    Controller(clientId, this)
}

fun Application.frontendModule() {
    routing {
        static("") {
            resources("web/mess-client/public")
            resources("web/mess-client/public/js")
            resources("web/mess-client/public/css")
            resources("web/mess-client/public/uploads")
            defaultResource("web/mess-client/public/index.html")
        }
        static("media") {
            files("media")
        }
    }
}

fun main(args: Array<String>) = io.ktor.server.cio.EngineMain.main(args)
