package com.mkulikov.taskb

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

fun main() {
    embeddedServer(factory = Netty, port = 8080, host = "0.0.0.0") {}.start(wait = true)
}
