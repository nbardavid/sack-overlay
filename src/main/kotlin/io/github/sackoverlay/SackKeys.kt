package io.github.sackoverlay

import com.mojang.blaze3d.platform.InputConstants
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.minecraft.client.KeyMapping
import net.minecraft.resources.Identifier
import tech.thatgravyboat.skyblockapi.api.location.LocationAPI
import tech.thatgravyboat.skyblockapi.helpers.McClient

/** Key bindings (set them in Controls) and the keys the screens react to. */
object SackKeys {

    private val CATEGORY: KeyMapping.Category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("sackoverlay", "main"))

    lateinit var openSacks: KeyMapping
        private set

    /** Flips sacks between their normal and enchanted side while our screens are open (Left Alt by default). */
    lateinit var toggleEnchanted: KeyMapping
        private set

    /** Held while clicking a page item: opens the amount window instead of withdrawing (Left Ctrl by default). */
    lateinit var chooseAmount: KeyMapping
        private set

    const val KEY_ESCAPE = 256
    const val KEY_RIGHT = 262
    const val KEY_LEFT = 263
    const val KEY_DOWN = 264
    const val KEY_UP = 265
    private const val KEY_LEFT_CONTROL = 341
    private const val KEY_LEFT_ALT = 342

    /** Whether the "choose amount" key is held. Key mappings are not updated while a screen is open, so the key is read directly. */
    fun chooseAmountHeld(): Boolean {
        val key = KeyMappingHelper.getBoundKeyOf(chooseAmount)
        return key.type == InputConstants.Type.KEYSYM && key != InputConstants.UNKNOWN &&
            InputConstants.isKeyDown(McClient.self.window, key.value)
    }

    fun register() {
        openSacks = KeyMappingHelper.registerKeyMapping(
            KeyMapping("key.sackoverlay.open_sacks", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.value, CATEGORY),
        )
        toggleEnchanted = KeyMappingHelper.registerKeyMapping(
            KeyMapping("key.sackoverlay.toggle_enchanted", InputConstants.Type.KEYSYM, KEY_LEFT_ALT, CATEGORY),
        )
        chooseAmount = KeyMappingHelper.registerKeyMapping(
            KeyMapping("key.sackoverlay.choose_amount", InputConstants.Type.KEYSYM, KEY_LEFT_CONTROL, CATEGORY),
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
