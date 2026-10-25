import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm") version "2.4.0"
    id("net.fabricmc.fabric-loom") version "1.18-SNAPSHOT"
    // Bundles MoulConfig under our own package, as SkyHanni does, so it never clashes with another copy
    id("com.gradleup.shadow") version "9.4.1"
}

version = property("mod_version") as String
group = "io.github.sackoverlay"
base { archivesName.set("sack-overlay") }

repositories {
    // SkyblockAPI (tech.thatgravyboat) is published here, as for SkyOcean
    maven("https://maven.teamresourceful.com/repository/maven-public/") {
        content { includeGroupAndSubgroups("tech.thatgravyboat"); includeGroupAndSubgroups("me.owdding") }
    }
    // MoulConfig, the settings screen library of SkyHanni and NEU
    maven("https://maven.notenoughupdates.org/releases/") { content { includeGroupAndSubgroups("org.notenoughupdates") } }
    maven("https://maven.terraformersmc.com/releases") { content { includeGroup("com.terraformersmc") } }
    maven("https://repo.hypixel.net/repository/Hypixel") { content { includeGroup("net.hypixel") } }
    maven("https://api.modrinth.com/maven") { content { includeGroup("maven.modrinth") } }
    // DevAuth: Microsoft login in the dev client (runClient), to join Hypixel while developing
    maven("https://pkgs.dev.azure.com/djtheredstoner/DevAuth/_packaging/public/maven/v1") { content { includeGroup("me.djtheredstoner") } }
    mavenCentral()
}

// MoulConfig goes inside our jar, relocated (see releaseJar)
val shade: Configuration by configurations.creating

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

    // Settings screen, same library as SkyHanni; Mod Menu is optional
    implementation("org.notenoughupdates.moulconfig:modern-${property("minecraft_version")}:${property("moulconfig_version")}")
    shade("org.notenoughupdates.moulconfig:modern-${property("minecraft_version")}:${property("moulconfig_version")}") { isTransitive = false }
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
    )
    inputs.properties(props)
    filesMatching("fabric.mod.json") { expand(props) }
}

// The released jar (releaseJar below): our classes with the nested SkyblockAPI jar, plus MoulConfig moved under our package.
// The jar without MoulConfig stays out of build/libs, which the release workflow uploads
tasks.jar {
    archiveClassifier.set("dev")
    destinationDirectory.set(layout.buildDirectory.dir("devlibs"))
}
// A task of our own: the plugin's shadowJar also packs the raw resources, whose fabric.mod.json lacks the
// nested SkyblockAPI entry that Loom adds to the jar
val releaseJar = tasks.register<ShadowJar>("releaseJar") {
    archiveClassifier.set("")
    from(zipTree(tasks.jar.flatMap { it.archiveFile }))
    from(shade.elements.map { files -> files.map { zipTree(it) } }) {
        // MoulConfig's own metadata would replace ours; its access widener entries are copied into ours
        exclude("fabric.mod.json", "moulconfig.accesswidener", "META-INF/MANIFEST.MF")
        rename("^LICENSE$", "LICENSE_moulconfig")
    }
    relocate("io.github.notenoughupdates.moulconfig", "io.github.sackoverlay.deps.moulconfig")
    mergeServiceFiles()
}
tasks.shadowJar { enabled = false }
tasks.assemble { dependsOn(releaseJar) }
