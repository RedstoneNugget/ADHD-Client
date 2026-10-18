# ADHD Client

**English** · [Deutsch](README.de.md)

Client-side Fabric mod for **Minecraft 1.21.11**. Instead of installing several separate mods, ADHD Client bundles them as modules that are switched on or off individually in one menu – similar to how a client like NoRisk packs many features into one place.

> **Note:** This is a fan project and is not affiliated with any particular server. Many servers don't allow mods like this. Check the server rules first; use at your own risk.

## Installation
1. Install the [Fabric Loader](https://fabricmc.net/use/) for 1.21.11 and the [Fabric API](https://modrinth.com/mod/fabric-api)
2. Put `adhd-client-1.0.0.jar` into your `mods` folder

Nothing is needed on the server. The mod only reads chunks your client has loaded anyway and sends nothing to the server.

## Controls
| Key | Action |
|---|---|
| **Right Ctrl** | Open the ADHD Client menu |

Can be changed under *Options → Controls → Key Binds*.

### Menu
Lists every module with an on/off switch and a button to its own settings (or list screen). Turning a module off stops its scanning and hides its markers; its settings stay as they are.

## Modules

### Base Finder
Scans the chunks your client has loaded in the background and marks the best finds: anomaly chunks that don't look freshly generated (artificial light, player blocks), unnatural spawners, unnatural bee nests, amethyst chunks and tall kelp. Settings, thresholds and colors are in the module's own settings screen, stored at `config/adhd_client/base_finder.json`.

### Block Finder
Searches loaded chunks for any block you configure – an exact ID (`minecraft:diamond_ore`), a wildcard (`*diamond_ore` matches the deepslate variant too) or a block tag (`#minecraft:logs`). Matches are marked through walls, kept in a found/not-found list with distance and an arrow to the nearest one, and can be checked off with a click. Settings are stored at `config/adhd_client/head_finder/config.json`.

Both modules work purely from data the client already has (loaded chunks, block states) – no extra network traffic, nothing sent to the server.

## Building
Requires Java 21.
```
./gradlew build
```
The jar ends up in `build/libs/`. `./gradlew runClient` starts a local test client (muted by default).

## License
MIT, see [LICENSE](LICENSE). Author: redstone_nugget.
