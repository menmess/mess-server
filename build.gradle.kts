import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    application
    kotlin("jvm") version "1.4.32"
    kotlin("plugin.serialization") version "1.5.0"
}

group = "by"
version = "1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    testImplementation(kotlin("test-junit"))

    // Ktor
    implementation("io.ktor:ktor-server-core:1.5.4")
    implementation("io.ktor:ktor-server-netty:1.5.4")
    implementation("ch.qos.logback:logback-classic:1.2.3")

    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.2.0")
}

tasks.test {
    useJUnit()
}

tasks.withType<KotlinCompile>() {
    kotlinOptions.jvmTarget = "1.8"
}
