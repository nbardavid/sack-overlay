package io.github.sackoverlay

import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.ItemStack
import org.slf4j.LoggerFactory
import tech.thatgravyboat.skyblockapi.api.datatype.DataTypes
import tech.thatgravyboat.skyblockapi.api.datatype.getData
import tech.thatgravyboat.skyblockapi.api.events.base.Subscription
import tech.thatgravyboat.skyblockapi.api.events.base.predicates.OnlyOnSkyBlock
import tech.thatgravyboat.skyblockapi.api.events.hypixel.SacksChangeEvent
import tech.thatgravyboat.skyblockapi.api.events.profile.ProfileChangeEvent
import tech.thatgravyboat.skyblockapi.api.events.screen.InventoryChangeEvent
import tech.thatgravyboat.skyblockapi.api.profile.items.sacks.SacksAPI
import tech.thatgravyboat.skyblockapi.api.profile.profile.ProfileAPI
import tech.thatgravyboat.skyblockapi.api.repo.apis.SkyBlockItemsRepo
import tech.thatgravyboat.skyblockapi.helpers.McPlayer
import tech.thatgravyboat.skyblockapi.utils.extentions.getRawLore
import tech.thatgravyboat.skyblockapi.utils.extentions.isSkyblockFiller
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Everything the mod knows about the player's sacks.
 * - Which item goes in which sack: sacks.json, taken from SkyOcean's data repository (meowdding-repo, MIT).
 * - Amounts: SacksAPI.sackItems (the [Sacks] chat messages and the sack menus).
 * - Sack of Sacks slots and capacities: read when the player opens those menus.
 */
object SackStore {

    private val logger = LoggerFactory.getLogger("SackOverlay")

    /** Per-item capacity read from a family's menu, together with the sacks equipped at that time. */
    data class FamilyCapacity(val sacks: List<String> = emptyList(), val perItem: Int = 0)

    /** What differs between SkyBlock profiles: the equipped sacks and their capacities. */
    data class ProfileData(
        /** Sack of Sacks slots in order: a sack id, or null for a free slot. */
        var sackSlots: MutableList<String?> = mutableListOf(),
        /** Per-item capacity of each family, valid while the same sacks stay equipped. */
        var familyCapacity: MutableMap<String, FamilyCapacity> = mutableMapOf(),
        /** Capacity learned for a single sack, by id (e.g. BEGINNER_COMBAT_SACK -> 640). */
        var sackCapacity: MutableMap<String, Int> = mutableMapOf(),
    )

    data class Data(
        /** Per-profile data, keyed by "<player uuid>/<profile name>". */
        var profiles: MutableMap<String, ProfileData> = mutableMapOf(),
        /** Exact item order of each sack menu, recorded when the player opens it. Same on every profile. */
        var layouts: MutableMap<String, MutableList<String>> = mutableMapOf(),
        /** Page order chosen by dragging pages around; families not listed keep their default place after these. */
        var pageOrder: MutableList<String> = mutableListOf(),
        // Saved before data was split per profile; moved into the first profile seen
        var sackSlots: MutableList<String?>? = null,
        var familyCapacity: MutableMap<String, FamilyCapacity>? = null,
        var sackCapacity: MutableMap<String, Int>? = null,
    )

    fun setPageOrder(order: List<String>) {
        data.pageOrder = order.toMutableList()
        save()
    }

    fun resetPageOrder() = setPageOrder(emptyList())

    private data class SackDef(val sack: String = "", val items: List<String> = emptyList())

    var data = Data()
        private set

    // Used while the profile is not known yet (just joined); never saved
    private val unknownProfile = ProfileData()

    /** Data of the current SkyBlock profile. */
    private val profile: ProfileData
        get() {
            val name = ProfileAPI.profileName?.takeIf { it.isNotBlank() } ?: return unknownProfile
            val uuid = McPlayer.self?.uuid ?: return unknownProfile
            val key = "$uuid/$name"
            data.profiles[key]?.let { return it }
            // Data saved before the split per profile goes to the first profile seen
            val created = ProfileData(
                data.sackSlots ?: mutableListOf(),
                data.familyCapacity ?: mutableMapOf(),
                data.sackCapacity ?: mutableMapOf(),
            )
            data.profiles[key] = created
            if (data.sackSlots != null || data.familyCapacity != null || data.sackCapacity != null) {
                data.sackSlots = null
                data.familyCapacity = null
                data.sackCapacity = null
                save()
            }
            return created
        }

