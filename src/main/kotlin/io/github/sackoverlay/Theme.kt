package io.github.sackoverlay

/**
 * Look of the whole interface. Frames follow the vanilla menu style, as Enhanced Storage does:
 * a black outline with cut corners, a raised inner border (light top-left, shadow bottom-right)
 * and bevelled slots (dark top-left, light bottom-right).
 */
enum class Theme(
    val frameOuter: Int,
    val frameLight: Int,
    val frameShadow: Int,
    val fill: Int,
    val slotDark: Int,
    val slotFill: Int,
    val slotLight: Int,
    val empty: Int,
    val selected: Int,
    val hover: Int,
    val zeroOverlay: Int,
    val dimOverlay: Int,
    val text: Int,
    val muted: Int,
    /** Light backgrounds use flat text, like vanilla menus; dark ones keep the shadow. */
    val shadow: Boolean,
) {
    /** Dark grey and slightly see-through: the world stays visible behind. */
    TRANSPARENT(
        frameOuter = 0xCC000000.toInt(), frameLight = 0xCC434343.toInt(), frameShadow = 0xCC434343.toInt(),
        fill = 0xCC1C1C1C.toInt(),
        slotDark = 0xCC000000.toInt(), slotFill = 0xCC282828.toInt(), slotLight = 0xCC434343.toInt(),
        empty = 0x80434343.toInt(), selected = 0xFFF5C842.toInt(), hover = 0x50FFFFFF,
        zeroOverlay = 0xB01C1C1C.toInt(), dimOverlay = 0xD01C1C1C.toInt(),
        text = 0xFFAAAAAA.toInt(), muted = 0xFF808080.toInt(), shadow = true,
    ),

    /** The vanilla menu look: light grey, white highlight, dark grey shadow, dark text. */
    LIGHT(
        frameOuter = 0xFF000000.toInt(), frameLight = 0xFFFFFFFF.toInt(), frameShadow = 0xFF555555.toInt(),
        fill = 0xFFC6C6C6.toInt(),
        slotDark = 0xFF373737.toInt(), slotFill = 0xFF8B8B8B.toInt(), slotLight = 0xFFFFFFFF.toInt(),
        empty = 0x80555555.toInt(), selected = 0xFFC8930A.toInt(), hover = 0x80FFFFFF.toInt(),
        zeroOverlay = 0xB0C6C6C6.toInt(), dimOverlay = 0xD0C6C6C6.toInt(),
        text = 0xFF404040.toInt(), muted = 0xFF555555.toInt(), shadow = false,
    ),

    /** Opaque dark grey. */
    DARK(
        frameOuter = 0xFF000000.toInt(), frameLight = 0xFF434343.toInt(), frameShadow = 0xFF434343.toInt(),
        fill = 0xFF1C1C1C.toInt(),
        slotDark = 0xFF000000.toInt(), slotFill = 0xFF282828.toInt(), slotLight = 0xFF434343.toInt(),
        empty = 0x80434343.toInt(), selected = 0xFFF5C842.toInt(), hover = 0x50FFFFFF,
        zeroOverlay = 0xB01C1C1C.toInt(), dimOverlay = 0xD01C1C1C.toInt(),
        text = 0xFFAAAAAA.toInt(), muted = 0xFF808080.toInt(), shadow = true,
    ),
    ;

    companion object {
        val current: Theme get() = SackConfig.get.theme
    }
}
