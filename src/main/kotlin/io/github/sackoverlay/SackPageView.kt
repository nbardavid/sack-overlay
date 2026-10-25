package io.github.sackoverlay

import io.github.sackoverlay.Gui.GAP
import io.github.sackoverlay.Gui.HEADER_H
import io.github.sackoverlay.Gui.INNER
import io.github.sackoverlay.Gui.LOGICAL_W
import io.github.sackoverlay.Gui.MUTED
import io.github.sackoverlay.Gui.PAD
import io.github.sackoverlay.Gui.SCROLLBAR
import io.github.sackoverlay.Gui.SEARCH_W
import io.github.sackoverlay.Gui.SELECTED
import io.github.sackoverlay.Gui.SHADOW
import io.github.sackoverlay.Gui.SLOT
import io.github.sackoverlay.Gui.SLOT_BG
import io.github.sackoverlay.Gui.TEXT
import io.github.sackoverlay.Gui.border
import io.github.sackoverlay.Gui.card
import io.github.sackoverlay.Gui.panel
import io.github.sackoverlay.Gui.slot
import io.github.sackoverlay.Gui.tr
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import tech.thatgravyboat.skyblockapi.api.profile.items.sacks.SacksAPI
import tech.thatgravyboat.skyblockapi.helpers.McClient
import tech.thatgravyboat.skyblockapi.platform.drawString
import tech.thatgravyboat.skyblockapi.utils.extentions.scaled
import tech.thatgravyboat.skyblockapi.utils.extentions.translated

/**
 * The sack pages panel, shared by the screen that replaces the Sack of Sacks and by /sackoverlay.
 * Storage Overlay layout: columns of pages with vanilla-sized slots, page tops aligned per row, a scrollbar.
 */
class SackPageView(private val font: Font, private val query: () -> String) {

    private data class Page(val family: String, val title: Component, val items: List<String>)
    private data class Hit(val x0: Int, val y0: Int, val x1: Int, val y1: Int, val action: () -> Unit)
    private data class Hovered(val id: String, val stack: ItemStack, val family: String)

    private var scroll = savedScroll
    private var maxScroll = 0
    private var selected: String? = null
    private val hits = mutableListOf<Hit>()
    private var hovered: Hovered? = null
    private val pageTops = mutableMapOf<String, Int>()
    private val withdrawals = WithdrawQueue()

    /** A page grabbed by its title. It only starts moving once the mouse has travelled a few pixels. */
    private class Drag(val family: String, val startX: Int, val startY: Int, val grabX: Int, val grabY: Int) {
        var active = false
        var target = -1
    }
    private var drag: Drag? = null

    // Page titles and page bounds of the last frame, in screen coordinates, in display order
    private data class PageRect(val family: String, val x: Int, val y: Int, val w: Int, val h: Int)
    private val headerRects = mutableListOf<PageRect>()
    private val pageRects = mutableListOf<PageRect>()
    private var displayed = listOf<String>()

    // Slots keep the vanilla inventory size: more columns are added instead of scaling pages up
    private var columns = 1

    private val hypixelMode get() = SackConfig.get.displayMode == DisplayMode.HYPIXEL

    /** Scrolls to this family's page and highlights it. */
    fun select(family: String) {
        selected = family
        SackStore.setFolded(family, false)
        pageTops[family]?.let { scroll = it.coerceIn(0, maxScroll) }
    }

    /** Arrow keys move the highlight between pages: left/right in reading order, up/down by one row. */
    fun navigate(key: Int): Boolean {
        val step = when (key) {
            SackKeys.KEY_LEFT -> -1
            SackKeys.KEY_RIGHT -> 1
            SackKeys.KEY_UP -> -columns
            SackKeys.KEY_DOWN -> columns
            else -> return false
        }
        if (displayed.isEmpty()) return true
        val current = displayed.indexOf(selected)
        val next = if (current < 0) 0 else (current + step).coerceIn(0, displayed.lastIndex)
        select(displayed[next])
        return true
    }

    /** Set while a popup is open: no hover, no tooltip, no clicks on the pages. */
    var frozen = false

    /** Item the player shift-clicked and wants to type an amount for, with the click position. */
    var amountPrompt: Triple<String, Int, Int>? = null

