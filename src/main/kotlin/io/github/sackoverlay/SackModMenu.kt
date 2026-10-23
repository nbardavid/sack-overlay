package io.github.sackoverlay

import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi

/** Adds the "Config" button to Sack Overlay's entry in Mod Menu. Only loaded when Mod Menu is installed. */
class SackModMenu : ModMenuApi {
    override fun getModConfigScreenFactory(): ConfigScreenFactory<*> = ConfigScreenFactory { parent -> SackConfig.screen(parent) }
}
