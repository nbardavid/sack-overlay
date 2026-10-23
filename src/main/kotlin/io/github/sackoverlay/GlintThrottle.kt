package io.github.sackoverlay

import tech.thatgravyboat.skyblockapi.helpers.McScreen

/** How often animated items (enchant glint) are redrawn while one of our screens is open. */
enum class GlintRefresh(val intervalMillis: Long) {
    SMOOTH(0),
    FPS_30(1000L / 30),
    FPS_20(1000L / 20),
}

/**
 * The game's GUI item atlas draws a static item once and reuses it, but frees animated ones (enchant glint) after
 * every frame, so each enchanted item is fully redrawn every frame. With pages full of enchanted items that is the
 * main cost of our screens. While one is open, the "free after this frame" flag is only let through on refresh
 * frames; in between, the atlas keeps the last image like it does for a static item.
 */
object GlintThrottle {

    private var refreshThisFrame = true
    private var nextRefreshAt = 0L

    /** Throttled value of the atlas's "free this slot after the frame" flag. */
    @JvmStatic
    fun discardAfterFrame(animated: Boolean): Boolean = animated && refreshThisFrame

    /** Called once per frame, after everything was drawn: decides whether the next frame refreshes animated items. */
    @JvmStatic
    fun endFrame() {
        if (!isLimiting()) {
            refreshThisFrame = true
            return
        }
        val now = System.currentTimeMillis()
        refreshThisFrame = now >= nextRefreshAt
        if (refreshThisFrame) nextRefreshAt = now + SackConfig.get.glintRefresh.intervalMillis
    }

    /** True while one of our screens is open and the refresh is limited. */
    @JvmStatic
    fun isLimiting(): Boolean {
        if (SackConfig.get.glintRefresh.intervalMillis <= 0) return false
        val screen = McScreen.self
        return screen is SackContainerScreen || screen is SackOverlayScreen
    }
}
