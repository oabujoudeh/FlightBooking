package com.flightbooking

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.pebble.Pebble
import io.pebbletemplates.pebble.loader.ClasspathLoader

/**
 * Configures the Pebble templating engine for the application.
 *
 * Installs the [Pebble] plugin and sets up a [ClasspathLoader] with
 * `"templates"` as the root prefix, so template files are resolved
 * from the `resources/templates/` directory on the classpath.
 */
fun Application.configureTemplates() {
    install(Pebble) {
        loader(
            ClasspathLoader().apply {
                prefix = "templates"
            },
        )
    }
}
