package io.github.sackoverlay

import io.github.sackoverlay.Gui.tr
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import tech.thatgravyboat.skyblockapi.helpers.McClient
import java.util.Locale

/**
 * Tooltip of a page item: a real item tooltip, built by the game from the item itself so other mods (prices...)
 * add their lines, like in Hypixel's sack menu. The "Stored:" line is replaced by the live amount.
 */
object SackTooltip {

    fun render(graphics: GuiGraphicsExtractor, font: Font, id: String, stack: ItemStack, family: String) {
        val menuStack = SackStore.menuStack(id)
        val lines = Screen.getTooltipFromItem(McClient.self, menuStack ?: stack).toMutableList()

        val owned = SackStore.count(id)
        val cap = SackStore.familyCapacity(family)
        val stored = Component.literal("Stored: ").withStyle(ChatFormatting.GRAY)
            .append(Component.literal(String.format(Locale.US, "%,d", owned)).withStyle(ChatFormatting.YELLOW))
            .append(Component.literal(cap?.let { "/" + Amounts.capacity(it) } ?: "").withStyle(ChatFormatting.GRAY))

        val storedIndex = lines.indexOfFirst { it.string.contains("Stored:") }
        if (storedIndex >= 0) {
            // Item seen in the sack menu: its lore already has Hypixel's lines, only the amount is refreshed
            lines[storedIndex] = stored
        } else {
            // Item from the repo: rebuild the sack part of Hypixel's tooltip under the name
            val familyName = Component.literal(Gui.familyTitle(family, 1).string + " Sack").withStyle(ChatFormatting.DARK_GRAY)
            lines.addAll(1, listOf(familyName, Component.empty(), stored, Component.empty()) + clickHints())
        }
        if (owned > 0) {
            val pickup = lines.indexOfFirst { it.string.contains("Click to pickup") }
            lines.add(if (pickup >= 0) pickup + 1 else lines.size, tr("hint.shift").withStyle(ChatFormatting.YELLOW))
        }

        val (mx, my) = McClient.mouse
        graphics.setTooltipForNextFrame(font, lines.map { it.visualOrderText }, DefaultTooltipPositioner.INSTANCE, mx.toInt(), my.toInt(), false)
    }

    /** Hypixel's wording for what each click does, following the settings. */
    private fun clickHints(): List<Component> {
        val config = SackConfig.get
        val right = if (config.rightClickStack == 64) tr("hint.right_stack") else Component.translatable("sackoverlay.ui.hint.right_amount", config.rightClickStack)
        val left = if (config.leftClickFillsInventory) tr("hint.left_pickup") else Component.translatable("sackoverlay.ui.hint.left_amount", config.leftClickAmount)
        return listOf(right.withStyle(ChatFormatting.AQUA), left.withStyle(ChatFormatting.YELLOW))
    }
}
