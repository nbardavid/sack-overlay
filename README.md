# Sack Overlay

Client-side Fabric mod for Hypixel SkyBlock (Minecraft 26.2): a clean overview of your sacks.

## What it does
- Replaces the `/sacks` screen with one page per sack family, the real Sack of Sacks slots, and your inventory where it usually is.
- Click an item to withdraw it (`/gfs`, exactly as if you typed it). Amounts update right away.
- Settings: the ⚙ button at the bottom right, Mod Menu's Config button, or `/sackoverlay config`.

## Data
- Amounts: `SacksAPI.sackItems` from [SkyblockAPI](https://github.com/SkyblockAPI/SkyblockAPI), the same source as SkyOcean, kept up to date by the `[Sacks]` chat messages and the sack menus.
- Which item goes in which sack: `sacks.json` from SkyOcean's [meowdding-repo](https://github.com/meowdding/meowdding-repo) (MIT).
- Sack of Sacks layout and capacities: read from the menus you open.
- No extra network requests. The only thing ever sent is `/gfs`, on click.

## Build
1. `gradlew.bat build` (Gradle downloads a JDK 25 if needed).
2. Copy `build/libs/sack-overlay-0.1.0.jar` into `mods/`, with Fabric API, Fabric Language Kotlin and YetAnotherConfigLib.

## Commands and keys
- `/sackoverlay`: open the overview outside the menu.
- `/sackoverlay search <text>`: open `/sacks` with a search already typed.
- `/sackoverlay config`: open the settings.
- Start typing to search (can be turned off). Arrow keys move between pages.
- In Controls, under Sack Overlay: "Open sacks" and "Search hovered item" (unbound by default).
