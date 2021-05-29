import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    application
    kotlin("jvm") version "1.5.0"
    kotlin("plugin.serialization") version "1.5.0"
}

group = "by"
version = "1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib", "1.5.0"))
    testImplementation(kotlin("test-junit"))

    // Ktor
    val ktor_version = "1.5.4"
    implementation("io.ktor:ktor-websockets:$ktor_version")
    implementation("io.ktor:ktor-client-websockets:$ktor_version")
    implementation("io.ktor:ktor-server-core:$ktor_version")
    implementation("io.ktor:ktor-server-cio:$ktor_version")
    implementation("ch.qos.logback:logback-classic:1.2.3")

    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.2.0")

    // Logging
    implementation("org.slf4j:slf4j-log4j12:1.7.29")
}

application {
    mainClass.set("io.ktor.server.cio.EngineMain")
}

tasks.test {
    useJUnit()
}

tasks.withType<KotlinCompile>() {
    kotlinOptions.jvmTarget = "1.8"
}
