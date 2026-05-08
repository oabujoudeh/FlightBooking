package com.flightbooking

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation

/**
 * Application entry point. Starts the Ktor server using the Netty engine.
 *
 * Engine configuration (port, host, etc.) is read from `application.conf`
 * on the classpath.
 *
 * @param args command-line arguments forwarded to [io.ktor.server.netty.EngineMain]
 */
fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain
        .main(args)
}

/**
 * Configures the main Ktor application module.
 *
 * Installs and wires up all top-level application features in order:
 * - [ContentNegotiation] with JSON serialization via kotlinx.serialization
 * - Pebble template engine (see [configureTemplates])
 * - HTTP routes (see [configureRouting])
 * - Session handling (see [configureSessions])
 */
fun Application.module() {
    install(ContentNegotiation) {
        json()
    }
    configureTemplates()
    configureRouting()
    configureSessions()
}
