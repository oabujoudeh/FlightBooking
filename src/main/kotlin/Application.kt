package com.flightbooking

import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*

/**
 * Starts the Ktor server.
 *
 * @param args command line arguments
 */
fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain
        .main(args)
}

/**
 * Sets up the main Ktor application features.
 *
 * It enables JSON support and loads templates, routes, and sessions.
 */
fun Application.module() {
    install(ContentNegotiation) {
        json()
    }
    configureTemplates()
    configureRouting()
    configureSessions()
}
