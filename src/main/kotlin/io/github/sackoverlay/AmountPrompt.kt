package io.github.sackoverlay

import io.github.sackoverlay.Gui.SHADOW
import io.github.sackoverlay.Gui.TEXT
import io.github.sackoverlay.Gui.card
import io.github.sackoverlay.Gui.panel
import io.github.sackoverlay.Gui.slot
import io.github.sackoverlay.Gui.tr
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import tech.thatgravyboat.skyblockapi.platform.drawString

/**
 * "Choose an amount" window opened by a shift-click on a page item, in the spirit of SkyOcean's:
 * +1/+10/+100/+1000 and -1/-10/-100/-1000 buttons, a number field and an OK button.
 * Entirely drawn and handled here, on top of everything: while it is open it takes every key and click.
 */
class AmountPrompt(private val font: Font, private val view: SackPageView) {

    private data class Button(val label: String, val x: Int, val y: Int, val w: Int, val action: () -> Unit)

    private var itemId: String? = null
    private var text = ""
    private var x = 0
    private var y = 0
    private val buttons = mutableListOf<Button>()

    val isOpen get() = itemId != null

    /** Opens the window in the middle of the screen if the view asked for it. Call every frame. */
    fun update(screenW: Int, screenH: Int) {
        val (id, _, _) = view.amountPrompt ?: return
        view.amountPrompt = null
        itemId = id
        text = ""
        x = (screenW - W) / 2
        y = (screenH - H) / 2
        buttons.clear()
        val rowTop = y + PAD + TITLE_H
        STEPS.forEachIndexed { i, step ->
            val bx = x + PAD + i * (BTN_W + SPACING)
            buttons += Button("+$step", bx, rowTop, BTN_W) { add(step) }
            buttons += Button("-$step", bx, rowTop + 2 * (BTN_H + SPACING), BTN_W) { add(-step) }
        }
        buttons += Button("OK", x + PAD + 3 * (BTN_W + SPACING), rowTop + BTN_H + SPACING, BTN_W) { confirm() }
        view.frozen = true
    }

    /** Draws the window above everything; call after the screen and its widgets are drawn. */
    fun render(graphics: GuiGraphicsExtractor, screenW: Int, screenH: Int, mouseX: Int, mouseY: Int) {
        val id = itemId ?: return
        graphics.nextStratum()
        graphics.fill(0, 0, screenW, screenH, BACKDROP)
        panel(graphics, x, y, W, H)

        val stack = SackStore.stackOf(id)
        graphics.drawString(stack.hoverName, x + PAD, y + PAD, TEXT, SHADOW)

        // Middle row: the item, the number field, then OK
        val rowY = y + PAD + TITLE_H + BTN_H + SPACING
        slot(graphics, x + PAD, rowY + 1)
        graphics.item(stack, x + PAD + 1, rowY + 2)
        val fieldX = x + PAD + SLOT_SIZE + SPACING
        val fieldW = 3 * (BTN_W + SPACING) - SLOT_SIZE - 2 * SPACING
        graphics.fill(fieldX, rowY, fieldX + fieldW, rowY + BTN_H, FIELD_BORDER)
        graphics.fill(fieldX + 1, rowY + 1, fieldX + fieldW - 1, rowY + BTN_H - 1, FIELD_FILL)
        val cursor = if ((System.currentTimeMillis() / 500) % 2 == 0L) "_" else ""
        val shown = if (text.isEmpty()) "" else text
        graphics.drawString(shown + cursor, fieldX + 5, rowY + 6, 0xFFFFFFFF.toInt(), true)
        if (text.isEmpty()) graphics.drawString(tr("amount"), fieldX + 5 + font.width("_"), rowY + 6, 0xFF707070.toInt(), false)

        for (button in buttons) {
            val hovered = mouseX in button.x until button.x + button.w && mouseY in button.y until button.y + BTN_H
            card(graphics, button.x, button.y, button.w, BTN_H)
            if (hovered) graphics.fill(button.x + 2, button.y + 2, button.x + button.w - 2, button.y + BTN_H - 2, Theme.current.hover)
            val label = Component.literal(button.label)
            graphics.drawString(label, button.x + (button.w - font.width(label)) / 2, button.y + 6, TEXT, SHADOW)
        }
    }

    /** Returns true when the window used the key (always, while open). */
    fun keyPressed(key: Int): Boolean {
        if (!isOpen) return false
        when (key) {
            KEY_ENTER, KEY_KP_ENTER -> confirm()
            KEY_ESCAPE -> close()
            KEY_BACKSPACE -> text = text.dropLast(1)
        }
        return true
    }

    /** Digits typed while the window is open. */
    fun charTyped(codepoint: Int): Boolean {
        if (!isOpen) return false
        val c = codepoint.toChar()
        if (c.isDigit() && text.length < MAX_DIGITS) text = (text + c).trimStart('0')
        return true
    }

    /** Buttons react, a click outside the window cancels it. Returns true while open (the click is consumed). */
    fun mouseClicked(mouseX: Int, mouseY: Int): Boolean {
        if (!isOpen) return false
        buttons.firstOrNull { mouseX in it.x until it.x + it.w && mouseY in it.y until it.y + BTN_H }?.let {
            it.action()
            return true
        }
        if (mouseX !in x until x + W || mouseY !in y until y + H) close()
        return true
    }

    private fun add(step: Int) {
        val next = ((text.toIntOrNull() ?: 0) + step).coerceIn(0, MAX_AMOUNT)
        text = if (next == 0) "" else next.toString()
    }

    private fun confirm() {
        val id = itemId
        val amount = text.toIntOrNull()
        if (id != null && amount != null && amount > 0) view.withdraw(id, amount)
        close()
    }

    private fun close() {
        itemId = null
        view.frozen = false
    }

    private companion object {
        val STEPS = listOf(1, 10, 100, 1000)
        const val BTN_W = 34
        const val BTN_H = 20
        const val SPACING = 2
        const val PAD = 7
        const val TITLE_H = 13
        const val SLOT_SIZE = 18
        const val W = 4 * BTN_W + 3 * SPACING + 2 * PAD
        const val H = TITLE_H + 3 * BTN_H + 2 * SPACING + 2 * PAD
        const val MAX_DIGITS = 7
        const val MAX_AMOUNT = 9_999_999
        const val BACKDROP = 0x60000000
        const val FIELD_BORDER = 0xFFA0A0A0.toInt()
        const val FIELD_FILL = 0xFF000000.toInt()
        const val KEY_ENTER = 257
        const val KEY_KP_ENTER = 335
        const val KEY_ESCAPE = 256
        const val KEY_BACKSPACE = 259
    }
}
