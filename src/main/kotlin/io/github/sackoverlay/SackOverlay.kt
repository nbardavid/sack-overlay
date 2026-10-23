package io.github.sackoverlay

import com.mojang.brigadier.arguments.StringArgumentType
import net.fabricmc.api.ClientModInitializer
import tech.thatgravyboat.skyblockapi.api.SkyBlockAPI
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription
import tech.thatgravyboat.skyblockapi.api.events.misc.RegisterCommandsEvent
import tech.thatgravyboat.skyblockapi.api.location.LocationAPI
import tech.thatgravyboat.skyblockapi.helpers.McClient

/**
 * Entry point. The mod only reads what the game already shows:
 * - amounts come from SacksAPI (SkyblockAPI), like SkyOcean;
 * - Sack of Sacks and sack contents come from the menus the player opens.
 * The only things ever sent to the server are /gfs on an item click and /sacks when the player asks for it.
 */
object SackOverlay : ClientModInitializer {

    override fun onInitializeClient() {
        SackConfig.load()
        SackStore.load()
        SackKeys.register()
        SkyBlockAPI.eventBus.register(SackStore)
        SkyBlockAPI.eventBus.register(this)
    }

    @Subscription
    fun onRegisterCommands(event: RegisterCommandsEvent) {
        // Client-side commands; only "search" may send /sacks, like typing it yourself
        event.register("sackoverlay") {
            callback { McClient.setScreenAsync { SackOverlayScreen() } }
            thenCallback("config") { McClient.setScreenAsync { SackConfig.screen(null) } }
            then("search query", StringArgumentType.greedyString()) {
                callback {
                    SackPageView.pendingQuery = getArgument("query", String::class.java)
                    if (SackConfig.get.replaceSackScreen && LocationAPI.isOnSkyBlock) {
                        McClient.sendCommand("sacks")
                    } else {
                        McClient.setScreenAsync { SackOverlayScreen() }
                    }
                }
            }
        }
    }
}
