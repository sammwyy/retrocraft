# Retrocraft

Play real retro games inside Minecraft.

Retrocraft adds handhelds, home consoles, arcade cabinets and CRT televisions to the game, all backed by actual [libretro](https://www.libretro.com/) emulator cores. Drop your ROMs on the server, grab a cartridge from the creative menu, plug it into a console and play — alone on a Game Boy in your pocket, or with friends gathered around a TV in your base.

- Fabric and Forge · Java 21 core · version-specific Minecraft integration
- Works on dedicated servers and in singleplayer

## What's in the box

| System | Hardware in game | Core |
| --- | --- | --- |
| NES | NES Handheld, NES Console | fceumm |
| SNES | SNES Console | snes9x |
| Game Boy / Game Boy Color | Game Boy, Game Boy Color | gambatte |
| Game Boy Advance | Game Boy Advance, Game Boy Advance SP | mgba |
| Nintendo DS | Nintendo DS | desmume |
| Nintendo 3DS | Nintendo 3DS | citra |
| PlayStation Portable | PSP | ppsspp |
| Mega Drive / Genesis | Mega Drive, Genesis | genesis_plus_gx |
| Nintendo 64 | Nintendo 64 | parallel_n64 |
| PlayStation | PlayStation Console | beetle_psx |
| PlayStation 2 | PlayStation 2 Console | pcsx2 |
| Dreamcast | Dreamcast Console | flycast |
| GameCube | GameCube Console | dolphin |
| Wii | Wii Console | dolphin |
| Arcade | Generic machine with swappable ROMs; one permanent-ROM cabinet per catalog game | mame2003 |

Plus three televisions to hook your consoles up to: a **Portable TV** (1 block), a **CRT Television** (2×1.5) and a big **Console Television** (3×2).

Every console, cartridge and TV has its own pixel-art model and texture. Top-loading consoles show the cartridge sitting in its slot; the NES front loader encloses it behind the door. Handheld screens and power indicators change when switched on. Disc-based systems have 3D game discs, including the smaller GameCube format. Disc colors identify the system: black GameCube, light blue Wii, blue/purple PS2, white PlayStation and orange Dreamcast.

## Handhelds

Hold a Game Boy, Game Boy Color, Game Boy Advance, Game Boy Advance SP, Nintendo DS, Nintendo 3DS, PSP or NES Handheld in your hand and right-click:

- Powered off → opens the console inventory with a single cartridge slot.
- Insert a cartridge, hit **Power on**, and the game HUD opens right away.
- Close the HUD whenever you want. The game keeps running on the server and picks up exactly where you left it the next time you open it.
- Powered handhelds render a live LCD preview directly on the item model in your hand, in inventories, on the ground and in item frames.
- Other players nearby hear your game's audio coming from you — even with the HUD closed, even if you drop the handheld on the ground.

Stash it in a chest and the game is suspended; take it back out and it resumes.

Nintendo DS and Nintendo 3DS have hinged models with two LCDs, each showing its own live preview or thumbnail. Their play screen has a **Screens** button to cycle through **Vertical**, **Horizontal**, **Large top** and **Large touch**; the choice is saved separately for each system. Click and drag inside the lower screen to use the stylus in any layout.

Game Boy Advance SP has its own hinged model, gameplay bezel and single-screen preview. It uses the same GBA cartridges, mGBA core and game saves as Game Boy Advance.

Nintendo DS uses DeSmuME and accepts `.nds` and `.ids`. Nintendo 3DS uses Citra with a private OpenGL 3.3 context. Desktop clients use an invisible window; dedicated Linux servers can use surfaceless Mesa/EGL. Supply decrypted `.3ds` / `.cci` / `.cxi` / `.app` files or `.3dsx` homebrew; CIA installers are not launchable cartridges. Large ROM files already placed in the server library run directly on the server when they exceed the configured client transfer budget.

PSP has a wide 480×272 LCD, a single analog stick, PlayStation face buttons, L/R shoulders and a UMD game item. Its handheld model displays live video or thumbnails, and its play screen uses a matching black-and-silver bezel. Use **WASD** for the analog stick, **arrow keys** for the D-pad, **Z/X/C/V** for Cross/Circle/Square/Triangle and **Q/E** for L/R; bindings remain customizable per console.

Place PSP games in a `psp/` or `playstation portable/` folder under the ROM library: `.iso`, `.chd`, `.cso`, `.pbp` and `.elf` overlap other systems and need this folder. PSP `.prx` homebrew is recognized anywhere. PPSSPP and its required fonts and compatibility files are downloaded automatically from the official libretro buildbot and cached together; no PSP BIOS is required. The core uses OpenGL at native resolution, including Mesa/EGL on dedicated Linux servers without a desktop. PSP runs on the server (including the integrated singleplayer server), since PPSSPP's initial snapshots exceed the client handoff limit. Its native memory-stick saves are stored under `config/retrocraft/saves/<scope>/<game-id>.files/PSP/` and follow the player/global/none save policy. Use the game's own save function; PSP suspend-state transfer and restoration are unavailable.

## Home consoles and TVs

Home consoles are blocks. Place one, place a TV within 12 blocks, insert a cartridge or disc (right-click the console with it) and power it on. The console grabs the nearest free TV and the game shows up on its screen, with sound coming from the set.

Anyone can walk up and watch. Docked consoles and arcades provide up to four controller slots; **Take control** claims the next free slot. The first slot holds the hybrid emulation lease, while each player drives their own pad. Handhelds stay single-player.

When you open an in-world play screen on a TV or arcade cabinet, the camera smoothly glides to frame the screen glass and hides your hand; use the top-right **`[-]` / `[+]`** buttons (or **`-` / `+`** keys) to step zoom between **50% and 400%**, alongside quick **Power off** and **Eject** buttons.

Arcade cabinets have a built-in CRT and need three blocks of vertical space. The generic arcade accepts swappable ROM boards. Each arcade ROM also appears as a dedicated cabinet in **RetroCraft - Arcades**: its internal ROM is permanent, and breaking the cabinet drops the same game variant without a separate board. Cabinet artwork follows the MAME set name or its parent set across nine themed cabinet families (**Pac-Man**, **Tetris**, **Space Invaders**, **Donkey Kong**, **Galaga**, **Street Fighter**, **Metal Slug**, **Mortal Kombat** and **Bubble Bobble**); unrecognized sets use the generic artwork with a personalized marquee. Hardware lives in **RetroCraft - Consoles**, and ROM items in **RetroCraft - Games**.

Break the console or eject the cartridge and the TV is free again. Unloading the chunk suspends the game; it comes back when someone's around to watch.

## Peripherals and expansions

Some consoles have extra ports next to the cartridge bay. Plug in an accessory from **RetroCraft - Consoles** through the console menu (or right-click a placed console with it) while the console is off; ports lock while it runs. Handhelds keep their accessories in the item's NBT (`Components`), placed consoles in their block entity, and breaking a console drops them. Hover an empty port to see what fits.

| Console | Port | Accessory | Effect in the emulator |
| --- | --- | --- | --- |
| NES Console | Controller Port 2 | NES Zapper | Light gun on port 2: aim with the mouse at the TV picture, left click fires |
| SNES Console | Controller Port 2 | Super Multitap | All four controller slots play |
| Game Boy / Game Boy Color | Link Port | Game Link Cable | Invite a nearby GB/GBC player within 16 blocks; the other player accepts or declines |
| Nintendo 64 | Memory Expansion Bay | Expansion Pak | 8 MB RAM; **without it the console has 4 MB**, so Donkey Kong 64 and Majora's Mask need it |
| Nintendo 64 | Controller Accessory | Controller Pak / Rumble Pak | Pak plugged into every controller |
| PlayStation | Memory Card Slot 2 | PlayStation Memory Card | Second memory card (slot 1 is always the regular save) |
| PlayStation | Controller Port 1 | PlayStation Multitap | Four players |
| Dreamcast | Expansion Slot 1 / 2 | Visual Memory Unit / Puru Puru Pack | Saves need the VMU; without it controllers have no memory unit |
| Wii | Wii Remote Extension | Wii Nunchuk / Classic Controller | Every Wii Remote uses that extension |

With a cable installed, open **Game Link** in the play screen and choose a nearby player who is using a GB/GBC. They receive an invitation with **Accept** and **Decline**; one cable is sufficient for the pair. Invitations expire after 30 seconds. After acceptance, both cores run on the Minecraft server and exchange actual serial bytes through a loopback relay (Gambatte network link, ports 56400–56420). Use the link/trade/multiplayer mode inside compatible games. **Unplug Link**, closing either play screen, moving beyond 16 blocks, changing dimensions, powering off or disconnecting removes the cable. Gambatte builds without network support cannot link. GBA/mGBA linking is unavailable.

Multitaps unlock player slots 3–4; the mod currently transports at most four controllers. With a Zapper installed, the first player's mouse aims and fires, replacing the second pad. Empty N64 accessory slots expose no Controller/Rumble Pak; the Expansion Pak controls the extra 4 MB of RAM. PS1 slot 2 cards and Dreamcast VMUs use durable native save files and keep emulation on the server. VMUs also show their LCD through Flycast's video overlay. Rumble/Puru Puru effects reach the player's controller HUD as visual vibration; physical gamepad vibration is not implemented.

## Controls

Press **F8 → Client → Controls**, choose a console, then click a control and press its new key or mouse button. **Escape** cancels recording, **Delete/Backspace** clears a binding, and **Defaults** restores that console's defaults. **Apply** saves changes locally in `config/retrocraft/client.json`. Each console has its own bindings. Shared keys are marked in red. F8 and Escape remain reserved for the menus.

| Default keys | Classic pads | Pads with analog sticks |
| --- | --- | --- |
| Arrow keys | D-pad | D-pad |
| Z / X | B / A | Bottom / right face button (Cross / Circle on PlayStation) |
| A / S | Y / X | — |
| C / V | — | Left / top face button (Square / Triangle on PlayStation) |
| Q / W | L / R | — |
| Q / E | — | L1 / R1 |
| 1 / 3 | — | L2 / R2, where present |
| WASD | — | Left stick |
| IJKL | — | Right stick, C stick or N64 C buttons |
| Left Shift / Right Ctrl | — | L3 / R3 on PlayStation |
| Enter / Right Shift | Start / Select | Start / Select, where present |
| `-` / `=` (or Numpad `-` / `+`) | Zoom out / in on world screens | Zoom out / in on world screens |
| F8 | Open settings | Open settings |
| Escape | Close the HUD | Close the HUD |

The in-game controller cards show console buttons and live stick positions. PlayStation symbols and stacked shoulder buttons match the pad layout. Key hints reflect your personal bindings and appear only on your own card. The server receives virtual controller buttons and axes; keyboard/mouse codes and binding profiles stay on your client.

## How it works (short version)

Emulation is **hybrid** by default (and can be switched to `server` or `client` in settings). While you're actively playing, the core runs locally on your client so local input avoids a network round trip; when you close the HUD or walk away, the save state is handed back to the server, which keeps running the game for anyone still watching or listening. Periodic checkpoints let the server recover the latest completed save after a crash or disconnect. The interval and transfer time determine how much progress can be recovered.

Durable per-game saves live under `config/retrocraft/saves/` (scoped per `player`, `global` or `none`): live `.state` snapshots keep powered consoles running across restarts, while battery `.sav` SRAM persists permanently even after powering a console off.

Each emulator core runs in its own isolated process, so one misbehaving game can't take the others down with it.

Cores are downloaded on demand from the [official libretro buildbot](https://buildbot.libretro.com/nightly/) for Linux, Windows and macOS (`x86_64` and `aarch64`). Only the cores your ROM library actually needs are fetched, and the hash of each core is pinned after the first download. Background core, ROM and BIOS transfers show live progress cards in the top-right corner of the screen. No ROMs, BIOS files or cores ship with the mod.

## Setup

1. Build the jar:

   ```sh
   ./gradlew -Pmc=1.21.1 -Ploader=fabric build
   ```

2. Install the matching JAR from `build/targets/<minecraft>/<loader>/libs/` on the server and every client. Fabric also requires **Fabric API**; Forge requires no additional dependency mods. Mod Menu is optional on Fabric. The emulator worker and its Java libraries are bundled in the JAR.

3. Put your ROMs in `config/retrocraft/roms/` on the server (or upload them in-game via **F8 → Server → ROM Library** by picking files or dragging and dropping them onto the window). Detection uses deterministic file extensions and, for shared formats, platform folders (`megadrive`/`genesis`, `ps1`/`psx`/`playstation`, `ps2`/`playstation2`, `dreamcast`/`dc`, `wii`, `gamecube`/`ngc`, `psp`/`playstation portable`). Console-unique formats (`.nes`, `.sfc`/`.smc`, `.gb`, `.gbc`, `.gba`, `.nds`/`.ids`, `.3ds`/`.3dsx`/`.cci`/`.cxi`/`.app`, `.md`/`.gen`/`.smd`, `.z64`/`.n64`/`.v64`, `.pbp`, `.cso`/`.zso`, `.cdi`/`.gdi`, `.wbfs`/`.rvz`/`.wad`/`.dol`, `.gcm`/`.gcz`/`.nkit`) match anywhere; shared disc images (`.iso`, `.chd`, `.cue`, `.img`) match inside their platform folder. ROM headers and archive contents are never inspected during discovery. Known MAME ZIP basenames stay intact and resolve to their full arcade titles. Games use content hashes for identity.

4. Start the world. Cartridges and discs for every ROM found appear in **RetroCraft - Games**, arcade variants in **RetroCraft - Arcades**, and consoles and TVs in **RetroCraft - Consoles**.

Added more ROMs later? Use the in-game **ROM Library** browser (which supports creating folders, renaming, moving, deleting, uploading and rescanning) or run `/retrocraft scan` (or `/retrocraft scan onlynews` to index only newly added files, op level 2) to rebuild and push the catalog to connected players without a restart.

### BIOS and Arcade notes

Systems that require firmware (**PlayStation**, **PlayStation 2**, **Dreamcast**, **GameCube / Wii** and **Arcade** sets that depend on a system BIOS) are managed under **F8 → Server → Cores** (`config/retrocraft/bios/<core>/`, or `config/retrocraft/bios/pcsx2/bios/` for PS2). If you power on a console whose BIOS is missing, an actionable **BIOS required** prompt opens and lets operators jump straight to that core's BIOS uploader. Installed BIOS files are synchronized automatically to hybrid clients when needed.

The default arcade core is MAME 2003, backed by a built-in MAME 0.78 / 2003-Plus machine and BIOS index:
- Known short ZIP names (e.g. `mslug.zip`, `pacman.zip`, `sf2.zip`) are recognized automatically and displayed with their full arcade title.
- Required arcade BIOS archives (such as `neogeo.zip`, `pgm.zip`, `skns.zip`, `cvs.zip`, `decocass.zip`, `playch10.zip`, `nss.zip`, `megatech.zip`, `megaplay.zip`, `stvbios.zip`, `cpzn1.zip`, `cpzn2.zip`, `tps.zip`, `taitofx1.zip`) can be uploaded in the BIOS manager, placed alongside ROMs, or extracted automatically from merged ROM/BIOS ZIPs; Neo-Geo BIOS archives are normalized so both MAME 0.78 and modern member names work.
- If a clone set is missing its parent ROM, Retrocraft reports the exact parent `.zip` and title required in the same folder.

### Configuration and commands

`config/retrocraft/server.json` is created on first launch. Press **F8**, use `/retrocraft gui` or `/rc gui`, or select the Mod Menu config button to access **Client** preferences without OP. Personal volume, video frame limit, world screens, handheld previews, controller HUD and camera focus are saved in `config/retrocraft/client.json`. The **Server** section (**Emulation**, **Streaming**, **Sessions**, **Cores** and **ROM Library**) and all server mutations require operator level 2.

Commands (`/retrocraft` or `/rc`):
- `/rc gui` — open the Retrocraft settings screen
- `/rc scan [onlynews]` — rescan the entire ROM library, or only newly added files (op level 2)
- `/rc reload` — reload `config/retrocraft/server.json` from disk (op level 2)
- `/rc config [<key> [<value>]]` — inspect or update any server config key or `cores.<system>` mapping such as `cores.retrocraft:ps2` (op level 2)

The defaults in `server.json` are sensible; the knobs you're most likely to touch:

```json
{
  "emulationMode": "hybrid",
  "saveMode": "player",
  "audioRadius": 16,
  "streamFps": 20,
  "maxActiveInstances": 8,
  "maxRomSizeMiB": 64
}
```

The `cores` key stores the mappings to the trusted, compatible core pool. Personal options can suppress ambient audio, video and thumbnails at the server, so disabling them also avoids the corresponding network traffic. Video textures obey the personal frame limit; server streams run at up to Minecraft's 20 ticks per second. Audio is delivered independently of the video frame rate.

## Development

Target versions and pinned loader dependencies live in [`gradle/targets.json`](gradle/targets.json). Each entry represents a supported API boundary; artifacts declare the exact Minecraft version they were built for. Fabric starts at 1.20.5, Forge at 1.20.6. Versions through 1.21.11 use Java 21; 26.x uses Java 25 for Minecraft while the core and worker remain Java 21 bytecode. Set `JAVA21_HOME` and `JAVA25_HOME` when both JDKs are installed.

```sh
./gradlew spotlessApply --offline
./gradlew -Pmc=1.21.1 -Ploader=fabric build
./gradlew -Pmc=1.21.8 -Ploader=forge --no-daemon build
./tools/gradle-modern -Pmc=1.21.11 -Ploader=fabric build
./tools/gradle-modern -Pmc=26.3 -Ploader=forge --no-daemon build
python3 tools/check_matrix.py
```

Use `./gradlew` through 1.21.10 and `./tools/gradle-modern` for 1.21.11 and 26.x. The latter downloads its separate Gradle wrapper into `build/`. Defaults are Minecraft 1.21.1 and Fabric. `-Ploader=none` builds only the core and worker. Forge runs use a fresh daemon because older ForgeGradle versions retain Minecraft state between projects.

`build` runs formatting checks, JUnit and the shared headless GameTests, including registration, cartridge ownership, controller packets, persistence and block drops. `runGameTest` runs only the Minecraft suite. `-Psmoke runClient` exercises model loading, live handheld textures and native keyboard input, then closes automatically. The matrix runner runs both suites plus the client for every profile, checks packaged metadata and Java bytecode, and records logs and results under `build/matrix/`. Linux headless client runs need Xvfb, Mesa and OpenAL. `--versions`, `--loaders`, `--offline` and `--no-client` narrow the run. Test mods never ship in release JARs.

The maintained source layout separates responsibilities:

| Directory | Responsibility |
| --- | --- |
| `core/` | Minecraft-independent emulation, ROMs, BIOS, configuration, saves and worker protocol; Java 21 |
| `worker/` | Isolated native emulator process and bundled runtime libraries; Java 21 |
| `common/` | Shared Minecraft gameplay, menus, rendering, packets, assets and regression scenarios |
| `fabric/`, `forge/` | Native loader entry points, registration and events |
| `compat/` | Small adapters selected by API boundary, plus mechanical Mojang class renames in `symbols.json` |
| `gradle/` | Target profiles and shared generation, packaging and test configuration |

Gameplay and assets have one source. Class renames are applied to generated build sources; behavioral API changes use explicit adapters. There is one release JAR per target and loader, with no Architectury or conversion mod required. Outputs and development worlds stay under `build/`; `clean` removes them, so keep ROM libraries and worlds intended for reuse outside development run directories.

Native NES tests use an original homebrew ROM and require `RETROCRAFT_TEST_CORE`. Game Link tests use two original homebrew GB ROMs and require `RETROCRAFT_TEST_GB_CORE` pointing to a network-enabled Gambatte core. N64 tests additionally accept `RETROCRAFT_TEST_N64_CORE` and `RETROCRAFT_TEST_N64_ROM`. Download tests require `RETROCRAFT_TEST_DOWNLOAD=true`; MAME tests require their corresponding `RETROCRAFT_TEST_MAME_*` variables. Optional native tests are skipped when their inputs are absent. Committed textures and models require no asset-generation tools to build. For asset development, `python3 tools/generate_models.py` rebuilds them, `--check` verifies reproducibility, and `python3 tools/check_models.py` checks references, geometry and screen alignment. `python3 tools/preview_models.py` writes review images to `build/previews/models/`. `python3 tools/generate_hud_frames.py` rebuilds the handheld background/overlay textures; `--check` verifies them. `python3 tools/generate_component_art.py` rebuilds the peripheral item textures and models. These optional tools use Pillow; previews also use NumPy.

Every console is a class implementing `ConsoleDefinition` (built-ins live in `server.console.builtin` and are listed in `Consoles.ALL`); `ConsoleRegistry.register(console)` calls its `register()`, which registers everything it brings: its system's media item, its accepted component items and its own item, block and block entity type. Extend `HandheldConsole`, `DockedConsole` or `ArcadeConsole` rather than implementing the interface directly. A console declares its `GameSystem` (e.g. `retrocraft:ps2`), which owns ROM discovery, the compatible cores (first is the default), the required `BiosRequirement` and catalog identity, and registers one media item named after its `MediaKind`: `nes_cartridge`, `nds_card`, `psp_umd`, `ps2_disc`, `arcade_board`. Consoles of the same system share one `GameSystem` constant. `KnownCores.defaultMappings()`, `RomFormats.ALL` and BIOS checks are derived from these declarations. Consoles are named after their system (`nes`, `gba`, `ps2`, `arcade`); only alternative hardware of the same system carries a suffix (`nes_handheld`, `gba_sp`, `genesis`, `arcade_tetris`, `arcade_cabinet`). A console also declares its outline bounds, whether it is listed in the creative tab or carries a permanent internal ROM, and its `ComponentSlot`s. A `ConsoleComponent` maps each core ID to a `HardwareProfile` (core option overrides, controller port devices, light-gun pointer source); a slot can also declare the hardware seen when it is empty. The worker only applies option values and devices the core itself advertises. `ConsoleType` distinguishes `HANDHELD`, `DOCKED` and `ARCADE`; the two placed types share multiplayer and world-video paths, while only `DOCKED` uses televisions. Adding a new system means declaring it with its discovery strategy and one or more consoles; client presentation is keyed by console ID in `ControllerProfile` (pad, HUD button layout and stick labels), `ConsoleTheme` (bezel colours) and, for handhelds, a `ConsoleHudRenderer`: two texture layers surround the video, with runtime layout and shared touch geometry. Client code lives in `client.ui` (screens, widgets and the HUD API), `client.render` (world and item renderers), `client.media` (video/audio streams) and `client.net` (hybrid emulation and settings channels). See [the handheld HUD API guide](docs/CONSOLE_HUD_API.md) for registration and customization. Docked consoles continue to use nearby televisions; arcade machines use their own model screens.

## License

MIT. See [LICENSE](LICENSE) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
