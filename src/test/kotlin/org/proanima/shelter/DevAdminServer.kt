package org.proanima.shelter

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

// Started by `./gradlew devAdmin`. H2 is only on the test classpath, which is why this lives here.
fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8095
    embeddedServer(
        factory = Netty,
        port = port,
        host = "127.0.0.1",
        module = { moduleWithDatabase("jdbc:h2:mem:dev;MODE=PostgreSQL;DB_CLOSE_DELAY=-1") }
    ).start(wait = true)
}
