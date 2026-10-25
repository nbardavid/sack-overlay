package io.github.sackoverlay

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.github.notenoughupdates.moulconfig.Config
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.Category
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDropdown
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import io.github.notenoughupdates.moulconfig.common.text.StructuredText
import io.github.notenoughupdates.moulconfig.gui.GuiContext
import io.github.notenoughupdates.moulconfig.gui.GuiElementComponent
import io.github.notenoughupdates.moulconfig.gui.MoulConfigEditor
import io.github.notenoughupdates.moulconfig.platform.MoulConfigScreenComponent
import io.github.notenoughupdates.moulconfig.processor.BuiltinMoulConfigGuis
import io.github.notenoughupdates.moulconfig.processor.ConfigProcessorDriver
import io.github.notenoughupdates.moulconfig.processor.MoulConfigProcessor
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.resources.language.I18n
import net.minecraft.network.chat.Component
import org.slf4j.LoggerFactory
import tech.thatgravyboat.skyblockapi.helpers.McClient
import java.lang.reflect.Field

/** How each sack page lists its items. */
enum class DisplayMode {
    /** Every item the sack accepts, in the in-game order, with missing ones greyed out. */
    HYPIXEL,

    /** Only the items you own, each shown once. */
    COMPACT,
    ;

    override fun toString() = SackConfig.valueName("displayMode", this)
}

/** Order of the items inside each sack page. */
enum class ItemOrder {
    /** Grouped by source, lower tiers first, compressed forms next to their base (sort.json). */
    LOGICAL,

    /** Hypixel's menu order, mostly alphabetical. */
    HYPIXEL,
    ;

    override fun toString() = SackConfig.valueName("itemOrder", this)
}

/** What the enchanted key does. */
enum class EnchantedKey {
    /** Each press flips sacks to the other side. */
    TOGGLE,

    /** The other side is shown while the key is held. */
    HOLD,
    ;

    override fun toString() = SackConfig.valueName("enchantedKey", this)
}

/** What the search box does to items that don't match. */
enum class SearchMode {
    /** Hide them, and hide pages left without any match. */
    FILTER,

    /** Keep every page in place and grey out the items that don't match. */
    DIM,
    ;

    override fun toString() = SackConfig.valueName("searchMode", this)
}

/**
 * User settings, saved to config/sackoverlay/config.json and edited with MoulConfig, the settings screen of SkyHanni
 * and NEU. Names and descriptions in the annotations are lang keys, translated when the screen opens.
 * The rest of the mod reads the flat properties at the bottom.
 */
class SackConfig : Config() {

    @JvmField
    @Category(name = K + "category.general", desc = K + "category.general.desc")
    var general = General()

    @JvmField
    @Category(name = K + "category.pages", desc = K + "category.pages.desc")
    var pages = Pages()

    @JvmField
    @Category(name = K + "category.withdraw", desc = K + "category.withdraw.desc")
    var withdraw = Withdraw()

    @JvmField
    @Category(name = K + "category.performance", desc = K + "category.performance.desc")
    var performance = Performance()

    class General {
        @JvmField
        @ConfigOption(name = K + "replaceSackScreen", desc = K + "replaceSackScreen.desc")
        @ConfigEditorBoolean
        var replaceSackScreen = true

        @JvmField
        @ConfigOption(name = K + "theme", desc = K + "theme.desc")
        @ConfigEditorDropdown
        var theme = Theme.TRANSPARENT

        @JvmField
        @ConfigOption(name = K + "rarityBackgrounds", desc = K + "rarityBackgrounds.desc")
        @ConfigEditorBoolean
        var rarityBackgrounds = true

        @JvmField
        @ConfigOption(name = K + "maxColumns", desc = K + "maxColumns.desc")
        @ConfigEditorSlider(minValue = 1f, maxValue = 4f, minStep = 1f)
        var maxColumns = 4f
    }

    class Pages {
        @JvmField
        @ConfigOption(name = K + "displayMode", desc = K + "displayMode.desc")
        @ConfigEditorDropdown
        var displayMode = DisplayMode.HYPIXEL

