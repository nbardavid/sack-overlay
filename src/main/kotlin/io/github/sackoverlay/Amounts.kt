package io.github.sackoverlay

import java.util.Locale

/** How amounts are written on screen. Locale.ROOT keeps a dot as decimal separator whatever the system language. */
object Amounts {

    // Amount labels are drawn for every slot every frame: format each amount once
    private val labelCache = HashMap<Int, String>()
    private const val LABEL_CACHE_MAX = 4096

    /** Slot label of an amount, cached. */
    fun label(n: Int): String {
        if (labelCache.size > LABEL_CACHE_MAX) labelCache.clear()
        return labelCache.getOrPut(n) { short(n) }
    }

    /** At most 3 characters besides the dot: 999, 2.2k, 143k, 1.2M, 12M. */
    fun short(n: Int): String = when {
        n >= 10_000_000 -> "${n / 1_000_000}M"
        n >= 1_000_000 -> String.format(Locale.ROOT, "%.1fM", n / 1e6).replace(".0M", "M")
        n >= 10_000 -> "${n / 1000}k"
        n >= 1_000 -> String.format(Locale.ROOT, "%.1fk", n / 1e3).replace(".0k", "k")
        else -> n.toString()
    }

    /** Capacity the way Hypixel writes it: 640, 6.7k, 20.2k, 2.6M. */
    fun capacity(n: Int): String = when {
        n >= 1_000_000 -> String.format(Locale.ROOT, "%.1fM", n / 1e6).replace(".0M", "M")
        n >= 1_000 -> String.format(Locale.ROOT, "%.1fk", n / 1e3).replace(".0k", "k")
        else -> n.toString()
    }
}
