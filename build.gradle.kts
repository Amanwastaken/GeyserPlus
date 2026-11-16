plugins {
    id("java")
}

group = "shieldoffhand"
version = "1.0.0"

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

repositories {
    mavenCentral()
    maven("https://repo.opencollab.dev/main/")
    maven("https://repo.opencollab.dev/maven-snapshots/")
}

dependencies {
    // Geyser internals (packet translators, sessions) – provided by Geyser at runtime
    compileOnly("org.geysermc.geyser:core:2.12.1-SNAPSHOT")
}

tasks.processResources {
    filesMatching("extension.yml") {
        expand("version" to project.version)
    }
}

tasks.jar {
    archiveFileName.set("ShieldOffhand.jar")
}