    /** button: 0 = left click, 1 = right click. */
    fun click(mx: Int, my: Int, button: Int, shift: Boolean): Boolean {
        hits.lastOrNull { mx in it.x0 until it.x1 && my in it.y0 until it.y1 }?.let {
            it.action()
            return true
        }
        // A page title: left click folds or starts a drag (decided on release), right click folds.
        // It never touches the real Sack of Sacks slots: a click there takes the sack out of it.
        headerRects.firstOrNull { mx in it.x until it.x + it.w && my in it.y until it.y + it.h }?.let {
            when (button) {
                0 -> drag = Drag(it.family, mx, my, mx - it.x, my - it.y)
                1 -> toggleFold(it.family)
            }
            return true
        }
        val id = hovered?.id ?: return false
        val owned = SackStore.count(id)
        if (owned <= 0) return true
        if (shift) {
            amountPrompt = Triple(id, mx, my)
            return true
        }
        withdraw(id, requestedAmount(button, owned))
        return true
    }

    /** Folds or unfolds a page; the state is saved, so it stays across screens and restarts. */
    private fun toggleFold(family: String) = SackStore.setFolded(family, !SackStore.isFolded(family))

    /** Withdraws through /gfs, at most one command per configured delay (see [WithdrawQueue]). */
    fun withdraw(itemId: String, requested: Int) = withdrawals.add(itemId, requested)

    /** Left click: everything owned, Hypixel fills the inventory (or a fixed amount if configured). Right click: one stack. */
    private fun requestedAmount(button: Int, owned: Int): Int {
        val config = SackConfig.get
        return when {
            button == 1 -> config.rightClickStack
            config.leftClickFillsInventory -> owned
            else -> config.leftClickAmount
        }
    }

    fun mouseDragged(mx: Int, my: Int): Boolean {
        val current = drag ?: return false
        if (!current.active && (kotlin.math.abs(mx - current.startX) > DRAG_THRESHOLD || kotlin.math.abs(my - current.startY) > DRAG_THRESHOLD)) {
            current.active = true
            current.target = displayed.indexOf(current.family)
        }
        return true
    }

    fun mouseReleased(): Boolean {
        val current = drag ?: return false
        drag = null
        if (!current.active) {
            // No movement: a plain click on the title folds the page
            toggleFold(current.family)
            return true
        }
        // Save the order as displayed, keeping the place of pages hidden by the search
        val shown = displayed
        SackStore.setPageOrder(shown + SackStore.data.pageOrder.filter { it !in shown })
        return true
    }

    fun scrollBy(amount: Double) {
        scroll = (scroll - (amount * 30).toInt()).coerceIn(0, maxScroll)
    }

    /** Draws the panel in (x, y, w, h). The search box is an EditBox the screen places at the top right. */
    fun render(graphics: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, mouseX: Int, mouseY: Int) {
        hits.clear()
        hovered = null
        columns = Gui.columnsFor(w)
        withdrawals.flush()

        panel(graphics, x, y, w, h)
        graphics.drawString(tr("title"), x + PAD, y + 6, TEXT, SHADOW)
        val modeLabel = tr(if (hypixelMode) "mode.hypixel" else "mode.compact")
        val modeX = x + w - PAD - SEARCH_W - 8 - font.width(modeLabel)
        graphics.drawString(modeLabel, modeX, y + 6, MUTED, SHADOW)
        hits += Hit(modeX, y + 3, modeX + font.width(modeLabel), y + 16) {
            SackConfig.get.displayMode = if (hypixelMode) DisplayMode.COMPACT else DisplayMode.HYPIXEL
            SackConfig.save()
        }

        renderPages(graphics, mouseX, mouseY, x + PAD, y + BAR_H, x + w - PAD, y + h - PAD)
    }

    /** Tooltip of the hovered item, if any (see [SackTooltip]). */
    fun renderTooltip(graphics: GuiGraphicsExtractor) {
        val (id, stack, family) = hovered ?: return
        SackTooltip.render(graphics, font, id, stack, family)
    }

    // ---------- Data ----------

