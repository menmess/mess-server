package by.mess

import by.mess.controller.Controller
import by.mess.model.randomId
import io.ktor.application.*
import io.ktor.http.content.*
import io.ktor.routing.*
import io.ktor.websocket.*
import kotlinx.coroutines.ExperimentalCoroutinesApi

@ExperimentalCoroutinesApi
fun Application.backendModule() {
    install(WebSockets) {
        pingPeriodMillis = environment.config.property("ktor.websocket.ping_period_ms").getString().toLong()
        timeoutMillis = environment.config.property("ktor.websocket.timeout_ms").getString().toLong()
    }
    Controller(randomId(), this)
}

fun Application.frontendModule() {
    routing {
        static("/") {
            resources("web/mess-client/public")
            resources("web/mess-client/public/js")
            resources("web/mess-client/public/css")
            resources("web/mess-client/public/uploads")
            defaultResource("web/mess-client/public/index.html")
        }
    }
}

fun main(args: Array<String>) = io.ktor.server.cio.EngineMain.main(args)