    /** Sack of Sacks slots of the current profile: a sack id, or null for a free slot. */
    val sackSlots: List<String?> get() = profile.sackSlots

    /** Sack family (e.g. AGRONOMY_SACK) -> ids of the items it accepts, in file order. */
    var families: Map<String, List<String>> = emptyMap()
        private set

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val file get() = FabricLoader.getInstance().configDir.resolve("sackoverlay/data.json")

    // Stacks seen in game are used as icons when the repo doesn't know an item
    private val seenStacks = mutableMapOf<String, ItemStack>()
    private var lastTitle = ""

    private val sizePrefix = Regex("^(BEGINNER|SMALL|MEDIUM|LARGE)_")
    private val storedRegex = Regex("Stored: ([\\d,.]+)/([\\d,.]+[kM]?)")

    // Wiki capacities (BEGINNER seen in game), used until the player opens the family's menu
    private val sizeCapacity = mapOf("BEGINNER" to 640, "SMALL" to 2_240, "MEDIUM" to 6_720, "LARGE" to 20_160)
    private val gemCapacity = mapOf("SMALL" to 128_000, "MEDIUM" to 512_000, "LARGE" to 2_560_000)

    fun load() {
        runCatching {
            val json = SackStore::class.java.getResourceAsStream("/assets/sackoverlay/sacks.json")!!.reader().readText()
            families = gson.fromJson(json, Array<SackDef>::class.java)
                .associate { familyOf(it.sack) to it.items }
        }.onFailure { logger.error("Could not read sacks.json", it) }
        runCatching {
            if (file.exists()) data = gson.fromJson(file.readText(), Data::class.java) ?: Data()
        }.onFailure { logger.error("Could not read data.json", it) }
    }

    private fun save() {
        runCatching {
            file.parent.createDirectories()
            file.writeText(gson.toJson(data))
        }.onFailure { logger.error("Could not write data.json", it) }
    }

    // ---------- Families and capacities ----------

    /**
     * LARGE_AGRONOMY_SACK -> AGRONOMY_SACK: every size of a family shares the same items.
     * Picks the longest known family that ends the id, so LARGE_ENCHANTED_MINING_SACK
     * is never taken for MINING_SACK, whatever the size prefix.
     */
    fun familyOf(sackId: String): String =
        families.keys.filter { sackId == it || sackId.endsWith("_$it") }.maxByOrNull { it.length }
            ?: sizePrefix.replace(sackId, "")

    /** "Mining Sack" -> MINING_SACK, "Gemstones Sack" -> GEMSTONE_SACK. */
    private fun familyOfTitle(title: String): String {
        val key = title.uppercase().replace(' ', '_')
        val singular = key.split('_').joinToString("_") { it.removeSuffix("S").ifEmpty { it } }
        return listOf(key, singular, familyOf(key), familyOf(singular)).firstOrNull { it in families } ?: familyOf(key)
    }

    /** Sacks of this family currently in the Sack of Sacks, sorted. */
    fun equippedOf(family: String): List<String> =
        profile.sackSlots.filterNotNull().filter { familyOf(it) == family }.sorted()

    /** Families of the equipped sacks, in Sack of Sacks order. */
    fun ownedFamilies(): List<String> = profile.sackSlots.filterNotNull().map(::familyOf).distinct()

    /**
     * How much each slot of the family can hold. The value read from the menu is only trusted while the
     * same sacks are equipped; otherwise it is the sum of each sack's known capacity, or null if one is unknown.
     */
    fun familyCapacity(family: String): Int? {
        val equipped = equippedOf(family)
        if (equipped.isEmpty()) return null
        profile.familyCapacity[family]?.takeIf { it.sacks == equipped }?.let { return it.perItem }
        val parts = equipped.map { unitCapacity(it) ?: return null }
        return parts.sum()
    }

