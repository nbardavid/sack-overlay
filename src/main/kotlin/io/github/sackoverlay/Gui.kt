package io.github.sackoverlay

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import tech.thatgravyboat.skyblockapi.utils.text.Text

/** Sizes, theme colors and vanilla-style frames shared by every screen of the mod. */
object Gui {
    const val SLOT = 18
    const val INNER = 4
    const val HEADER_H = 13
    const val LOGICAL_W = 9 * SLOT + 2 * INNER
    const val PAD = 6
    const val GAP = 6
    const val SCROLLBAR = 4
    const val SEARCH_W = 120

    // Colors of the current theme (see Theme)
    val SLOT_BG get() = Theme.current.slotFill
    val EMPTY get() = Theme.current.empty
    val SELECTED get() = Theme.current.selected
    val HOVER get() = Theme.current.hover
    val ZERO_OVERLAY get() = Theme.current.zeroOverlay
    val DIM_OVERLAY get() = Theme.current.dimOverlay
    val TEXT get() = Theme.current.text
    val MUTED get() = Theme.current.muted
    val SHADOW get() = Theme.current.shadow

    fun tr(key: String): MutableComponent = Component.translatable("sackoverlay.ui.$key")

    /** AGRONOMY_SACK -> "Agronomy", with "×2" when several sacks of the family are equipped. */
    fun familyTitle(family: String, count: Int): Component {
        val name = family.removeSuffix("_SACK").split('_').joinToString(" ") { it.lowercase().replaceFirstChar(Char::titlecase) }
        return Text.of(if (count > 1) "$name ×$count" else name)
    }

    /** Panel width for this many page columns. */
    fun widthFor(columns: Int) = 2 * PAD + columns * LOGICAL_W + (columns - 1) * GAP + GAP + SCROLLBAR

    /** Number of columns that fit in this width, between 1 and the configured maximum. */
    fun columnsFor(width: Int) =
        ((width - widthFor(1)) / (LOGICAL_W + GAP) + 1).coerceIn(1, SackConfig.get.maxColumns.coerceIn(1, 4))

    /** A main panel: framed window with a 2 px raised border. */
    fun panel(graphics: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int) = frame(graphics, x, y, w, h, 2)

    /** A page card: same frame with a 1 px border. */
    fun card(graphics: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int) = frame(graphics, x, y, w, h, 1)

    /**
     * Vanilla-style frame: black outline with cut corners, then a [border] px raised edge
     * (light on the top and left, shadow on the bottom and right) around the fill.
     */
    fun frame(graphics: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, border: Int) {
        val t = Theme.current
        val r = x + w
        val b = y + h
        // Outline, one pixel in from each corner so the corners look cut
        graphics.fill(x + 2, y, r - 2, y + 1, t.frameOuter)
        graphics.fill(x + 2, b - 1, r - 2, b, t.frameOuter)
        graphics.fill(x, y + 2, x + 1, b - 2, t.frameOuter)
        graphics.fill(r - 1, y + 2, r, b - 2, t.frameOuter)
        graphics.fill(x + 1, y + 1, x + 2, y + 2, t.frameOuter)
        graphics.fill(r - 2, y + 1, r - 1, y + 2, t.frameOuter)
        graphics.fill(x + 1, b - 2, x + 2, b - 1, t.frameOuter)
        graphics.fill(r - 2, b - 2, r - 1, b - 1, t.frameOuter)
        // Raised edge: light top and left, shadow bottom and right
        graphics.fill(x + 2, y + 1, r - 1 - border, y + 1 + border, t.frameLight)
        graphics.fill(x + 1, y + 2, x + 1 + border, b - 1 - border, t.frameLight)
        graphics.fill(x + 1 + border, b - 1 - border, r - 2, b - 1, t.frameShadow)
        graphics.fill(r - 1 - border, y + 1 + border, r - 1, b - 2, t.frameShadow)
        // Corners where light meets shadow take the fill color, like vanilla
        graphics.fill(r - 1 - border, y + 1, r - 2, y + 1 + border, t.fill)
        graphics.fill(x + 1, b - 1 - border, x + 1 + border, b - 2, t.fill)
        graphics.fill(x + 1 + border, y + 1 + border, r - 1 - border, b - 1 - border, t.fill)
    }

    /**
     * Vanilla-style slot: dark top and left edge, light bottom and right edge.
     * Five rectangles: the fill covers the whole slot (corners included), the edges go on top.
     */
    fun slot(graphics: GuiGraphicsExtractor, x: Int, y: Int) {
        val t = Theme.current
        graphics.fill(x, y, x + SLOT, y + SLOT, t.slotFill)
        graphics.fill(x, y, x + SLOT - 1, y + 1, t.slotDark)
        graphics.fill(x, y + 1, x + 1, y + SLOT - 1, t.slotDark)
        graphics.fill(x + 1, y + SLOT - 1, x + SLOT, y + SLOT, t.slotLight)
        graphics.fill(x + SLOT - 1, y + 1, x + SLOT, y + SLOT - 1, t.slotLight)
    }

    fun border(graphics: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, color: Int) {
        graphics.fill(x, y, x + w, y + 1, color)
        graphics.fill(x, y + h - 1, x + w, y + h, color)
        graphics.fill(x, y, x + 1, y + h, color)
        graphics.fill(x + w - 1, y, x + w, y + h, color)
    }
}
