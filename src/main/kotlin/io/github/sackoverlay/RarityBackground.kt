package io.github.sackoverlay

import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.item.ItemStack
import org.slf4j.LoggerFactory
import tech.thatgravyboat.skyblockapi.api.datatype.DataTypes
import tech.thatgravyboat.skyblockapi.api.datatype.getData
import java.lang.reflect.Method

/**
 * Background behind each page item, in the color of its rarity.
 * Skyblocker only draws its "item rarity backgrounds" in real menu slots, and the pages draw their items themselves,
 * so its public `ItemBackgroundManager.drawBackgrounds` is called here: same look, and its own style, opacity and
 * on/off settings apply. Without Skyblocker, a plain fill in the rarity color is drawn instead.
 */
object RarityBackground {

    private val logger = LoggerFactory.getLogger("SackOverlay")

    private var skyblocker: Method? = runCatching {
        if (!FabricLoader.getInstance().isModLoaded("skyblocker")) return@runCatching null
        Class.forName("de.hysky.skyblocker.skyblock.item.background.ItemBackgroundManager").getMethod(
            "drawBackgrounds", ItemStack::class.java, GuiGraphicsExtractor::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
        )
    }.getOrNull()

    private const val FALLBACK_ALPHA = 0x66

    /** Draws the background of an item drawn at (x, y). */
    fun draw(graphics: GuiGraphicsExtractor, stack: ItemStack, x: Int, y: Int) {
        if (!SackConfig.get.rarityBackgrounds) return
        skyblocker?.let { method ->
            try {
                method.invoke(null, stack, graphics, x, y)
                return
            } catch (e: Exception) {
                // A Skyblocker update changed it: fall back to our own fill from now on
                logger.warn("Could not draw Skyblocker's item background, using a plain one", e)
                skyblocker = null
            }
        }
        val rarity = stack.getData(DataTypes.RARITY) ?: return
        graphics.fill(x, y, x + 16, y + 16, (FALLBACK_ALPHA shl 24) or (rarity.color and 0xFFFFFF))
    }
}