    private fun unitCapacity(sackId: String): Int? {
        profile.sackCapacity[sackId]?.let { return it }
        val size = sizePrefix.find(sackId)?.groupValues?.get(1) ?: return null
        return (if ("GEMSTONE" in sackId) gemCapacity else sizeCapacity)[size]
    }

    // ---------- Amounts ----------

    // Changes the player just asked for, not yet confirmed by a [Sacks] message: id -> (delta, time)
    private val pending = mutableMapOf<String, Pair<Int, Long>>()
    private const val PENDING_TTL_MS = 30_000L

    /** Amount to display: SacksAPI's value, adjusted by withdrawals and deposits the player just made. */
    fun count(itemId: String): Int {
        val base = SacksAPI.sackItems[itemId] ?: 0
        if (!SackConfig.get.instantUpdates) return base
        val (delta, at) = pending[itemId] ?: return base
        if (System.currentTimeMillis() - at > PENDING_TTL_MS) {
            pending.remove(itemId)
            return base
        }
        return (base + delta).coerceAtLeast(0)
    }

    private fun addPending(itemId: String, delta: Int) {
        val previous = pending[itemId]?.first ?: 0
        pending[itemId] = (previous + delta) to System.currentTimeMillis()
    }

    /** Called on a withdraw click: the display drops right away instead of waiting for the [Sacks] message. */
    fun onWithdrawRequested(itemId: String, amount: Int) = addPending(itemId, -amount)

    /**
     * Called on an "Insert Inventory" click: estimates what will go in (items accepted by an equipped sack,
     * up to the known capacity). The [Sacks] message brings the real values afterwards.
     */
    fun onInsertRequested(inventory: List<ItemStack>) {
        val owned = ownedFamilies()
        val added = mutableMapOf<String, Int>()
        for (stack in inventory) {
            if (stack.isEmpty) continue
            val id = stack.getData(DataTypes.ID) ?: continue
            val family = owned.firstOrNull { families[it]?.contains(id) == true } ?: continue
            val already = added[id] ?: 0
            val room = familyCapacity(family)?.let { (it - count(id) - already).coerceAtLeast(0) } ?: Int.MAX_VALUE
            val amount = minOf(stack.count, room)
            if (amount > 0) added[id] = already + amount
        }
        added.forEach { (id, amount) -> addPending(id, amount) }
    }

    /** A [Sacks] message arrived: SacksAPI now has the real value, so the estimate is dropped. */
    @Subscription
    fun onSacksChange(event: SacksChangeEvent) {
        event.changedItems.forEach { pending.remove(it.item) }
    }

    /** Estimates and menu stacks (with their "Stored:" line) belong to the previous profile. */
    @Subscription
    fun onProfileChange(event: ProfileChangeEvent) {
        pending.clear()
        seenStacks.clear()
    }

    /**
     * How many of this item fit in the player's main inventory (36 slots): free slots at full stack size,
     * plus the room left in stacks of the same item.
     */
    fun inventoryRoom(itemId: String): Int {
        val inventory = McPlayer.self?.inventory ?: return 0
        val maxStack = stackOf(itemId).maxStackSize.coerceAtLeast(1)
        var room = 0
        for (i in 0 until 36) {
            val stack = inventory.getItem(i)
            when {
                stack.isEmpty -> room += maxStack
                stack.getData(DataTypes.ID) == itemId -> room += (maxStack - stack.count).coerceAtLeast(0)
            }
        }
        return room
    }

    /**
     * The item exactly as the sack menu showed it (with its "Stored:" lore), if it was seen this session.
     * Used for the tooltip, so other mods (prices...) see the same item as in Hypixel's menu.
     */
    fun menuStack(itemId: String): ItemStack? =
        seenStacks[itemId]?.takeIf { stack -> stack.getRawLore().any { storedRegex.containsMatchIn(it) } }

