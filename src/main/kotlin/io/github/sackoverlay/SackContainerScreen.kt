package io.github.sackoverlay

import io.github.sackoverlay.Gui.GAP
import io.github.sackoverlay.Gui.HEADER_H
import io.github.sackoverlay.Gui.INNER
import io.github.sackoverlay.Gui.LOGICAL_W
import io.github.sackoverlay.Gui.PAD
import io.github.sackoverlay.Gui.SEARCH_W
import io.github.sackoverlay.Gui.SHADOW
import io.github.sackoverlay.Gui.SLOT
import io.github.sackoverlay.Gui.TEXT
import io.github.sackoverlay.Gui.panel
import io.github.sackoverlay.Gui.slot
import io.github.sackoverlay.Gui.tr
import io.github.sackoverlay.Gui.widthFor
import io.github.sackoverlay.mixin.AbstractContainerScreenAccessor
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.ContainerScreen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.Slot
import tech.thatgravyboat.skyblockapi.helpers.McClient
import tech.thatgravyboat.skyblockapi.helpers.McPlayer
import tech.thatgravyboat.skyblockapi.platform.drawString
import tech.thatgravyboat.skyblockapi.utils.extentions.cleanName
import tech.thatgravyboat.skyblockapi.utils.extentions.isSkyblockFiller
import tech.thatgravyboat.skyblockapi.utils.text.TextProperties.stripped

/**
 * Replaces the Sack of Sacks screen while keeping the server's real menu.
 * The real slots are only moved: the player inventory stays centered at the bottom as usual,
 * the menu (sacks, buttons) goes into a panel on its left, and the pages fill the top.
 * Clicks on those slots are ordinary vanilla clicks.
 */
