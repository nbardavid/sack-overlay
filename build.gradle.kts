import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.4.0"
    id("net.fabricmc.fabric-loom") version "1.18-SNAPSHOT"
}

version = property("mod_version") as String
group = "io.github.sackoverlay"
base { archivesName.set("sack-overlay") }

repositories {
    // SkyblockAPI (tech.thatgravyboat) is published here, as for SkyOcean
    maven("https://maven.teamresourceful.com/repository/maven-public/") {
        content { includeGroupAndSubgroups("tech.thatgravyboat"); includeGroupAndSubgroups("me.owdding") }
    }
    maven("https://maven.isxander.dev/releases") { content { includeGroupAndSubgroups("dev.isxander") } }
    // YACL's JSON5 parser
    maven("https://maven.quiltmc.org/repository/release") { content { includeGroupAndSubgroups("org.quiltmc") } }
    maven("https://maven.terraformersmc.com/releases") { content { includeGroup("com.terraformersmc") } }
    maven("https://repo.hypixel.net/repository/Hypixel") { content { includeGroup("net.hypixel") } }
    maven("https://api.modrinth.com/maven") { content { includeGroup("maven.modrinth") } }
    // DevAuth: Microsoft login in the dev client (runClient), to join Hypixel while developing
    maven("https://pkgs.dev.azure.com/djtheredstoner/DevAuth/_packaging/public/maven/v1") { content { includeGroup("me.djtheredstoner") } }
    mavenCentral()
}

val sbapiCapability = "tech.thatgravyboat:skyblock-api-${property("minecraft_version")}"

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    implementation("net.fabricmc:fabric-loader:${property("fabric_loader_version")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")
    implementation("net.fabricmc:fabric-language-kotlin:${property("fabric_kotlin_version")}")

    // SkyblockAPI drags in its own copy of DevAuth; ours below is the only one, dev client only
    implementation("tech.thatgravyboat:skyblock-api:${property("skyblockapi_version")}") {
        capabilities { requireCapability(sbapiCapability) }
        exclude(group = "me.djtheredstoner")
    }
    include("tech.thatgravyboat:skyblock-api:${property("skyblockapi_version")}") {
        capabilities { requireCapability(sbapiCapability) }
        exclude(group = "me.djtheredstoner")
    }
    runtimeOnly("me.djtheredstoner:DevAuth-fabric:${property("devauth_version")}")

    // Settings screen, same library as Skyblocker; Mod Menu is optional
    implementation("dev.isxander:yet-another-config-lib:${property("yacl_version")}")
    compileOnly("com.terraformersmc:modmenu:${property("modmenu_version")}")

    // Needed by SkyblockAPI at runtime (Hypixel's official mod API)
    runtimeOnly("maven.modrinth:hypixel-mod-api:${property("hypixel_modapi_version")}")
}

// Hot reload for the dev client: JetBrains Runtime + HotswapAgent, used only when ../tools has them.
// Then `gradlew classes` while the game runs swaps the changed classes in (mixins still need a restart).
val hotswapAgent = file("../tools/hotswap-agent.jar")
val hotReload = hotswapAgent.exists()

loom {
    // Lets the screen move the real slots (Slot.x / Slot.y)
    accessWidenerPath.set(file("src/main/resources/sackoverlay.accesswidener"))

    runs.named("client") {
        // "main" is DevAuth's default Microsoft account; the first launch asks the player to log in through the browser
        jvmArguments.addAll("-Ddevauth.enabled=true", "-Ddevauth.account=main")
        if (hotReload) {
            jvmArguments.addAll(
                "-XX:+AllowEnhancedClassRedefinition",
                "-XX:HotswapAgent=external",
                "-javaagent:${hotswapAgent.absolutePath}=autoHotswap=true",
                // Fabric isolates game classes; this exposes the agent so its hooks can be called from them
                "-Dfabric.systemLibraries=${hotswapAgent.absolutePath}",
                "--add-opens=java.base/java.lang=ALL-UNNAMED",
                "--add-opens=java.base/java.io=ALL-UNNAMED",
                "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
                "--add-opens=java.base/jdk.internal.loader=ALL-UNNAMED",
                "--add-opens=java.desktop/java.beans=ALL-UNNAMED",
            )
        }
    }
}

if (hotReload) {
    // The JetBrains Runtime is found through org.gradle.java.installations.paths in ~/.gradle/gradle.properties
    tasks.named<JavaExec>("runClient") {
        javaLauncher.set(
            javaToolchains.launcherFor {
                languageVersion.set(JavaLanguageVersion.of(25))
                vendor.set(JvmVendorSpec.JETBRAINS)
            },
        )
    }
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_25)
        optIn.add("kotlin.time.ExperimentalTime")
    }
}

tasks.processResources {
    val props = mapOf(
        "version" to project.version,
        "minecraft" to project.property("minecraft_version"),
        "fabric_kotlin" to project.property("fabric_kotlin_version"),
        "sbapi" to project.property("skyblockapi_version"),
        "yacl" to (project.property("yacl_version") as String).substringBefore('+'),
    )
    inputs.properties(props)
    filesMatching("fabric.mod.json") { expand(props) }
}
