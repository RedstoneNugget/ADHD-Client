# ADHD Client

**English** · [Deutsch](README.de.md)

Client-side Fabric mod for **Minecraft 1.21.11**. Instead of installing many separate mods, ADHD Client bundles them as modules that you switch on or off in one menu. Every setting is made inside the client – only the key that opens the menu lives in the normal Minecraft controls.

> **Note:** This is a fan project and is not affiliated with any server. Many servers don't allow mods like this. Check the server rules first; use at your own risk.

## Installation
1. Install the [Fabric Loader](https://fabricmc.net/use/) for 1.21.11 and the [Fabric API](https://modrinth.com/mod/fabric-api)
2. Put `adhd-client-1.1.0.jar` into your `mods` folder

Nothing is needed on the server. Don't install it together with the standalone Base Finder or Head Finder mods – that functionality is already included.

## Usage
Press **Right Ctrl** to open the click GUI (changeable under *Options → Controls → Key Binds*). Modules are grouped into category windows (Render, World, Camera, Movement, Misc, Client) that you can drag around and collapse with a right click on the title. **Left click** a module to turn it on or off – a green bar means on. **Right click** opens its settings. All modules start switched off.

If a module has its own key, you set it at the very top of that module's menu: click the key button, then press the new key or a mouse button (Esc or left click cancels, Delete removes the key). Module keys only work while the module is switched on in the ClickGUI.

**Design** lets you pick the colors of all menus (presets, RGB sliders or hex), the window opacity and the language: automatic (follows the game language), German or English.

## Modules

| Module | What it does | Key |
|---|---|---|
| **Base Finder** | Scans loaded chunks and marks the best finds: suspicious chunks (artificial light, player blocks), unnatural spawners, unnatural bee nests, amethyst chunks and tall kelp. | H show markers, J scan (changeable) |
| **Block ESP** | Highlights any blocks you pick from a searchable list of every block in the game. Boxes take the block's color; optional tracers, fill and range. | – |
| **ESP** | Shows selected entities (players, mobs, items …) through walls: box, glow outline, tracers, name tags with distance and health. Color by distance, team or a fixed color. | – |
| **RTPQ Invsee** | When the chat names your RTPQ opponent, `/invsee` opens automatically with "fight / requeue" buttons and a countdown, and warns about elytra, mace and many crystals. You only enter plain text, no patterns. | – |
| **Free Cam** | Detach the camera and fly around freely. Your body stays in place and visible; clicks are blocked. Speed, sprint factor, smooth movement. | F6 (changeable) |
| **Free Look** | Look around your player in third person without turning them. Distance adjustable (also with the scroll wheel), camera stops at blocks or goes through them, hold or toggle, optionally also for the normal F5 view. | Left Alt (changeable) |
| **Full Bright** | Everything bright like night vision, adjustable strength, optionally ignores the Darkness effect. | none needed |
| **Blink** | Holds back your movement packets: the server sees you standing still, and when you turn it off everything is sent at once. Optional copy of you at the start, auto send after a few seconds, cancel key that puts you back at the start. Anticheats often detect it. | none (set in the menu) |
| **Inspect All** | Shows everything Minecraft knows about the block or entity you look at, or the item in your hand or under the mouse: block state, components, NBT (highlighted, collapsible), readable summaries for bee nests, spawners, signs, chests and more, markers through walls, search, copy as SNBT or /give, JSON export. Lock a target to keep an overlay while you play. | I panel, K lock (changeable) |

All modules only use data your client already has. Nothing extra is sent to the server.

Settings are stored in `config/adhd_client/`.

## Building
Requires Java 21.
```
./gradlew build
```
The jar ends up in `build/libs/`. `./gradlew runClient` starts a local test client (muted by default).

## License
MIT, see [LICENSE](LICENSE). Author: redstone_nugget.