    private fun pages(): List<Page> {
        val families = SackStore.families
        val data = SackStore.data
        val owned = SackStore.sackSlots.filterNotNull().map(SackStore::familyOf)
        // Owned sacks first (Sack of Sacks order), then those whose menu the player opened
        val order = (owned + data.layouts.keys).distinct().filter { it in families || it in data.layouts }
            .ifEmpty { families.keys.filter { f -> families[f].orEmpty().any { SackStore.count(it) > 0 } } }

        // Pages the player moved come first, in their chosen order
        val custom = data.pageOrder
        val sorted = order.sortedBy { custom.indexOf(it).let { i -> if (i < 0) Int.MAX_VALUE else i } }

        val shown = mutableSetOf<String>()
        val pages = sorted.mapNotNull { family ->
            val base = data.layouts[family] ?: families[family].orEmpty()
            val all = if (SackConfig.get.itemOrder == ItemOrder.LOGICAL) SackStore.logicalOrder(family, base) else base
            val items = if (hypixelMode) all else all.filter { SackStore.count(it) > 0 && it !in shown }
            shown += items
            if (items.isEmpty()) null else Page(family, Gui.familyTitle(family, owned.count { it == family }), items)
        }
        // Sack items that no displayed page lists
        val others = SacksAPI.sackItems.filter { (id, n) -> n > 0 && id !in shown }.keys.sorted()
        val all = if (others.isEmpty()) pages else pages + Page("OTHER", tr("others"), others)
        // Hidden pages stay out, except while searching so their items can still be found
        val searching = query().isNotBlank()
        return all.filter { searching || !SackStore.isHidden(it.family) }
            .sortedBy { custom.indexOf(it.family).let { i -> if (i < 0) Int.MAX_VALUE else i } }
    }

    private fun matches(itemId: String): Boolean {
        val q = query().trim()
        return q.isEmpty() || SackStore.stackOf(itemId).hoverName.string.contains(q, ignoreCase = true)
    }

    // ---------- Rendering ----------

    /** In Dim mode with a search typed, every page stays and only the items are greyed out. */
    private val dimming get() = SackConfig.get.searchMode == SearchMode.DIM && query().isNotBlank()

    private fun renderPages(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, left: Int, top: Int, right: Int, bottom: Int) {
        var pages = if (dimming) {
            pages().map { it to it.items }
        } else {
            pages().mapNotNull { page ->
                val items = page.items.filter(::matches)
                if (items.isEmpty()) null else page to items
            }
        }
        if (pages.isEmpty()) {
            graphics.drawString(tr("empty"), left, top + 4, MUTED, SHADOW)
            return
        }

        // While dragging, the grabbed page takes the place of the page under the mouse and the others shift
        val current = drag?.takeIf { it.active }
        val dragged = current?.let { d -> pages.firstOrNull { it.first.family == d.family } }
        if (current != null && dragged != null) {
            pageRects.firstOrNull { mouseX in it.x until it.x + it.w && mouseY in it.y until it.y + it.h }
                ?.let { current.target = displayed.indexOf(it.family) }
            val without = pages - dragged
            pages = without.toMutableList().apply { add(current.target.coerceIn(0, without.size), dragged) }
            // Scroll when the dragged page reaches the top or bottom edge
            if (mouseY < top + EDGE_SCROLL) scroll -= 4
            if (mouseY > bottom - EDGE_SCROLL) scroll += 4
        }
        displayed = pages.map { it.first.family }
        headerRects.clear()
        pageRects.clear()

        // Layout first: where every visible page goes
        val placements = ArrayList<Placement>(pages.size)
        var rowTop = 0
        for (row in pages.chunked(columns)) {
            var rowH = 0
            row.forEachIndexed { col, (page, items) ->
                val folded = SackStore.isFolded(page.family) && query().isBlank()
                val rows = if (folded) 0 else (items.size + 8) / 9
                val h = HEADER_H + rows * SLOT + (if (rows > 0) INNER else 0)
                rowH = maxOf(rowH, h)
                pageTops[page.family] = rowTop

                val x = left + col * (LOGICAL_W + GAP)
                val y = top + rowTop - scroll
                pageRects += PageRect(page.family, x, y, LOGICAL_W, h)
                if (y > bottom || y + h < top) return@forEachIndexed
                placements += Placement(page, items, rows, h, folded, x, y)
                if (mouseY in top until bottom) headerRects += PageRect(page.family, x, y, LOGICAL_W, HEADER_H)
            }
            rowTop += rowH + GAP
        }

        val inArea = mouseY in top until bottom && current == null && !frozen
        graphics.enableScissor(left, top, right, bottom)
        if (SackConfig.get.cachePages && current == null) {
            // Static content comes from the offscreen layer, redrawn only when something it shows changed
            val signature = signatureOf(placements, left, top, right, bottom)
            val generation = PageLayer.sync(graphics)
            if (generation != layerGeneration || signature != layerSignature) {
                val layer = PageLayer.beginLayer(graphics, left, top, right - left, bottom - top)
                for (p in placements) layer.translated(p.x, p.y) { renderPage(layer, p, Pass.STATIC) }
                PageLayer.endLayer(layer)
                layerGeneration = generation
                layerSignature = signature
            }
            PageLayer.blit(graphics, left, top, right, bottom)
            // Live on top: enchanted (animated) items and the hover highlight
            for (p in placements) graphics.translated(p.x, p.y) { renderPage(graphics, p, Pass.LIVE, mouseX - p.x, mouseY - p.y, inArea) }
        } else {
            for (p in placements) {
                if (p.page == dragged?.first) {
                    // Empty frame where the dragged page will land
                    border(graphics, p.x, p.y, LOGICAL_W, p.h, SELECTED)
                    continue
                }
                graphics.translated(p.x, p.y) { renderPage(graphics, p, Pass.ALL, mouseX - p.x, mouseY - p.y, inArea) }
            }
        }
        graphics.disableScissor()

        maxScroll = (rowTop - GAP - (bottom - top)).coerceAtLeast(0)
        scroll = scroll.coerceIn(0, maxScroll)
        savedScroll = scroll
        scrollbar(graphics, right - SCROLLBAR, top, bottom, rowTop - GAP)

        // The dragged page follows the mouse, above everything else
        if (current != null && dragged != null) {
            val (page, items) = dragged
            val folded = SackStore.isFolded(page.family) && query().isBlank()
            val rows = if (folded) 0 else (items.size + 8) / 9
            val h = HEADER_H + rows * SLOT + (if (rows > 0) INNER else 0)
            val ghost = Placement(page, items, rows, h, folded, mouseX - current.grabX, mouseY - current.grabY)
            graphics.translated(ghost.x, ghost.y) { renderPage(graphics, ghost, Pass.ALL) }
        }
    }

