# Sack Overlay — project notes

Client-side Fabric mod (Minecraft 26.2, Kotlin) for Hypixel SkyBlock: replaces the `/sacks` (Sack of Sacks) screen with a clean overview of every sack.
Started in a Claude Desktop session; the full conversation export is `../conversation-claude-desktop.zip`.

## Working with the user
- The user is French-speaking: answer in French. Code, comments, README and mod description stay in English; UI strings go through `assets/sackoverlay/lang/en_us.json` + `fr_fr.json`.
- Strict minimum on screen, no clutter. Dark, square, NEU / Enhanced Storage look with vanilla-sized slots.
- Ban safety comes first. Only read what the game already shows. Never automate actions or poll Hypixel's API. The only thing sent to the server is `/gfs <ID> <amount>`, one command per player click, with a 300 ms cooldown. Same behaviour and rate as the big mods (SkyOcean, Skyblocker).
- "Sacks" means Hypixel's Sacks / Sack of Sacks, not backpacks.

## Architecture (`src/main/kotlin/io/github/sackoverlay`)
- `SackOverlay`: entrypoint. Registers on SkyblockAPI's event bus and adds the `/sackoverlay` and `/sackoverlay config` client commands.
- `SackStore`: data.
  - Amounts come from `SacksAPI.sackItems` (SkyblockAPI, same source as SkyOcean).
  - Withdrawals and "Insert Inventory" clicks are applied to the display right away, then dropped when the next `SacksChangeEvent` arrives or after 30 s.
  - The Sack of Sacks layout, per-sack item order and capacities are read from `InventoryChangeEvent` and saved in `config/sackoverlay/data.json`.
  - Equipped sacks and capacities are saved per profile (`profiles["<player uuid>/<profile name>"]`, from `ProfileAPI.profileName`); item order and page order are shared. Data from before the split moves into the first profile seen. Until the profile is known, an unsaved empty profile is used.
- Capacity:
  - The `Stored: X/Y` value read from a sack's menu is only trusted while the same sacks stay equipped.
  - Otherwise the capacity is the sum of each sack's known capacity: the wiki sizes, or a per-sack value learned from the menu (e.g. BEGINNER = 640).
- Item → sack mapping: `assets/sackoverlay/sacks.json`, taken from meowdding-repo (MIT, license file alongside).
- `Gui`: shared sizes, theme colors, `tr()` and the vanilla-style frames (`panel`, `card`, `slot`, `border`). `Amounts`: amount and capacity labels. `SackTooltip`: the page item tooltip. `WithdrawQueue`: the `/gfs` queue, one per screen.
- `SackPageView`: shared panel.
  - Columns of sack pages (up to 4), vanilla 18 px slots, amounts at 3/4 scale in at most 3 characters.
  - Two display modes: HYPIXEL shows all items with zeros greyed out; COMPACT shows owned items only, without duplicates.
  - Pages can be dragged by their title to reorder them (live preview). The order is saved in `data.pageOrder` and can be reset from the settings.
  - Right click on a title folds the page (saved in `data.folded`). Clicks never hide a page: pages are hidden from a "Hidden pages" group in the settings, one tick box per sack (`data.hidden`). Both are shared by all profiles. Hidden pages show up again while searching.
  - Withdrawing: left click sends `/gfs` with the whole stock (Hypixel only moves what fits), right click sends a stack, Shift + click opens `AmountPrompt`. Full slots get a red overlay.
  - The tooltip is a real item tooltip (`Screen.getTooltipFromItem`), so price mods add their lines. It uses the stack seen in the sack menu when available, with its "Stored:" line replaced by the live amount; otherwise it rebuilds Hypixel's lines. Hover uses vanilla `container/slot_highlight_*` sprites.
