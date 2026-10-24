package io.github.sackoverlay

import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.input.KeyEvent

/** Keyboard handling shared by both screens: search box and arrow navigation. */
object SearchInput {

    /** Sets up the search box: initial text and remembering what is typed. */
    fun setup(search: EditBox) {
        search.value = SackPageView.initialQuery()
        search.setResponder { SackPageView.savedQuery = it }
    }

    /** Returns true when the key was used here. The search box only takes keys once it was clicked. */
    fun keyPressed(event: KeyEvent, search: EditBox, view: SackPageView, focus: (EditBox?) -> Unit): Boolean {
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
