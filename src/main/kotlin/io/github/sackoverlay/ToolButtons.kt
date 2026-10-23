package io.github.sackoverlay

import io.github.sackoverlay.Gui.card
import io.github.sackoverlay.Gui.tr
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import tech.thatgravyboat.skyblockapi.platform.showTooltip

/**
 * Small framed icon buttons in a row next to the inventory, like Enhanced Storage's tool buttons:
 * settings, then theme (each click switches to the next theme).
 */
class ToolButtons(openSettings: () -> Unit) {

    private class Tool(val icon: Identifier, val tooltip: () -> Component, val action: () -> Unit)

    private val tools = listOf(
        Tool(icon("gear"), { tr("settings") }, openSettings),
        Tool(icon("theme"), {
            val name = Component.translatable("sackoverlay.config.theme.${Theme.current.name.lowercase()}")
            Component.translatable("sackoverlay.ui.theme", name)
        }) {
            SackConfig.get.theme = Theme.entries[(Theme.current.ordinal + 1) % Theme.entries.size]
            SackConfig.save()
        },
    )

    private var x = 0
    private var y = 0
    private var hovered: Tool? = null

    /** Top left corner of the row. */
    fun place(x: Int, y: Int) {
        this.x = x
        this.y = y
    }

    fun render(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        hovered = null
        val theme = Theme.current
        tools.forEachIndexed { i, tool ->
            val bx = x + i * (SIZE + SPACING)
            val isHovered = mouseX in bx until bx + SIZE && mouseY in y until y + SIZE
            if (isHovered) hovered = tool
            card(graphics, bx, y, SIZE, SIZE)
            if (isHovered) graphics.fill(bx + 2, y + 2, bx + SIZE - 2, y + SIZE - 2, theme.hover)
            val tint = when {
                !isHovered -> theme.text
                theme.shadow -> 0xFFFFFFFF.toInt()
                else -> 0xFF000000.toInt()
            }
            val offset = (SIZE - ICON) / 2
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, tool.icon, bx + offset, y + offset, ICON, ICON, tint)
        }
    }

    fun renderTooltip(graphics: GuiGraphicsExtractor) {
        hovered?.let { graphics.showTooltip(it.tooltip()) }
    }

    /** Returns true when a button was clicked. */
    fun click(mouseX: Int, mouseY: Int): Boolean {
        tools.forEachIndexed { i, tool ->
            val bx = x + i * (SIZE + SPACING)
            if (mouseX in bx until bx + SIZE && mouseY in y until y + SIZE) {
                tool.action()
                return true
            }
        }
        return false
    }

    companion object {
        const val SIZE = 20
        private const val SPACING = 2
        private const val ICON = 16

        /** Width of the whole row, for the screen bounds reported to other mods. */
        const val ROW_WIDTH = 2 * SIZE + SPACING

        private fun icon(name: String) = Identifier.fromNamespaceAndPath("sackoverlay", name)
    }
}
