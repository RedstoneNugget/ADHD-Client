<p align="center">
  <img src="docs/logo.png" width="160" alt="ADHD Client Logo">
</p>

# ADHD Client

**English** · [Deutsch](README.de.md)

Client-side Fabric mod for **Minecraft 1.21.11**. Instead of installing many separate mods, ADHD Client bundles them as modules that you switch on or off in one menu. Every setting is made inside the client – only the key that opens the menu lives in the normal Minecraft controls.

> **Note:** This is a fan project and is not affiliated with any server. Many servers don't allow mods like this. Check the server rules first; use at your own risk.

## Installation
1. Install the [Fabric Loader](https://fabricmc.net/use/) for 1.21.11 and the [Fabric API](https://modrinth.com/mod/fabric-api)
2. Put `adhd-client-1.7.1.jar` into your `mods` folder

Nothing is needed on the server. Don't install it together with the standalone Base Finder or Head Finder mods – that functionality is already included.

## Usage
Press **Right Ctrl** to open the click GUI (changeable under *Options → Controls → Key Binds*). Modules are grouped into category windows (Render, World, Camera, Movement, Misc, Client) that you can drag around and collapse with a right click on the title. **Left click** a module to turn it on or off – a green bar means on. **Right click** opens its settings. All modules start switched off.

If a module has its own key, you set it at the very top of that module's menu: click the key button, then press the new key or a mouse button (Esc or Delete means no key, left click cancels). The small ↺ button next to it puts the key back to the module's default. Module keys only work while the module is switched on in the ClickGUI.

**Design** lets you pick the colors of all menus (presets like Purple, Ocean or Red, RGB sliders or hex), the window opacity and the language: automatic (follows the game language), German or English.

## Modules

| Module | What it does | Key |
|---|---|---|
| **Base Finder** | Scans loaded chunks and marks the best finds: suspicious chunks (artificial light, player blocks), unnatural spawners, unnatural bee nests, amethyst chunks and tall kelp. | H show markers, J scan (changeable) |
| **Block ESP** | Highlights any blocks you pick from a searchable list of every block in the game. Boxes take the block's color; optional tracers, fill and range. | – |
| **ESP** | Shows selected entities (players, mobs, items …) through walls: box, glow outline, tracers, name tags with distance and health. Color by distance, team or a fixed color. | – |
| **RTPQ Invsee** | When the chat names your RTPQ opponent, `/invsee` opens automatically with "fight / requeue" buttons and a countdown, and warns about elytra, mace and many crystals. You only enter plain text, no patterns. | – |
| **Free Cam** | Detach the camera and fly around freely. Your body stays in place and visible; clicks are blocked. Speed, sprint factor, smooth movement. | F6 (changeable) |
| **Free Look** | Look around your player in third person without turning them. Distance adjustable (also with the scroll wheel), camera stops at blocks or goes through them, hold or toggle, optionally also for the normal F5 view. | Left Alt (changeable) |
| **Full Bright** | Everything as bright as broad daylight, even in caves; optionally the sky looks like noon too (only for you, the server time stays the same). Adjustable strength, optionally ignores the Darkness effect. | none needed |
| **Butterfly** | Weapons in your hand look like a butterfly knife in CS2 style (3D model "Butterfly Fade" by markminerov123, see Credits): it flips open when you pull it out, does a trick inspect on a key, flips when you hit and fidgets while you stand still. You choose which items become it,. Only you see it. Make your own animations in Blockbench: [guide (German)](docs/Butterfly-Blockbench-Anleitung.md). | R inspect (changeable) |
| **Blink** | Holds back your movement packets: the server sees you standing still, and when you turn it off everything is sent at once. Optional copy of you at the start, auto send after a few seconds, cancel key that puts you back at the start. Anticheats often detect it. | none (set in the menu) |
| **Inspect All** | Shows everything Minecraft knows about the block or entity you look at, or the item in your hand or under the mouse: block state, components, NBT (highlighted, collapsible), readable summaries for bee nests, spawners, signs, chests and more, markers through walls, search, copy as SNBT or /give, JSON export, a button to clear all markers and a color palette for them. Lock a target to keep an overlay while you play. | I panel, K lock (changeable) |
| **Packet Tools** | A bar next to every container menu: close without packet, close only on the server (desync), turn click packets off or delay them and send them all at once, save a menu and reopen it, disconnect while sending, build your own slot/button click packets, copy the title as JSON, sync ID and revision, and a chat field to write messages or commands while the menu stays open. Plus "Hold all": no packets go to the server until you release them (or discard them), with its own key, keep-alive passthrough and a live packet log – plus a large packet log window showing every sent and received packet with filter, search, pause, copy and a count per type. Many servers detect this. | V reopens a saved menu (changeable) |
| **Lucky Casino** | A full casino with play money: slot machine, roulette, coin flip, blackjack, crash, scratch cards, Plinko, mines, daily wheel, loot boxes, missions and achievements. Chips are virtual and stored locally; your LuckyCasino progress carries over. Also `/casino open`. | O opens the casino (changeable) |

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

## Credits
The Butterfly module uses a modified version of "Butterfly Fade" (https://sketchfab.com/3d-models/butterfly-fade-2dd640a011434f338abb4dca508e9099) by markminerov123 (https://sketchfab.com/markminerov123), licensed under CC BY 4.0 (http://creativecommons.org/licenses/by/4.0/). Changes: converted for Minecraft, handles split into separate moving parts, textures resized.
