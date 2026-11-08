plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
    application
}

group = "org.proanima"
version = "0.1.0"

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    implementation("io.ktor:ktor-server-core-jvm:2.3.12")
    implementation("io.ktor:ktor-server-netty-jvm:2.3.12")
    implementation("io.ktor:ktor-server-html-builder-jvm:2.3.12")
    implementation("io.ktor:ktor-server-status-pages-jvm:2.3.12")
    implementation("io.ktor:ktor-server-caching-headers-jvm:2.3.12")
    implementation("io.ktor:ktor-server-compression-jvm:2.3.12")
    implementation("io.ktor:ktor-server-sessions-jvm:2.3.12")
    implementation("ch.qos.logback:logback-classic:1.5.6")

    implementation("org.postgresql:postgresql:42.7.4")
    implementation("com.zaxxer:HikariCP:5.1.0")

    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-tests-jvm:2.3.12")
    // The database code is tested on an in-memory H2 in PostgreSQL mode, so tests need no server and no network.
    testImplementation("com.h2database:h2:2.3.232")
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("org.proanima.shelter.ApplicationKt")
}
// A throwaway site with the admin panel for looking at it by hand: an in-memory database that starts with the
// content of data/, the password below, no connection to anything real.
tasks.register<JavaExec>("devAdmin") {
    group = "application"
    description = "Runs the site with the admin panel on an in-memory database at http://localhost:8095 (password: dev-password-123)."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("org.proanima.shelter.DevAdminServerKt")
    environment("PORT", "8095")
    environment("ADMIN_PASSWORD", "dev-password-123")
    environment("SESSION_SECRET", "dev-session-secret-not-for-production-use")
    environment("ADMIN_COOKIE_SECURE", "false")
}
