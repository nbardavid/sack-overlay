rootProject.name = "sack-overlay"

pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")
    }
}

plugins {
    // Downloads a JDK 25 automatically when the machine only has a JRE
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
