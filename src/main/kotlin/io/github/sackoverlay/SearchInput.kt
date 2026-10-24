package io.github.sackoverlay

import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.input.KeyEvent
import net.minecraft.world.item.ItemStack
import tech.thatgravyboat.skyblockapi.utils.text.TextProperties.stripped

/** Keyboard handling shared by both screens: search box, "search hovered item" key, arrow navigation. */
object SearchInput {

    /** Sets up the search box: initial text and remembering what is typed. */
    fun setup(search: EditBox) {
        search.value = SackPageView.initialQuery()
        search.setResponder { SackPageView.savedQuery = it }
    }

    /**
     * Returns true when the key was used here. The search box only takes keys once it was clicked.
     * [slotStack] is the item of the real slot under the mouse (empty stack for an empty slot), or null when
     * the mouse is not over a real slot.
     */
    fun keyPressed(event: KeyEvent, search: EditBox, view: SackPageView, slotStack: ItemStack?, focus: (EditBox?) -> Unit): Boolean {
        if (SackKeys.searchHovered.matches(event)) {
            val stack = view.hoveredStack() ?: slotStack?.takeUnless { it.isEmpty }
            if (stack != null) {
                search.value = stack.hoverName.stripped
                return true
            }
        }
        if (search.isFocused) {
            when {
                // Escape leaves the search box first; a second Escape closes the screen
                event.key() == SackKeys.KEY_ESCAPE -> focus(null)
                else -> search.keyPressed(event)
            }
            return true
        }
        return view.navigate(event.key())
    }
}
