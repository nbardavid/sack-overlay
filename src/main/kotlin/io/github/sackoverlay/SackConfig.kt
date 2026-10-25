package io.github.sackoverlay

import dev.isxander.yacl3.api.ButtonOption
import dev.isxander.yacl3.api.ConfigCategory
import dev.isxander.yacl3.api.Option
import dev.isxander.yacl3.api.OptionDescription
import dev.isxander.yacl3.api.OptionGroup
import dev.isxander.yacl3.api.YetAnotherConfigLib
import dev.isxander.yacl3.api.controller.EnumControllerBuilder
import dev.isxander.yacl3.api.controller.IntegerFieldControllerBuilder
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder
import dev.isxander.yacl3.config.v2.api.ConfigClassHandler
import dev.isxander.yacl3.config.v2.api.SerialEntry
import dev.isxander.yacl3.config.v2.api.serializer.GsonConfigSerializerBuilder
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier

/** How each sack page lists its items. */
enum class DisplayMode {
    /** Every item the sack accepts, in the in-game order, with missing ones greyed out. */
    HYPIXEL,

    /** Only the items you own, each shown once. */
    COMPACT,
}

/** Order of the items inside each sack page. */
enum class ItemOrder {
    /** Grouped by source, lower tiers first, compressed forms next to their base (sort.json). */
    LOGICAL,

    /** Hypixel's menu order, mostly alphabetical. */
    HYPIXEL,
}

/** What the search box does to items that don't match. */
enum class SearchMode {
    /** Hide them, and hide pages left without any match. */
    FILTER,

    /** Keep every page in place and grey out the items that don't match. */
    DIM,
}

/** User settings, saved to config/sackoverlay/config.json and edited through YACL like Skyblocker's. */
class SackConfig {
    @SerialEntry @JvmField var replaceSackScreen = true
    @SerialEntry @JvmField var theme = Theme.TRANSPARENT
    @SerialEntry @JvmField var displayMode = DisplayMode.HYPIXEL
    @SerialEntry @JvmField var itemOrder = ItemOrder.LOGICAL
    @SerialEntry @JvmField var rarityBackgrounds = true
    @SerialEntry @JvmField var maxColumns = 4
    @SerialEntry @JvmField var leftClickFillsInventory = true
    @SerialEntry @JvmField var leftClickAmount = 64
    @SerialEntry @JvmField var rightClickStack = 64
    @SerialEntry @JvmField var withdrawDelayMs = 500
    @SerialEntry @JvmField var instantUpdates = true
    @SerialEntry @JvmField var glintRefresh = GlintRefresh.FPS_30
    @SerialEntry @JvmField var cachePages = true
    @SerialEntry @JvmField var searchMode = SearchMode.FILTER
    @SerialEntry @JvmField var rememberSearch = false
    @SerialEntry @JvmField var rememberScroll = true

