package com.example.com

import io.ktor.server.application.*
import io.ktor.server.pebble.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.server.http.content.*

fun Application.configureRouting() {
    routing {
        static("/static") {
            resources("static")
        }

        get("/") {
            call.respondTemplate("booking.peb", emptyMap<String, Any>())
        }
    }
}