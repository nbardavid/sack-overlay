package io.github.sackoverlay

import com.mojang.blaze3d.platform.InputConstants
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.minecraft.client.KeyMapping
import net.minecraft.resources.Identifier
import tech.thatgravyboat.skyblockapi.api.location.LocationAPI
import tech.thatgravyboat.skyblockapi.helpers.McClient

/** Key bindings (unbound by default, set them in Controls) and the keys the screens react to. */
object SackKeys {

    private val CATEGORY: KeyMapping.Category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("sackoverlay", "main"))

    lateinit var openSacks: KeyMapping
        private set
    lateinit var searchHovered: KeyMapping
        private set

    const val KEY_ESCAPE = 256
    const val KEY_RIGHT = 262
    const val KEY_LEFT = 263
    const val KEY_DOWN = 264
    const val KEY_UP = 265

    fun register() {
        openSacks = KeyMappingHelper.registerKeyMapping(
            KeyMapping("key.sackoverlay.open_sacks", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.value, CATEGORY),
        )
        searchHovered = KeyMappingHelper.registerKeyMapping(
            KeyMapping("key.sackoverlay.search_hovered", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.value, CATEGORY),
        )

        // Key presses only count while no screen is open, so this never fires while typing in chat or a menu
        ClientTickEvents.END_CLIENT_TICK.register {
            while (openSacks.consumeClick()) {
                // Same as typing /sacks; it is a SkyBlock command, so nothing is sent elsewhere
                if (LocationAPI.isOnSkyBlock) McClient.sendCommand("sacks")
            }
        }
    }
}