    companion object {
        private val HANDLER: ConfigClassHandler<SackConfig> = ConfigClassHandler.createBuilder(SackConfig::class.java)
            .id(Identifier.fromNamespaceAndPath("sackoverlay", "config"))
            .serializer { config ->
                GsonConfigSerializerBuilder.create(config)
                    .setPath(FabricLoader.getInstance().configDir.resolve("sackoverlay/config.json"))
                    .build()
            }
            .build()

        val get: SackConfig get() = HANDLER.instance()

        fun load() {
            HANDLER.load()
        }

        fun save() {
            HANDLER.save()
        }

        fun screen(parent: Screen?): Screen {
            val defaults = HANDLER.defaults()
            val config = get
            return YetAnotherConfigLib.createBuilder()
                .title(t("title"))
                .category(
                    ConfigCategory.createBuilder()
                        .name(t("category.general"))
                        .group(
                            OptionGroup.createBuilder()
                                .name(t("group.display"))
                                .option(
                                    Option.createBuilder<Boolean>()
                                        .name(t("replaceSackScreen"))
                                        .description(OptionDescription.of(t("replaceSackScreen.desc")))
                                        .binding(defaults.replaceSackScreen, { config.replaceSackScreen }, { config.replaceSackScreen = it })
                                        .controller(TickBoxControllerBuilder::create)
                                        .build(),
                                )
                                .option(
                                    Option.createBuilder<Theme>()
                                        .name(t("theme"))
                                        .description(OptionDescription.of(t("theme.desc")))
                                        .binding(defaults.theme, { config.theme }, { config.theme = it })
                                        .controller { option ->
                                            EnumControllerBuilder.create(option)
                                                .enumClass(Theme::class.java)
                                                .formatValue { t("theme.${it.name.lowercase()}") }
                                        }
                                        .build(),
                                )
                                .option(
                                    Option.createBuilder<DisplayMode>()
                                        .name(t("displayMode"))
                                        .description(OptionDescription.of(t("displayMode.desc")))
                                        .binding(defaults.displayMode, { config.displayMode }, { config.displayMode = it })
                                        .controller { option ->
                                            EnumControllerBuilder.create(option)
                                                .enumClass(DisplayMode::class.java)
                                                .formatValue { t("displayMode.${it.name.lowercase()}") }
                                        }
                                        .build(),
                                )
                                .option(
                                    Option.createBuilder<ItemOrder>()
                                        .name(t("itemOrder"))
                                        .description(OptionDescription.of(t("itemOrder.desc")))
                                        .binding(defaults.itemOrder, { config.itemOrder }, { config.itemOrder = it })
                                        .controller { option ->
                                            EnumControllerBuilder.create(option)
                                                .enumClass(ItemOrder::class.java)
                                                .formatValue { t("itemOrder.${it.name.lowercase()}") }
                                        }
                                        .build(),
                                )
                                .option(
                                    Option.createBuilder<Int>()
                                        .name(t("maxColumns"))
                                        .description(OptionDescription.of(t("maxColumns.desc")))
                                        .binding(defaults.maxColumns, { config.maxColumns }, { config.maxColumns = it })
                                        .controller { option -> IntegerSliderControllerBuilder.create(option).range(1, 4).step(1) }
                                        .build(),
                                )
                                .option(
                                    Option.createBuilder<GlintRefresh>()
                                        .name(t("glintRefresh"))
                                        .description(OptionDescription.of(t("glintRefresh.desc")))
                                        .binding(defaults.glintRefresh, { config.glintRefresh }, { config.glintRefresh = it })
                                        .controller { option ->
                                            EnumControllerBuilder.create(option)
                                                .enumClass(GlintRefresh::class.java)
                                                .formatValue { t("glintRefresh.${it.name.lowercase()}") }
                                        }
                                        .build(),
                                )
                                .option(bool("rarityBackgrounds", defaults.rarityBackgrounds, { config.rarityBackgrounds }, { config.rarityBackgrounds = it }))
                                .option(bool("cachePages", defaults.cachePages, { config.cachePages }, { config.cachePages = it }))
                                .option(
                                    ButtonOption.createBuilder()
                                        .name(t("resetPageOrder"))
                                        .description(OptionDescription.of(t("resetPageOrder.desc")))
                                        .action { _, _ -> SackStore.resetPageOrder() }
                                        .build(),
                                )
                                .option(
                                    Option.createBuilder<Boolean>()
                                        .name(t("instantUpdates"))
                                        .description(OptionDescription.of(t("instantUpdates.desc")))
                                        .binding(defaults.instantUpdates, { config.instantUpdates }, { config.instantUpdates = it })
                                        .controller(TickBoxControllerBuilder::create)
                                        .build(),
                                )
                                .build(),
                        )
                        .group(hiddenPages())
                        .group(
                            OptionGroup.createBuilder()
                                .name(t("group.search"))
                                .option(
                                    Option.createBuilder<SearchMode>()
                                        .name(t("searchMode"))
                                        .description(OptionDescription.of(t("searchMode.desc")))
                                        .binding(defaults.searchMode, { config.searchMode }, { config.searchMode = it })
                                        .controller { option ->
                                            EnumControllerBuilder.create(option)
                                                .enumClass(SearchMode::class.java)
                                                .formatValue { t("searchMode.${it.name.lowercase()}") }
                                        }
                                        .build(),
                                )
                                .option(bool("rememberSearch", defaults.rememberSearch, { config.rememberSearch }, { config.rememberSearch = it }))
                                .option(bool("rememberScroll", defaults.rememberScroll, { config.rememberScroll }, { config.rememberScroll = it }))
                                .build(),
                        )
                        .group(
                            OptionGroup.createBuilder()
                                .name(t("group.withdraw"))
                                .description(OptionDescription.of(t("group.withdraw.desc")))
                                .option(
                                    Option.createBuilder<Boolean>()
                                        .name(t("leftClickFillsInventory"))
                                        .description(OptionDescription.of(t("leftClickFillsInventory.desc")))
                                        .binding(defaults.leftClickFillsInventory, { config.leftClickFillsInventory }, { config.leftClickFillsInventory = it })
                                        .controller(TickBoxControllerBuilder::create)
                                        .build(),
                                )
                                .option(
                                    Option.createBuilder<Int>()
                                        .name(t("leftClickAmount"))
                                        .description(OptionDescription.of(t("leftClickAmount.desc")))
                                        .binding(defaults.leftClickAmount, { config.leftClickAmount }, { config.leftClickAmount = it })
                                        .controller { option -> IntegerFieldControllerBuilder.create(option).range(1, 2240) }
                                        .build(),
                                )
                                .option(
                                    Option.createBuilder<Int>()
                                        .name(t("withdrawDelayMs"))
                                        .description(OptionDescription.of(t("withdrawDelayMs.desc")))
                                        .binding(defaults.withdrawDelayMs, { config.withdrawDelayMs }, { config.withdrawDelayMs = it })
                                        .controller { option -> IntegerSliderControllerBuilder.create(option).range(100, 1000).step(50) }
                                        .build(),
                                )
                                .option(
                                    Option.createBuilder<Int>()
                                        .name(t("rightClickStack"))
                                        .binding(defaults.rightClickStack, { config.rightClickStack }, { config.rightClickStack = it })
                                        .controller { option -> IntegerFieldControllerBuilder.create(option).range(1, 2240) }
                                        .build(),
                                )
                                .build(),
                        )
                        .build(),
                )
                .save(::save)
                .build()
                .generateScreen(parent)
        }

        private fun t(key: String): Component = Component.translatable("sackoverlay.config.$key")

        /** One tick box per sack page, folded by default since the list is long. Hidden pages still show while searching. */
        private fun hiddenPages(): OptionGroup {
            val pages = SackStore.families.keys.map { it to Gui.familyTitle(it, 1) }.sortedBy { it.second.string } +
                ("OTHER" to Gui.tr("others"))
            val group = OptionGroup.createBuilder()
                .name(t("group.hiddenPages"))
                .description(OptionDescription.of(t("group.hiddenPages.desc")))
                .collapsed(true)
            for ((family, title) in pages) {
                group.option(
                    Option.createBuilder<Boolean>()
                        .name(title)
                        .binding(false, { SackStore.isHidden(family) }, { SackStore.setHidden(family, it) })
                        .controller(TickBoxControllerBuilder::create)
                        .build(),
                )
            }
            return group.build()
        }

        /** A tick box option with a name and a description, both taken from the lang file. */
        private fun bool(key: String, default: Boolean, get: () -> Boolean, set: (Boolean) -> Unit): Option<Boolean> =
            Option.createBuilder<Boolean>()
                .name(t(key))
                .description(OptionDescription.of(t("$key.desc")))
                .binding(default, get, set)
                .controller(TickBoxControllerBuilder::create)
                .build()
    }
}