    fun stackOf(itemId: String): ItemStack =
        SkyBlockItemsRepo.getItemStack(itemId) ?: seenStacks[itemId] ?: SkyBlockItemsRepo.getItemStackOrDefault(itemId)

    // ---------- Reading menus ----------

    @Subscription
    @OnlyOnSkyBlock
    fun onInventoryChange(event: InventoryChangeEvent) {
        if (event.isInPlayerInventory) return
        val title = event.title
        if ("Sack" in title && title != lastTitle) {
            // Logged so the exact menu titles can be checked in game
            logger.info("Opened menu: \"{}\"", title)
            lastTitle = title
        }
        when {
            title.equals("Sack of Sacks", ignoreCase = true) -> readSackOfSacks(event)
            title.endsWith("Sack") -> {
                val family = familyOfTitle(title)
                readCapacity(event, family)
                readLayout(event, family)
            }
        }
    }

    /** Records the item order as Hypixel shows it, empty items included. */
    private fun readLayout(event: InventoryChangeEvent, family: String) {
        val layout = mutableListOf<String>()
        for (slot in event.inventory) {
            if (slot.container is Inventory) continue
            val stack = slot.item
            val id = stack.getData(DataTypes.ID) ?: continue
            val lore = stack.getRawLore()
            when {
                // Gemstones Sack: one slot per gem, with Rough / Flawed / Fine lines
                id.startsWith("ROUGH_") && lore.any { it.trim().startsWith("Flawed:") } ->
                    listOf("ROUGH", "FLAWED", "FINE").forEach { layout += id.replaceFirst("ROUGH", it) }
                lore.any { storedRegex.containsMatchIn(it) } -> layout += id
            }
            seenStacks.putIfAbsent(id, stack.copy())
        }
        if (layout.isNotEmpty() && data.layouts[family] != layout) {
            logger.info("Recorded item order for {}: {} items", family, layout.size)
            data.layouts[family] = layout
            save()
        }
    }

    /**
     * Rereads the whole menu on every change. A sack is an item whose id ends with _SACK.
     * A free slot is an empty (air) slot, which must not be mistaken for the decorative glass panes.
     */
    private fun readSackOfSacks(event: InventoryChangeEvent) {
        val container = event.inventory.filter { it.container !is Inventory }
        val slots = mutableListOf<String?>()
        for (slot in container) {
            val stack = slot.item
            if (stack.isEmpty) {
                slots += null
                continue
            }
            if (stack.isSkyblockFiller()) continue
            val id = stack.getData(DataTypes.ID) ?: continue
            if (id.endsWith("_SACK")) {
                slots += id
                seenStacks[id] = stack.copy()
            }
        }
        if (slots != profile.sackSlots) {
            logger.info("Read Sack of Sacks: {} sacks, {} free slots", slots.count { it != null }, slots.count { it == null })
            profile.sackSlots = slots
            save()
        }
    }

    /** Reads "Stored: X/Y": Y is the per-item capacity of the whole family with the sacks equipped now. */
    private fun readCapacity(event: InventoryChangeEvent, family: String) {
        val stack = event.item
        val id = stack.getData(DataTypes.ID) ?: return
        val stored = stack.getRawLore().firstNotNullOfOrNull { storedRegex.find(it) } ?: return
        seenStacks[id] = stack.copy()
        val cap = parseAmount(stored.groupValues[2])
        if (cap <= 0) return

        val equipped = equippedOf(family)
        val entry = FamilyCapacity(equipped, cap)
        if (profile.familyCapacity[family] == entry) return
        profile.familyCapacity[family] = entry
        // With a single sack type equipped, the capacity of one copy can be deduced
        if (equipped.isNotEmpty() && equipped.distinct().size == 1) {
            profile.sackCapacity[equipped.first()] = cap / equipped.size
        }
        save()
    }

    private fun parseAmount(text: String): Int {
        val clean = text.replace(",", "")
        val mult = when {
            clean.endsWith("k") -> 1_000.0
            clean.endsWith("M") -> 1_000_000.0
            else -> 1.0
        }
        return ((clean.trimEnd('k', 'M').toDoubleOrNull() ?: 0.0) * mult).toInt()
    }
}
