val logbackVersion = property("logback_version") as String

plugins {
    kotlin("jvm") version "2.3.0"
    kotlin("plugin.serialization") version "2.3.0"
    id("io.ktor.plugin") version "3.4.0"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
}

group = "com.example.com"
version = "0.0.1"

application {
    mainClass = "io.ktor.server.netty.EngineMain"
}

kotlin {
    jvmToolchain(21)
}

ktlint {
    verbose.set(true)
    outputToConsole.set(true)
    ignoreFailures.set(false)
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    ignoreFailures = true
}

dependencies {
    implementation("io.ktor:ktor-server-core-jvm")
    implementation("io.ktor:ktor-server-netty")
    implementation("io.ktor:ktor-server-config-yaml")
    implementation("io.ktor:ktor-server-pebble")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("org.xerial:sqlite-jdbc:3.45.1.0")
    implementation("io.ktor:ktor-server-core")
    implementation("org.mindrot:jbcrypt:0.4")

    implementation("io.ktor:ktor-server-content-negotiation")
    implementation("io.ktor:ktor-serialization-kotlinx-json")

    // Sessions
    implementation("io.ktor:ktor-server-sessions")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // email
    implementation("com.sun.mail:jakarta.mail:2.0.1")

    // charts
    implementation("org.knowm.xchart:xchart:3.8.8")

    // Testing
    testImplementation("io.ktor:ktor-server-test-host")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
}