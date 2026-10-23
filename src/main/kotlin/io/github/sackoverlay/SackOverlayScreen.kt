package io.github.sackoverlay

import io.github.sackoverlay.SackPageView.Companion.EMPTY
import io.github.sackoverlay.SackPageView.Companion.GAP
import io.github.sackoverlay.SackPageView.Companion.HEADER_H
import io.github.sackoverlay.SackPageView.Companion.INNER
import io.github.sackoverlay.SackPageView.Companion.LOGICAL_W
import io.github.sackoverlay.SackPageView.Companion.MUTED
import io.github.sackoverlay.SackPageView.Companion.PAD
import io.github.sackoverlay.SackPageView.Companion.SEARCH_W
import io.github.sackoverlay.SackPageView.Companion.SHADOW
import io.github.sackoverlay.SackPageView.Companion.SLOT
import io.github.sackoverlay.SackPageView.Companion.TEXT
import io.github.sackoverlay.SackPageView.Companion.border
import io.github.sackoverlay.SackPageView.Companion.panel
import io.github.sackoverlay.SackPageView.Companion.slot
import io.github.sackoverlay.SackPageView.Companion.tr
import io.github.sackoverlay.SackPageView.Companion.widthFor
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import tech.thatgravyboat.skyblockapi.helpers.McClient
import tech.thatgravyboat.skyblockapi.platform.drawString

/**
 * Read-only view outside the menu (/sackoverlay): the pages, and below them the Sack of Sacks
 * as it was last seen. Nothing here clicks anything on the server, except withdrawing with /gfs.
 */
class SackOverlayScreen : Screen(Component.translatable("sackoverlay.ui.title")) {

    private lateinit var search: EditBox
    private lateinit var tools: ToolButtons
    private lateinit var view: SackPageView
    private lateinit var prompt: AmountPrompt
    private val stripHits = mutableListOf<Pair<IntArray, String>>()

    private val panelW get() = widthFor(SackPageView.columnsFor(width - 16))
    private val panelX get() = (width - panelW) / 2
    private val panelY get() = 8
    private val stripH get() = HEADER_H + ((SackStore.sackSlots.size.coerceAtLeast(1) + 8) / 9) * SLOT + INNER
    private val bottom get() = height - 8

    override fun init() {
        search = EditBox(font, panelX + panelW - PAD - SEARCH_W, panelY + 4, SEARCH_W, 12, tr("search"))
        search.setHint(tr("search"))
        SearchInput.setup(search)
        addRenderableWidget(search)
        tools = ToolButtons { McClient.setScreen(SackConfig.screen(this)) }
        view = SackPageView(font) { search.value }
        prompt = AmountPrompt(font, view)
    }

    override fun isPauseScreen() = false

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        val stripY = bottom - stripH
        view.render(graphics, panelX, panelY, panelW, stripY - GAP - panelY, mouseX, mouseY)
        renderSackOfSacks(graphics, panelX, stripY)
        // Settings button right of the Sack of Sacks panel, level with its top
        tools.place(panelX + LOGICAL_W + GAP, stripY)
        tools.render(graphics, mouseX, mouseY)
        prompt.update(width, height)
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
        if (!prompt.isOpen) {
            view.renderTooltip(graphics)
            tools.renderTooltip(graphics)
        }
        prompt.render(graphics, width, height, mouseX, mouseY)
    }

    private fun renderSackOfSacks(graphics: GuiGraphicsExtractor, x: Int, y: Int) {
        val slots = SackStore.sackSlots
        stripHits.clear()
        panel(graphics, x, y, LOGICAL_W, stripH)
        graphics.drawString(tr("sack_of_sacks"), x + INNER + 1, y + 3, TEXT, SHADOW)
        if (slots.isEmpty()) graphics.drawString(tr("open_sacks_once"), x + INNER + 1, y + HEADER_H + 4, MUTED, SHADOW)
        slots.forEachIndexed { i, sackId ->
            val sx = x + INNER + (i % 9) * SLOT
            val sy = y + HEADER_H + (i / 9) * SLOT
            if (sackId == null) {
                border(graphics, sx, sy, SLOT, SLOT, EMPTY)
                return@forEachIndexed
            }
            slot(graphics, sx, sy)
            graphics.item(SackStore.stackOf(sackId), sx + 1, sy + 1)
            stripHits += intArrayOf(sx, sy, sx + SLOT, sy + SLOT) to SackStore.familyOf(sackId)
        }
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val mx = event.x.toInt()
        val my = event.y.toInt()
        if (prompt.mouseClicked(mx, my)) return true
        if (tools.click(mx, my)) return true
        stripHits.firstOrNull { (r, _) -> mx in r[0] until r[2] && my in r[1] until r[3] }?.let { (_, family) ->
            view.select(family)
            return true
        }
        if (view.click(mx, my, event.button(), event.hasShiftDown())) return true
        return super.mouseClicked(event, doubleClick)
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (prompt.keyPressed(event.key())) return true
        if (SearchInput.keyPressed(event, search, view, null) { setFocused(it) }) return true
        return super.keyPressed(event)
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        if (prompt.charTyped(event.codepoint())) return true
        return super.charTyped(event)
    }

    override fun mouseDragged(event: MouseButtonEvent, dragX: Double, dragY: Double): Boolean {
        if (view.mouseDragged(event.x.toInt(), event.y.toInt())) return true
        return super.mouseDragged(event, dragX, dragY)
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        if (view.mouseReleased()) return true
        return super.mouseReleased(event)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        view.scrollBy(scrollY)
        return true
    }
}