        @JvmField
        @ConfigOption(name = K + "itemOrder", desc = K + "itemOrder.desc")
        @ConfigEditorDropdown
        var itemOrder = ItemOrder.LOGICAL

        @JvmField
        @ConfigOption(name = K + "combineEnchanted", desc = K + "combineEnchanted.desc")
        @ConfigEditorBoolean
        var combineEnchanted = false

        @JvmField
        @ConfigOption(name = K + "enchantedKey", desc = K + "enchantedKey.desc")
        @ConfigEditorDropdown
        var enchantedKey = EnchantedKey.TOGGLE

        @Transient
        @JvmField
        @ConfigOption(name = K + "resetPageOrder", desc = K + "resetPageOrder.desc")
        @ConfigEditorButton(buttonText = "Reset")
        var resetPageOrder = Runnable { SackStore.resetPageOrder() }

        @JvmField
        @ConfigOption(name = K + "group.search", desc = "")
        @Accordion
        var search = Search()

        @Transient
        @JvmField
        @ConfigOption(name = K + "group.hiddenPages", desc = K + "group.hiddenPages.desc")
        @Accordion
        var hiddenPages = HiddenPages()
    }

    class Search {
        @JvmField
        @ConfigOption(name = K + "searchMode", desc = K + "searchMode.desc")
        @ConfigEditorDropdown
        var searchMode = SearchMode.FILTER

        @JvmField
        @ConfigOption(name = K + "rememberSearch", desc = K + "rememberSearch.desc")
        @ConfigEditorBoolean
        var rememberSearch = false
    }

    /** Hidden pages live in SackStore's data; this only holds the editor that lists them. */
    class HiddenPages {
        @Transient
        @JvmField
        @ConfigOption(name = K + "group.hiddenPages", desc = K + "group.hiddenPages.desc")
        @HiddenPagesEditor.Marker
        var list = false
    }

    class Withdraw {
        @JvmField
        @ConfigOption(name = K + "leftClickFillsInventory", desc = K + "leftClickFillsInventory.desc")
        @ConfigEditorBoolean
        var leftClickFillsInventory = true

        @JvmField
        @ConfigOption(name = K + "leftClickAmount", desc = K + "leftClickAmount.desc")
        @ConfigEditorSlider(minValue = 1f, maxValue = 2240f, minStep = 1f)
        var leftClickAmount = 64f

        @JvmField
        @ConfigOption(name = K + "rightClickStack", desc = K + "rightClickStack.desc")
        @ConfigEditorSlider(minValue = 1f, maxValue = 2240f, minStep = 1f)
        var rightClickStack = 64f

        @JvmField
        @ConfigOption(name = K + "withdrawDelayMs", desc = K + "withdrawDelayMs.desc")
        @ConfigEditorSlider(minValue = 100f, maxValue = 1000f, minStep = 50f)
        var withdrawDelayMs = 500f
    }

    class Performance {
        @JvmField
        @ConfigOption(name = K + "glintRefresh", desc = K + "glintRefresh.desc")
        @ConfigEditorDropdown
        var glintRefresh = GlintRefresh.FPS_30

        @JvmField
        @ConfigOption(name = K + "cachePages", desc = K + "cachePages.desc")
        @ConfigEditorBoolean
        var cachePages = true
    }

    override fun getTitle(): StructuredText = StructuredText.of("Sack Overlay")

    // Flat access for the rest of the mod
    val replaceSackScreen get() = general.replaceSackScreen
    var theme
        get() = general.theme
        set(value) {
            general.theme = value
        }
    val rarityBackgrounds get() = general.rarityBackgrounds
    val maxColumns get() = general.maxColumns.toInt()
    var displayMode
        get() = pages.displayMode
        set(value) {
            pages.displayMode = value
        }
    val itemOrder get() = pages.itemOrder
    val combineEnchanted get() = pages.combineEnchanted
    val enchantedKey get() = pages.enchantedKey
    val searchMode get() = pages.search.searchMode
    val rememberSearch get() = pages.search.rememberSearch
    val leftClickFillsInventory get() = withdraw.leftClickFillsInventory
    val leftClickAmount get() = withdraw.leftClickAmount.toInt()
    val rightClickStack get() = withdraw.rightClickStack.toInt()
    val withdrawDelayMs get() = withdraw.withdrawDelayMs.toInt()
    val glintRefresh get() = performance.glintRefresh
    val cachePages get() = performance.cachePages