- Performance: `GlintThrottle` with `GuiItemAtlasMixin` and `GameRendererMixin` (same technique as Enhanced Storage 2.0, our own code). Animated (enchanted) items are only redrawn at the configured rate (30 FPS by default) while our screens are open. ImmediatelyFast's animated atlas stands aside meanwhile. Amount labels are cached and slots take 5 fills.
- `PageLayer` + `GuiRendererMixin` (Enhanced Storage's page cache technique, our own code): the pages' static content is extracted into an offscreen GuiRenderState, rendered by a second GuiRenderer into a window-sized texture, then blitted as one quad every frame. It is re-extracted only when `signatureOf` changes (layout, scroll, theme, search, counts, capacities...). Enchanted (foil) items and the hover highlight are drawn live on top (`Pass.LIVE`). Dragging, or setting `cachePages = false`, falls back to `Pass.ALL`.
- `AmountPrompt`: a modal drawn after everything (`nextStratum`) with +/- 1/10/100/1000, a number field and OK. It takes every key, char and click while open, and item tooltips are suppressed.
- `SackContainerScreen`: replaces the Sack of Sacks screen, swapped in by `mixin/GuiSetScreenMixin` (in 26.2, `setScreen` lives on `Gui`).
  - It keeps the real `ChestMenu` and only moves the real slots: the player inventory stays centered at the bottom, and the Sack of Sacks slots go into a panel on its left.
  - It reports the real bounds through `leftPos`/`topPos` and `AbstractContainerScreenAccessor` (imageWidth/imageHeight are final in 26.2), so Skyblocker's Quick Nav tabs and RRV place themselves around it. It leaves top and bottom margins when those mods are loaded.
- `SackOverlayScreen`: standalone, read-only view (`/sackoverlay`).
- `SackConfig` (YACL, like Skyblocker) + `SackModMenu`: settings via the ⚙ button, Mod Menu, or `/sackoverlay config`.
- Only the Sack of Sacks is replaced: the user wants each sack's own menu to stay vanilla. A version that replaced them with live slots inside the page was tried and removed. A left click on a page title clicks that sack's slot to open its vanilla menu, where clicks are instant and can be spammed; a right click folds the page.
- `/gfs` is slower than menu clicks: Hypixel handles commands with more latency. This cannot be fixed client-side; one left click (whole stock) avoids spamming. At most one `/gfs` goes out per `withdrawDelayMs` (500 ms by default). Clicks in between add up in `queuedWithdraw` and are flushed as one command; the slot shows a bottom-up white fill (`waitFill`) meanwhile. A queue still pending when the screen closes is dropped on purpose.
- `Theme` (Transparent default / Light / Dark). Frames are drawn in code in the vanilla menu style that Enhanced Storage uses: a black outline with cut corners, a raised edge and bevelled slots. No Enhanced Storage assets are copied (they are GPL).
- `ToolButtons`: one framed settings button with our own gear sprite (`textures/gui/sprites/gear.png`), placed right of the inventory and level with its top. The theme is chosen in the settings only. `/gfs` cooldown: 100 ms.
- `SearchInput` + `SackKeys` (features taken from Enhanced Storage's ideas):
  - search modes Filter / Dim;
  - optional remembered search and scroll position;
  - `/sackoverlay search <text>` sends /sacks with the search pre-filled;
  - unbound keys "open /sacks" and "search hovered item";
  - arrow keys move the highlight between pages.

## References studied
- SkyOcean + SkyblockAPI (meowdding): data source, `/gfs` format.
- Enhanced Storage (KdGaming0, GPL-3.0): approach only, no code copied. Screen swap + moving real slots + reporting bounds for other mods.
- NEU StorageOverlay (1.8.9): original inspiration.

## Workflow on the user's Windows PC
- After every build, copy `build/libs/sack-overlay-0.1.0.jar` into `D:\Jeux\ModrinthApp\profiles\SkyBlock Enhanced\mods` (replace the old one) — the user tests in that profile.
- Replacing the jar while the game runs breaks it until restart: the next resource reload reads the new file with the old zip index ("invalid LOC header", missing textures and lang). Tell the user to restart the game after a copy made while it was open.
- Hot reload dev client is configured (`gradlew runClient` with `../tools/jbr-25` + `../tools/hotswap-agent.jar`, DevAuth account "main"), but the user prefers testing in the real profile because the dev instance lacks their other mods.

## Releases
- Repo: https://github.com/nbardavid/sack-overlay (development happens on this PC).
- Bump `mod_version` in `gradle.properties`, commit and push, then `gh release create v<version> --generate-notes`: `.github/workflows/publish.yml` builds the jar, attaches it to the release and uploads it to Modrinth (secret `MODRINTH_TOKEN`, variable `MODRINTH_ID` = `YZojHzPT`).

- Modrinth project: https://modrinth.com/mod/sack-overlay-hypixel-skyblock (owner "lourio"), submitted for review on 2026-10-23. `modrinth.md` holds the page description.
- Modrinth checks every link when saving a page in review; a save can fail with "link could not be verified" even for working GitHub links. Retrying later worked.

## Build
- `./gradlew build` (on macOS run `chmod +x gradlew` first). The foojay plugin downloads JDK 25 if needed.
- Versions live in `gradle.properties` (MC 26.2, Fabric Loom 1.18, Kotlin 2.4, SkyblockAPI 4.2.42 bundled with `include`, YACL 3.9.5).
- Tested in game in the user's Modrinth "SkyBlock Enhanced" profile alongside Skyblocker, SkyOcean, SkyHanni, Enhanced Storage, RRV and Catharsis.

## Known gaps
- Pocket Sack-in-a-Sack upgrades are invisible in the sack id. The capacity is exact only after opening that sack's menu.
- `/gfs` with ids such as `INK_SACK:3` is unverified.
- Catharsis texture-pack overlays may still draw at vanilla positions.
- Opening a sack from our panel shows Hypixel's vanilla sack menu (not replaced).

## Next ideas
- Auto-update comes from Modrinth: the Modrinth App updates the mod once it is published there.