    /** A page and where it goes on screen. */
    private data class Placement(
        val page: Page, val items: List<String>, val rows: Int, val h: Int, val folded: Boolean, val x: Int, val y: Int,
    )

    /** STATIC: what goes in the cached layer. LIVE: what is drawn over it every frame. ALL: both, without a cache. */
    private enum class Pass { STATIC, LIVE, ALL }

    // Signature and generation of what the layer currently holds
    private var layerSignature = 0L
    private var layerGeneration = -1

    /** Everything the static layer depends on, hashed: when it changes, the layer is redrawn. */
    private fun signatureOf(placements: List<Placement>, left: Int, top: Int, right: Int, bottom: Int): Long {
        var h = -3750763034362895579L
        fun mix(v: Int) {
            h = (h xor v.toLong()) * 1099511628211L
        }
        mix(left); mix(top); mix(right); mix(bottom)
        mix(Theme.current.ordinal)
        mix(selected.hashCode())
        mix(query().hashCode())
        mix(if (dimming) 1 else 0)
        mix(if (SackConfig.get.rarityBackgrounds) 1 else 0)
        mix(McClient.self.window.guiScale)
        for (p in placements) {
            mix(p.page.family.hashCode()); mix(p.x); mix(p.y); mix(p.h); mix(if (p.folded) 1 else 0)
            mix(p.page.title.string.hashCode())
            mix(SackStore.familyCapacity(p.page.family) ?: -1)
            for (id in p.items) {
                mix(id.hashCode())
                mix(SackStore.count(id))
            }
        }
        return h
    }