    companion object {
        private const val K = "sackoverlay.config."

        private val logger = LoggerFactory.getLogger("sackoverlay")
        private val file = FabricLoader.getInstance().configDir.resolve("sackoverlay/config.json").toFile()
        private val gson = GsonBuilder().setPrettyPrinting().create()

        var get = SackConfig()
            private set

        /** Shown name of an enum value in the settings: "<key>.<value in lowercase>" in the lang file. */
        fun valueName(key: String, value: Enum<*>): String = I18n.get(K + key + "." + value.name.lowercase())

        fun load() {
            get = try {
                if (file.exists()) {
                    val json = JsonParser.parseString(file.readText()).asJsonObject
                    gson.fromJson(if (json.has("general")) json else migrate(json), SackConfig::class.java)
                } else {
                    SackConfig()
                }
            } catch (e: Exception) {
                logger.warn("Could not read the settings, using the defaults", e)
                SackConfig()
            }
        }

        fun save() {
            try {
                file.parentFile.mkdirs()
                file.writeText(gson.toJson(get))
            } catch (e: Exception) {
                logger.warn("Could not save the settings", e)
            }
        }

        /** Settings saved before the move to MoulConfig were flat (YACL); each one goes into its category. */
        private fun migrate(old: JsonObject): JsonObject {
            val categories = mapOf(
                "general" to listOf("replaceSackScreen", "theme", "rarityBackgrounds", "maxColumns"),
                "pages" to listOf("displayMode", "itemOrder", "combineEnchanted", "enchantedKey"),
                "withdraw" to listOf("leftClickFillsInventory", "leftClickAmount", "rightClickStack", "withdrawDelayMs"),
                "performance" to listOf("glintRefresh", "cachePages"),
            )
            val json = JsonObject()
            for ((category, keys) in categories) {
                val obj = JsonObject()
                keys.filter(old::has).forEach { obj.add(it, old[it]) }
                json.add(category, obj)
            }
            val search = JsonObject()
            listOf("searchMode", "rememberSearch").filter(old::has).forEach { search.add(it, old[it]) }
            json.getAsJsonObject("pages").add("search", search)
            return json
        }

        fun screen(parent: Screen?): Screen {
            val processor = TranslatedProcessor(get)
            BuiltinMoulConfigGuis.addProcessors(processor)
            processor.registerConfigEditor(HiddenPagesEditor.Marker::class.java) { option, _ -> HiddenPagesEditor(option) }
            ConfigProcessorDriver(processor).apply { checkExpose = false }.processConfig(get)
            return SettingsScreen(GuiContext(GuiElementComponent(MoulConfigEditor(processor))), parent)
        }
    }

    /** Saves when closed and goes back to the screen it was opened from (Mod Menu, /sackoverlay). */
    private class SettingsScreen(context: GuiContext, private val parent: Screen?) :
        MoulConfigScreenComponent(Component.literal("Sack Overlay"), context, parent) {

        override fun removed() {
            super.removed()
            save()
        }

        override fun onClose() {
            super.onClose()
            if (parent != null) McClient.setScreen(parent)
        }
    }

    /** Reads the lang keys of the annotations in the current language. */
    private class TranslatedProcessor(config: SackConfig) : MoulConfigProcessor<SackConfig>(config) {
        override fun beginCategory(baseObject: Any, field: Field, name: String, description: String) =
            super.beginCategory(baseObject, field, tr(name), tr(description))

        override fun beginAccordion(baseObject: Any, field: Field, option: ConfigOption, id: Int) =
            super.beginAccordion(baseObject, field, tr(option), id)

        override fun emitOption(baseObject: Any, field: Field, option: ConfigOption) =
            super.emitOption(baseObject, field, tr(option))

        private fun tr(key: String) = if (key.isEmpty()) "" else I18n.get(key)

        private fun tr(option: ConfigOption) = ConfigOption(name = tr(option.name), desc = tr(option.desc))
    }
}
