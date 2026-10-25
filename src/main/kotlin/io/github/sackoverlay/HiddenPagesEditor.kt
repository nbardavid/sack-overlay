package io.github.sackoverlay

import io.github.notenoughupdates.moulconfig.GuiTextures
import io.github.notenoughupdates.moulconfig.common.RenderContext
import io.github.notenoughupdates.moulconfig.common.text.StructuredText
import io.github.notenoughupdates.moulconfig.gui.GuiOptionEditor
import io.github.notenoughupdates.moulconfig.gui.MouseEvent
import io.github.notenoughupdates.moulconfig.processor.ProcessedOption

/**
 * The "Hidden pages" list: one switch per sack page, in columns. Pages come from the sacks known to the mod,
 * so they can't be fixed config fields. The state is SackStore's, shared by all profiles.
 */
class HiddenPagesEditor(option: ProcessedOption) : GuiOptionEditor(option) {

    /** Put on the config field that shows this editor. */
    @Retention(AnnotationRetention.RUNTIME)
    @Target(AnnotationTarget.FIELD)
    annotation class Marker

    // With combined sacks, a sack and its enchanted sack share one page, hidden with the normal one
    private val pages: List<Pair<String, String>> = run {
        val enchanted = SackPageView.ENCHANTED
        val combine = SackConfig.get.combineEnchanted
        SackStore.families.keys
            .filter { !combine || !it.startsWith(enchanted) || it.removePrefix(enchanted) !in SackStore.families }
            .map { it to Gui.familyTitle(it, 1).string }.sortedBy { it.second } +
            ("OTHER" to Gui.tr("others").string)
    }

    private val rows get() = (pages.size + COLUMNS - 1) / COLUMNS

    override fun getHeight() = TOP + rows * ROW + 4

    override fun render(context: RenderContext, x: Int, y: Int, width: Int) {
        context.drawDarkRect(x, y, width, getHeight(), true)
        val font = context.minecraft.defaultFontRenderer
        context.drawStringScaledMaxWidth(option.description, font, x + 6, y + 6, true, width - 12, 0xc0c0c0)
        val columnW = (width - 12) / COLUMNS
        pages.forEachIndexed { i, (family, title) ->
            val cx = x + 6 + (i % COLUMNS) * columnW
            val cy = y + TOP + (i / COLUMNS) * ROW
            val hidden = SackStore.isHidden(family)
            context.drawTexturedRect(if (hidden) GuiTextures.TOGGLE_ON else GuiTextures.TOGGLE_OFF, cx.toFloat(), cy.toFloat(), SWITCH_W.toFloat(), SWITCH_H.toFloat())
            context.drawStringScaledMaxWidth(StructuredText.of(title), font, cx + SWITCH_W + 4, cy + 1, true, columnW - SWITCH_W - 8, if (hidden) 0xe0e0e0 else 0x909090)
        }
    }

    override fun mouseInput(x: Int, y: Int, width: Int, mouseX: Int, mouseY: Int, mouseEvent: MouseEvent): Boolean {
        if (mouseEvent !is MouseEvent.Click || !mouseEvent.mouseState || mouseEvent.mouseButton != 0) return false
        val columnW = (width - 12) / COLUMNS
        val col = (mouseX - x - 6) / columnW
        val row = (mouseY - y - TOP).floorDiv(ROW)
        if (mouseX < x + 6 || col !in 0 until COLUMNS || row !in 0 until rows) return false
        val (family) = pages.getOrNull(row * COLUMNS + col) ?: return false
        SackStore.setHidden(family, !SackStore.isHidden(family))
        return true
    }

    // The settings search also finds a page by its name
    override fun fulfillsSearch(word: String) = super.fulfillsSearch(word) || pages.any { it.second.lowercase().contains(word) }

    private companion object {
        const val COLUMNS = 3
        const val TOP = 22
        const val ROW = 12
        const val SWITCH_W = 18
        const val SWITCH_H = 9
    }
}