class SackContainerScreen(menu: ChestMenu, inventory: Inventory, title: Component) :
    AbstractContainerScreen<ChestMenu>(menu, inventory, title) {

    private lateinit var search: EditBox
    private lateinit var tools: ToolButtons
    private lateinit var view: SackPageView
    private lateinit var prompt: AmountPrompt
    private val placed = mutableListOf<Slot>()

    // Layout in screen coordinates, recomputed every frame
    private var pagesX = 0
    private var pagesY = 0
    private var pagesW = 0
    private var pagesH = 0
    private var invX = 0
    private var sackX = 0
    private var panelsY = 0
    private var bottom = 0

    private val sackH get() = HEADER_H + ((placed.size + 8) / 9).coerceAtLeast(1) * SLOT + INNER

    override fun init() {
        super.init()
        collectPlaced()
        layout()
        search = EditBox(font, 0, 0, SEARCH_W, 12, tr("search"))
        search.setHint(tr("search"))
        SearchInput.setup(search)
        addRenderableWidget(search)
        tools = ToolButtons { McClient.setScreenAsync { SackConfig.screen(null) } }
        placeWidgets()
        view = SackPageView(font) { search.value }
        prompt = AmountPrompt(font, view)
    }

    private fun collectPlaced() {
        placed.clear()
        for (i in 0 until menu.rowCount * 9) {
            val slot = menu.slots[i]
            // An empty slot is a free sack slot; only the decorative glass panes are hidden
            if (slot.item.isEmpty || !slot.item.isSkyblockFiller()) placed += slot
        }
    }

    /**
     * Computes the panels, then reports their bounding box as leftPos/topPos/imageWidth/imageHeight,
     * so mods that attach around a menu (Skyblocker tabs, RRV bar...) place themselves around it.
     */
    private fun layout() {
        bottom = height - bottomMargin()
        panelsY = bottom - maxOf(INV_H, sackH)

        // The inventory stays where the game usually puts it: centered horizontally
        invX = (width - INV_W) / 2
        sackX = (invX - GAP - LOGICAL_W).coerceAtLeast(4)

        pagesW = widthFor(Gui.columnsFor(width - 16))
        pagesX = (width - pagesW) / 2
        pagesY = topMargin()
        pagesH = panelsY - GAP - pagesY

        val left = minOf(pagesX, sackX)
        val right = maxOf(pagesX + pagesW, invX + INV_W + GAP + ToolButtons.ROW_WIDTH)
        leftPos = left
        topPos = pagesY
        // The interface is added by the mixin at load time, hence the cast through Any
        val accessor = (this as Any) as AbstractContainerScreenAccessor
        accessor.sackoverlay_setImageWidth(right - left)
        accessor.sackoverlay_setImageHeight(bottom - pagesY)
    }

    private fun placeWidgets() {
        search.x = pagesX + pagesW - PAD - SEARCH_W
        search.y = pagesY + 4
        // Settings button right of the inventory, level with its top, like Enhanced Storage
        tools.place(invX + INV_W + GAP, panelsY)
    }

    /** Places the real slots; their coordinates are relative to leftPos / topPos. */
    private fun placeSlots() {
        for (i in 0 until menu.rowCount * 9) {
            val slot = menu.slots[i]
            if (slot !in placed) {
                slot.x = PARKED
                slot.y = PARKED
            }
        }
        placed.forEachIndexed { n, slot ->
            slot.x = sackX + INNER + (n % 9) * SLOT + 1 - leftPos
            slot.y = panelsY + HEADER_H + (n / 9) * SLOT + 1 - topPos
        }
        // 27 slots then the hotbar, with the same offsets as the vanilla inventory
        val base = menu.slots.size - 36
        for (i in 0 until 36) {
            val slot = menu.slots[base + i]
            val row = if (i < 27) i / 9 else 3
            slot.x = invX + 8 + (i % 9) * SLOT - leftPos
            slot.y = panelsY + HEADER_H + 1 + row * SLOT + (if (i < 27) 0 else 4) - topPos
        }
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        collectPlaced()
        layout()
        placeWidgets()
        placeSlots()
        prompt.update(width, height)
        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
        if (!prompt.isOpen) {
            view.renderTooltip(graphics)
            tools.renderTooltip(graphics)
        }
        // The amount window goes over everything, slots and widgets included
        prompt.render(graphics, width, height, mouseX, mouseY)
    }

    // No item tooltips under the amount window
    override fun extractTooltip(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        if (!prompt.isOpen) super.extractTooltip(graphics, mouseX, mouseY)
    }

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick)
        view.render(graphics, pagesX, pagesY, pagesW, pagesH, mouseX, mouseY)

        panel(graphics, invX, panelsY, INV_W, INV_H)
        graphics.drawString(tr("inventory"), invX + 8, panelsY + 3, TEXT, SHADOW)
        for (i in 0 until 36) {
            val s = menu.slots[menu.slots.size - 36 + i]
            slot(graphics, s.x + leftPos - 1, s.y + topPos - 1)
        }

        panel(graphics, sackX, panelsY, LOGICAL_W, sackH)
        graphics.drawString(tr("sack_of_sacks"), sackX + INNER + 1, panelsY + 3, TEXT, SHADOW)
        placed.forEach { slot(graphics, it.x + leftPos - 1, it.y + topPos - 1) }

        tools.render(graphics, mouseX, mouseY)
    }

    // Titles are drawn inside the panels
    override fun extractLabels(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {}

    // Clicking next to the panels must never drop the held item
    override fun hasClickedOutside(mouseX: Double, mouseY: Double, left: Int, top: Int) = false

    override fun slotClicked(slot: Slot, slotId: Int, buttonNum: Int, containerInput: ContainerInput) {
        if (slot in placed && slot.item.cleanName.equals("Insert Inventory", ignoreCase = true)) {
            // Updates the display right away; the [Sacks] message confirms it later
            SackStore.onInsertRequested(menu.slots.takeLast(36).map { it.item })
        }
        super.slotClicked(slot, slotId, buttonNum, containerInput)
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        val mx = event.x.toInt()
        val my = event.y.toInt()
        if (prompt.mouseClicked(mx, my)) return true
        if (tools.click(mx, my)) return true
        if (view.click(mx, my, event.button(), event.hasShiftDown())) return true
        return super.mouseClicked(event, doubleClick)
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

    override fun keyPressed(event: KeyEvent): Boolean {
        if (prompt.keyPressed(event.key())) return true
        // Search box and arrows; typing in the search must not close the screen (E key)
        if (SearchInput.keyPressed(event, search, view) { setFocused(it) }) return true
        return super.keyPressed(event)
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        if (prompt.charTyped(event.codepoint())) return true
        return super.charTyped(event)
    }

    companion object {
        private const val PARKED = -9999
        private const val INV_W = 176
        private const val INV_H = HEADER_H + 1 + 4 * SLOT + 4 + INNER

        private val skyblockerLoaded = FabricLoader.getInstance().isModLoaded("skyblocker")
        private val rrvLoaded = FabricLoader.getInstance().isModLoaded("rrv")

        // Room for Skyblocker's Quick Nav tabs (28 px above and below the box) and RRV's search bar (22 px)
        private fun topMargin() = if (skyblockerLoaded) 32 else 8
        private fun bottomMargin() = 8 + (if (skyblockerLoaded) 26 else 0) + (if (rrvLoaded) 24 else 0)

        /** Called by the mixin on every screen change: only the Sack of Sacks is replaced, each sack's own menu stays vanilla. */
        @JvmStatic
        fun swap(screen: Screen?): Screen? {
            if (screen !is ContainerScreen || !SackConfig.get.replaceSackScreen) return screen
            if (!screen.title.stripped.equals("Sack of Sacks", ignoreCase = true)) return screen
            val player = McPlayer.self ?: return screen
            return SackContainerScreen(screen.menu, player.inventory, screen.title)
        }
    }
}