    private fun renderPage(
        graphics: GuiGraphicsExtractor, p: Placement, pass: Pass,
        mx: Int = -1, my: Int = -1, mouseInArea: Boolean = false,
    ) {
        val (page, items, rows, h, folded) = p
        val cap = SackStore.familyCapacity(page.family)
        if (pass != Pass.LIVE) {
            card(graphics, 0, 0, LOGICAL_W, h)
            // The selected page gets a gold outline over the frame
            if (page.family == selected) border(graphics, 0, 0, LOGICAL_W, h, SELECTED)
            graphics.drawString(page.title, INNER + 1, 3, TEXT, SHADOW)
            // Right side: how much each slot can hold with the equipped sacks
            val right = if (folded) "▸" else cap?.let { "max ${Amounts.short(it)}" } ?: ""
            graphics.drawString(right, LOGICAL_W - INNER - font.width(right), 3, MUTED, SHADOW)
        }
        if (folded) return

        for (i in 0 until rows * 9) {
            val sx = INNER + (i % 9) * SLOT
            val sy = HEADER_H + (i / 9) * SLOT
            if (pass != Pass.LIVE) slot(graphics, sx, sy)
            val id = items.getOrNull(i) ?: continue
            val stack = SackStore.stackOf(id)
            // Enchanted items are animated: they stay out of the cached layer and are drawn live
            val animated = stack.hasFoil()
            val isHovered = pass != Pass.STATIC && mouseInArea && mx >= sx && mx < sx + SLOT && my >= sy && my < sy + SLOT
            // Vanilla slot highlight: back part under the item (only when the item is drawn here), front part over it
            if (isHovered && (pass == Pass.ALL || animated)) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HIGHLIGHT_BACK, sx - 3, sy - 3, 24, 24)
            }
            val drawContent = when (pass) {
                Pass.ALL -> true
                Pass.STATIC -> !animated
                Pass.LIVE -> animated
            }
            if (drawContent) slotContent(graphics, id, stack, sx, sy, cap)
            // Withdrawal waiting or just sent: the slot fills from the bottom until the next /gfs can go out
            if (pass != Pass.STATIC) {
                withdrawals.fill(id)?.let { fraction ->
                    val fillH = ((SLOT - 2) * fraction).toInt()
                    graphics.fill(sx + 1, sy + SLOT - 1 - fillH, sx + SLOT - 1, sy + SLOT - 1, WAIT_OVERLAY)
                }
            }
            if (isHovered) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HIGHLIGHT_FRONT, sx - 3, sy - 3, 24, 24)
                hovered = Hovered(id, stack, page.family)
            }
        }
    }

    /** The item of a slot with its overlays and amount. */
    private fun slotContent(graphics: GuiGraphicsExtractor, id: String, stack: ItemStack, sx: Int, sy: Int, cap: Int?) {
        RarityBackground.draw(graphics, stack, sx + 1, sy + 1)
        graphics.item(stack, sx + 1, sy + 1)
        val amount = SackStore.count(id)
        if (amount == 0) {
            // Accepted but not owned: greyed out, no amount
            graphics.fill(sx + 1, sy + 1, sx + SLOT - 1, sy + SLOT - 1, Gui.ZERO_OVERLAY)
        } else {
            // Full slot: red veil over the item, nothing for slots that still have room
            if (cap != null && amount >= cap) {
                graphics.fill(sx + 1, sy + 1, sx + SLOT - 1, sy + SLOT - 1, FULL_OVERLAY)
            }
            // Small amount (3/4 size) in the bottom right corner, like the vanilla stack count
            val label = Amounts.label(amount)
            graphics.translated(sx + SLOT - 1, sy + SLOT - 7) {
                graphics.scaled(COUNT_SCALE, COUNT_SCALE) {
                    graphics.drawString(label, -font.width(label), 0, COUNT, true)
                }
            }
        }
        // Dim mode: items that don't match the search are covered, the others stay as they are
        if (dimming && !matches(id)) {
            graphics.fill(sx + 1, sy + 1, sx + SLOT - 1, sy + SLOT - 1, Gui.DIM_OVERLAY)
        }
    }

    private fun scrollbar(graphics: GuiGraphicsExtractor, x: Int, top: Int, bottom: Int, contentH: Int) {
        val viewH = bottom - top
        graphics.fill(x, top, x + SCROLLBAR, bottom, SLOT_BG)
        if (contentH <= viewH) return
        val thumbH = (viewH * viewH / contentH).coerceAtLeast(12)
        val thumbY = top + (viewH - thumbH) * scroll / maxScroll.coerceAtLeast(1)
        graphics.fill(x, thumbY, x + SCROLLBAR, thumbY + thumbH, MUTED)
    }

    companion object {
        const val BAR_H = 20
        const val WAIT_OVERLAY = 0x80FFFFFF.toInt()
        const val COUNT_SCALE = 0.75f
        const val DRAG_THRESHOLD = 4
        const val EDGE_SCROLL = 16

        // Same in every theme: amounts are white with a shadow, like vanilla stack counts
        const val COUNT = 0xFFFFFFFF.toInt()
        const val FULL_OVERLAY = 0x70FF3B30
        private val HIGHLIGHT_BACK = Identifier.withDefaultNamespace("container/slot_highlight_back")
        private val HIGHLIGHT_FRONT = Identifier.withDefaultNamespace("container/slot_highlight_front")

        // Kept across screens for the "remember" settings, and filled by /sackoverlay search
        var savedScroll = 0
        var savedQuery = ""
        var pendingQuery: String? = null

        /** Search text to show when a screen opens: a /sackoverlay search query first, then the remembered one. */
        fun initialQuery(): String =
            pendingQuery?.also { pendingQuery = null } ?: if (SackConfig.get.rememberSearch) savedQuery else ""
    }
}
