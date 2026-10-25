package io.github.sackoverlay

import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.input.KeyEvent

/** Keyboard handling shared by both screens: search box, the enchanted key, arrow navigation. */
object SearchInput {

    /** Sets up the search box: initial text and remembering what is typed. */
    fun setup(search: EditBox) {
        search.value = SackPageView.initialQuery()
        search.setResponder { SackPageView.savedQuery = it }
        // A key still held when the last screen closed never got its release
        SackStore.enchantedPeek = false
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
        // Same as the Enchanted button (Left Alt by default): flips sacks, or shows the other side while held
        if (isEnchantedKey(event)) {
            if (SackConfig.get.enchantedKey == EnchantedKey.HOLD) SackStore.enchantedPeek = true else SackStore.toggleEnchantedView()
            return true
        }
        return view.navigate(event.key())
    }

    /** Hold mode: releasing the enchanted key goes back to the side shown before. */
    fun keyReleased(event: KeyEvent): Boolean {
        if (!isEnchantedKey(event) || !SackStore.enchantedPeek) return false
        SackStore.enchantedPeek = false
        return true
    }

    private fun isEnchantedKey(event: KeyEvent) = SackConfig.get.combineEnchanted && SackKeys.toggleEnchanted.matches(event)
}
