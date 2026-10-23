package io.github.sackoverlay

import tech.thatgravyboat.skyblockapi.helpers.McClient

/**
 * Withdrawals asked by the player: exactly the /gfs command they would type themselves.
 * Hypixel handles commands slower than menu clicks, so one /gfs goes out per [delay]. Clicks in between are not
 * lost: they add up and leave as a single command when the delay is over (see [flush]).
 * Hypixel only moves what fits in the inventory, so no space check is needed before sending.
 * One queue per open screen: a withdrawal still waiting when the screen closes is dropped on purpose.
 */
class WithdrawQueue {

    // Withdrawal waiting for the delay to end, and the item of the last command sent (for the slot fill)
    private var queued: Pair<String, Int>? = null
    private var lastItem: String? = null
    private var lastCommand = 0L
    private val delay get() = SackConfig.get.withdrawDelayMs.toLong()

    fun add(itemId: String, requested: Int) {
        val already = queued?.takeIf { it.first == itemId }?.second ?: 0
        val amount = minOf(already + requested, SackStore.count(itemId))
        if (amount <= 0) return
        queued = itemId to amount
        flush()
    }

    /** Sends the queued withdrawal once the delay since the last command is over. Called on click and every frame. */
    fun flush() {
        val (itemId, amount) = queued ?: return
        val now = System.currentTimeMillis()
        if (now - lastCommand < delay) return
        queued = null
        lastCommand = now
        lastItem = itemId
        McClient.sendCommand("gfs $itemId $amount")
        // The display estimate is capped by the free space, since that is all Hypixel will move
        SackStore.onWithdrawRequested(itemId, minOf(amount, SackStore.inventoryRoom(itemId)))
    }

    /**
     * How full the slot of this item is drawn, from 0 to 1, while it waits for the delay (queued) or just sent a
     * command (cooling down); null when nothing is pending for it.
     */
    fun fill(itemId: String): Float? {
        if (itemId != queued?.first && itemId != lastItem) return null
        val elapsed = System.currentTimeMillis() - lastCommand
        if (elapsed >= delay) return if (itemId == queued?.first) 1f else null
        return elapsed.toFloat() / delay
    }
}
