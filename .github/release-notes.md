**ADHD Client 1.8.7** for Minecraft 1.21.11 (Fabric, client only) · [Deutsch weiter unten](#deutsch)

One client, many modules – each switched on or off in one menu (**Right Ctrl**). All settings, colors and module keys are set inside the client. Menus in English or German.

### New in 1.8.7
- **Aim Lock** now also works in Free Cam and Free Look (it turns your body, not the camera)

### New in 1.8.6
- **Huge number ranges** – every number setting goes much further (e.g. ESP range up to 8192, Free Cam speed up to 1000, Air Jump up to 1000 jumps). Big sliders are fine at the start and coarse at the end; the mouse wheel changes one step, Shift + wheel ten
- **Stack Drop** (new module, Misc) – pick items and blocks, one button or key throws out every matching stack from hotbar and inventory
- **Auto Totem** switches the hotbar like a real mouse wheel scroll; speed in half-tick steps (default half a tick per slot)
- **Lunge Macro** and **Auto Reconnect** can also be set to "instant"

### New in 1.8.3
- **Chat Emojis** – :skull:, :heart:, :star:, :shrug: and more become symbols in chat (only for you, optionally also when sending)
- **Client Intro** – short ADHD Client animation on the title screen at start; a click skips it
- **Enemy Totems** – "⚑ Totem" after players holding a totem, plus how often their totem popped
- **Air Jump** is now unlimited by default

### New in 1.8.2
- **Auto Totem** and **Anti Crystal Death**: own on/off keys and a speed setting down to "instant" (everything in one tick)
- **Reset button** at the bottom right of every module window (click twice) – puts all settings of that module back to default

### New in 1.8.1
- **Air Jump** (Movement) – jump again in mid-air, number and strength adjustable
- **Auto Totem** – missing offhand totem: the hotbar selection visibly scrolls to a totem, swaps it into the offhand and scrolls back
- **Anti Crystal Death** – end crystal nearby: opens the inventory and puts totems in main hand and offhand
- The butterfly model is now a readable JSON file (no packed data in the jar), fewer false alarms in virus scans

### New in 1.8.0
- **Stronghold Finder** (World) – throw two eyes of ender from different spots, top left shows the stronghold and Nether coordinates, distance and which way to turn
- **Calculator** (key N) – math with stacks and shulkers, items into shulkers/stacks, overworld ↔ nether
- **Lunge Macro** (key V) – takes the lunge spear, jabs and switches back in the same tick; hold to repeat
- **Auto Reconnect** – reconnects after a disconnect with countdown and cancel button
- **Macros** – keys that send commands or chat messages
- **Free Cam** – option to keep mining the block your body looks at

### New in 1.7.6
- **Aim Lock** (new module, Misc) – press G (changeable) to lock onto the entity in your crosshair. While your crosshair is anywhere on its hitbox nothing moves; when it slips off, it is pulled smoothly back to the nearest height of the hitbox, with a little random deviation. Releases at a set number of blocks. Speed, deviation and players-only settings. Usually not allowed on servers

### New in 1.7.4
- **Ghost Items** (new module, Misc) – put any item into your inventory only on your side: pick from every item, choose the count, remove them with one button. Option **Infinite**: the stack never runs out, e.g. when placing. The server knows nothing about them
- **Fake enchantments** – extra window in Ghost Items: any enchantment at level 1–255 on the item in your hand, only you see it

### New in 1.7.2
- **Butterfly: own models** – put .bbmodel files into the models folder and pick them in the menu; they play their own draw, inspect, slash and idle animations and reload when you save in Blockbench
- **glTF import** – one click turns a .gltf, .glb or Sketchfab .zip in the models folder into a .bbmodel (aligned and sized like the knife)

### New in 1.7.1
- **Butterfly: better animations** – real balisong mechanics: you hold one handle, the blade turns around its pin and the other handle swings with it. Nothing passes through anything anymore (checked step by step), smooth motion with wrist turns, new CS2-like hold
- **Butterfly: own animations** – make your own draw, inspect, slash and idle animations in Blockbench; the menu creates a template and the client reloads it when you save ([guide, German](https://github.com/RedstoneNugget/ADHD-Client/blob/main/docs/Butterfly-Blockbench-Anleitung.md))
- The color option is gone – the model brings its own colors

### New in 1.7.0
- **Butterfly** – weapons you pick look like a CS2-style butterfly knife in your hand: it flips open when you pull it out, does a trick inspect (R), slashes left and right when you hit and fidgets while you stand still. Pick which items become it and the animation speed. Only you see it
- 3D model: "Butterfly Fade" by markminerov123 (https://sketchfab.com/3d-models/butterfly-fade-2dd640a011434f338abb4dca508e9099), CC BY 4.0, converted for Minecraft

### New in 1.6.0
- **Packet Tools: large packet log** – a window with every sent (→) and received (←) packet: list or count per type, filter by direction, search by name or content, pause, clear and copy. Opens with "Log" in the menu bar, from the Packet Tools settings or with its own key
- New mod icon and logo

### New in 1.5.0
- **Full Bright like daytime** – full sky light and maximum brightness everywhere, even at night and in caves; new option "Sky like daytime" (on by default) shows a noon sky. Only you see it, the server time stays the same

### New in 1.4.0
- **Lucky Casino** – a full casino with play money (slots, roulette, blackjack, crash, Plinko, mines and more) as a module; your LuckyCasino progress carries over (O opens it)
- **Red design** – new preset "Red" in the Design menu
- **Inspect All** – "Clear marks" button in the panel and a color palette for markers
- The Design menu fits on small screens again

### New in 1.3.0
- **Reset keys** – every module key has a small reset button next to it that puts it back to the default
- **Esc means no key** – pressing Esc while picking a key now removes the key instead of cancelling (left click still cancels)
- **Packet Tools: Hold all** – no packets go to the server until you release them (then everything is sent at once) or discard them; own keys, also outside menus, keep-alive passthrough so you are not kicked, info above the hotbar
- **Packet Tools: packet log** – shows top right which packets are being sent
- Fixed: holding packets in singleplayer no longer touches the built-in server

### New in 1.2.0
- **Packet Tools** – a bar next to every container menu: close without packet, server desync, turn click packets off or delay them, save and reopen menus, disconnect while sending, build your own click packets, copy the title, and a chat field to write while the menu stays open (V reopens a saved menu)

### New in 1.1.0
- **Inspect All** – see everything Minecraft knows about a block, item or entity: block state, components, NBT, readable summaries, markers through walls, search, copy as SNBT or `/give`, JSON export (I panel, K lock)
- **Blink** – holds back your movement and sends it all at once, with a copy of you at the start, auto send and a cancel key (new **Movement** category)
- **Mouse buttons as keys** – every module key can also be a mouse button, e.g. a side button
- **Keys only work while the module is on** – switch a module on in the menu, then its key does something
- **Base Finder** – own keys for showing markers (H) and scanning (J) at the top of its menu, plus a scan button
- **ESP** – easier colors: three mode buttons and a palette to click
- Short key taps are no longer missed, and the built jar contains no metadata

### All modules
- **Base Finder** – marks suspicious chunks, unnatural spawners and bee nests, amethyst and tall kelp
- **Block ESP** – highlight any blocks you pick from a list of every block
- **ESP** – show selected entities through walls (box, glow, tracers, name tags)
- **Full Bright** – everything bright like daytime, no key needed
- **Butterfly** – CS2-style butterfly knife with animations
- **Free Cam** – fly the camera freely, your body stays put (F6)
- **Free Look** – look around in third person, adjustable distance, through blocks or not (Left Alt)
- **Blink** – hold back your movement, then send it at once
- **RTPQ Invsee** – auto `/invsee` on your RTPQ opponent with elytra/mace/crystal warnings
- **Inspect All** – all data of blocks, items and entities
- **Packet Tools** – tools for container menus and their packets
- **Aim Lock** – stick to a target's hitbox
- **Ghost Items** – client-side items and fake enchantments
- **Stack Drop** – throw out all stacks of chosen items at once
- **Lucky Casino** – casino with play money

Not affiliated with any server. Check the server rules before using it – Blink, ESP, Packet Tools and similar modules are often not allowed.

### Installation
1. Install the [Fabric Loader](https://fabricmc.net/use/) for 1.21.11 and the [Fabric API](https://modrinth.com/mod/fabric-api)
2. Put `adhd-client-1.8.7.jar` into your `mods` folder (remove older ADHD Client jars and the standalone Base Finder / InspectAll / LuckyCasino jars)

---

### Deutsch
Ein Client, viele Module – jedes einzeln an/aus in einem Menü (**Rechte Strg**). Alle Einstellungen, Farben und Modul-Tasten stellst du im Client ein. Menüs auf Deutsch oder Englisch.

**Neu in 1.8.7**
- **Aim Lock** geht jetzt auch in Free Cam und Free Look (dreht deinen Körper, nicht die Kamera)

**Neu in 1.8.6**
- **Riesige Zahlenbereiche** – jede Zahl-Einstellung geht viel weiter (z. B. ESP-Reichweite bis 8192, Free-Cam-Tempo bis 1000, Air Jump bis 1000 Sprünge). Große Regler sind vorne fein und hinten grob; das Mausrad ändert um einen Schritt, Shift + Mausrad um zehn
- **Stack Drop** (neues Modul, Sonstiges) – Items und Blöcke auswählen, ein Knopf oder eine Taste wirft alle passenden Stacks aus Hotbar und Inventar
- **Auto Totem** wechselt die Hotbar wie echtes Mausrad-Scrollen; Tempo in halben Ticks (Standard: ein halber Tick pro Feld)
- **Lunge Macro** und **Auto Reconnect** lassen sich auch auf „sofort“ stellen

**Neu in 1.8.3**
- **Chat-Emojis** – :skull:, :heart:, :star:, :shrug: und mehr werden im Chat zu Symbolen (nur bei dir, optional auch beim Senden)
- **Client-Intro** – kurze ADHD-Client-Animation im Hauptmenü beim Start; ein Klick überspringt sie
- **Gegner-Totems** – „⚑ Totem“ hinter Spielern mit Totem in der Hand, dazu wie oft ihr Totem geplatzt ist
- **Air Jump** ist jetzt standardmäßig unbegrenzt

**Neu in 1.8.2**
- **Auto Totem** und **Anti Crystal Death**: eigene An/Aus-Tasten und Tempo bis „sofort“ (alles in einem Tick)
- **Zurücksetzen-Knopf** unten rechts in jedem Modul-Fenster (zweimal klicken) – alle Einstellungen des Moduls auf Standard

**Neu in 1.8.1**
- **Air Jump** (Bewegung) – in der Luft nochmal springen, Anzahl und Kraft einstellbar
- **Auto Totem** – fehlt das Offhand-Totem, läuft die Hotbar-Auswahl sichtbar zum Totem, tauscht es in die Offhand und läuft zurück
- **Anti Crystal Death** – End-Kristall in der Nähe: öffnet das Inventar und legt Totems in Main- und Offhand
- Das Butterfly-Modell ist jetzt eine lesbare JSON-Datei (keine gepackten Daten mehr in der Jar), weniger Fehlalarme bei Virus-Scans

**Neu in 1.8.0**
- **Stronghold Finder** (Welt) – zwei Ender-Augen von verschiedenen Orten werfen, oben links stehen Stronghold- und Nether-Koordinaten, Entfernung und Drehrichtung
- **Rechner** (Taste N) – Rechnungen mit Stacks und Shulkern, Items in Shulker/Stacks, Overworld ↔ Nether
- **Lunge Macro** (Taste V) – nimmt den Lunge-Speer, sticht zu und wechselt im selben Tick zurück; Halten wiederholt
- **Auto Reconnect** – verbindet nach einer Trennung neu, mit Countdown und Abbrechen
- **Makros** – Tasten, die Befehle oder Chat-Nachrichten schicken
- **Free Cam** – Option, weiter am Körper abzubauen

**Neu in 1.7.6**
- **Aim Lock** (neues Modul, Sonstiges) – G (änderbar) lockt die Entity im Fadenkreuz. Solange das Fadenkreuz irgendwo auf der Hitbox ist, bewegt sich nichts; rutscht es runter, wird es weich zur nächsten Höhe der Hitbox zurückgezogen, mit etwas zufälliger Abweichung. Löst sich ab der eingestellten Blockzahl. Tempo, Abweichung und „Nur Spieler“ einstellbar. Auf Servern meist verboten

**Neu in 1.7.4**
- **Ghost Items** (neues Modul, Sonstiges) – beliebige Items nur bei dir ins Inventar legen: aus allen Items wählen, Anzahl einstellen, mit einem Knopf wieder entfernen. Option **Unendlich**: der Stapel wird nie weniger, z. B. beim Platzieren. Der Server weiß nichts davon
- **Fake-Verzauberungen** – Extra-Fenster in Ghost Items: jede Verzauberung mit Stufe 1–255 auf das Item in der Hand, nur du siehst sie

**Neu in 1.7.2**
- **Butterfly: eigene Modelle** – .bbmodel-Dateien in den Modelle-Ordner legen und im Menü auswählen; sie spielen ihre eigenen Animationen (Ziehen, Inspect, Schlag, Spielen) und laden beim Speichern in Blockbench neu
- **glTF-Import** – ein Klick macht aus .gltf, .glb oder einem Sketchfab-.zip im Modelle-Ordner eine .bbmodel (ausgerichtet und auf Messergröße)

**Neu in 1.7.1**
- **Butterfly: bessere Animationen** – echte Butterfly-Mechanik: du hältst einen Griff, die Klinge dreht sich um dessen Bolzen und der andere Griff schwingt mit. Nichts geht mehr durcheinander (Schritt für Schritt nachgerechnet), flüssige Bewegungen mit Handgelenk, neue Haltung wie in CS2
- **Butterfly: eigene Animationen** – Ziehen, Inspect, Schlag und Spielen selbst in Blockbench machen; das Menü erstellt eine Vorlage, der Client lädt sie beim Speichern neu ([Anleitung](https://github.com/RedstoneNugget/ADHD-Client/blob/main/docs/Butterfly-Blockbench-Anleitung.md))
- Die Farbeinstellung ist weg – das Modell bringt seine eigenen Farben mit

**Neu in 1.7.0**
- **Butterfly** – ausgewählte Waffen sehen in der Hand aus wie ein Butterfly-Messer im CS2-Stil: klappt beim Ziehen auf, Inspect-Trick (R), Schnitte abwechselnd links und rechts beim Schlagen und Spielen im Stand. Du wählst die Items und das Tempo. Nur du siehst das
- 3D-Modell: „Butterfly Fade“ von markminerov123 (https://sketchfab.com/3d-models/butterfly-fade-2dd640a011434f338abb4dca508e9099), CC BY 4.0, für Minecraft umgewandelt

**Neu in 1.6.0**
- **Packet Tools: großes Paket-Log** – ein Fenster mit allen gesendeten (→) und empfangenen (←) Paketen: Liste oder Zählung pro Typ, Filter nach Richtung, Suche nach Name oder Inhalt, Pause, Leeren und Kopieren. Öffnet sich mit „Log“ in der Menü-Leiste, in den Packet-Tools-Einstellungen oder per eigener Taste
- Neues Mod-Icon und Logo

**Neu in 1.5.0**
- **Full Bright wie am Tag** – volles Himmelslicht und maximale Helligkeit überall, auch nachts und in Höhlen; neue Option „Himmel wie am Tag“ (standardmäßig an) zeigt einen Mittagshimmel. Nur du siehst das, die Server-Zeit bleibt gleich

**Neu in 1.4.0**
- **Lucky Casino** – ein komplettes Casino mit Spielgeld (Slots, Roulette, Blackjack, Crash, Plinko, Minen u. v. m.) als Modul; dein LuckyCasino-Spielstand wird übernommen (O öffnet es)
- **Rotes Design** – neue Vorlage „Rot“ im Design-Menü
- **Inspect All** – Knopf „Marker weg“ im Panel und eine Farbpalette für Markierungen
- Das Design-Menü passt wieder auf kleine Bildschirme

**Neu in 1.3.0**
- **Tasten zurücksetzen** – neben jeder Modul-Taste ein kleiner Knopf, der sie auf den Standard zurücksetzt
- **Esc = keine Taste** – Esc beim Tastenwählen entfernt jetzt die Taste statt abzubrechen (Linksklick bricht weiter ab)
- **Packet Tools: Alles anhalten** – keine Pakete gehen an den Server, bis du sie loslässt (dann alles auf einmal) oder verwirfst; eigene Tasten, auch außerhalb von Menüs, Keep-Alive-Durchlass gegen Kicks, Info über der Hotbar
- **Packet Tools: Paket-Log** – zeigt oben rechts, welche Pakete gesendet werden
- Behoben: Anhalten im Einzelspieler greift nicht mehr in den eingebauten Server ein

**Neu in 1.2.0**
- **Packet Tools** – eine Leiste neben jedem Container-Menü: ohne Paket schließen, Server-Desync, Klick-Pakete aus- oder verzögern, Menüs speichern und wieder öffnen, trennen und dabei senden, eigene Klick-Pakete bauen, Titel kopieren und ein Chat-Feld, um bei offenem Menü zu schreiben (V öffnet ein gespeichertes Menü)

**Neu in 1.1.0**
- **Inspect All** – alles, was Minecraft über Block, Item oder Entity weiß: Blockstate, Komponenten, NBT, lesbare Zusammenfassungen, Markierungen durch Wände, Suche, Kopieren als SNBT oder `/give`, JSON-Export (I Panel, K sperren)
- **Blink** – hält deine Bewegung zurück und sendet sie auf einmal, mit Kopie am Start, automatischem Senden und Abbrechen-Taste (neue Kategorie **Bewegung**)
- **Maustasten als Tasten** – jede Modul-Taste kann auch eine Maustaste sein, z. B. eine Seitentaste
- **Tasten nur bei eingeschaltetem Modul** – Modul im Menü anschalten, dann reagiert seine Taste
- **Base Finder** – eigene Tasten für Markierungen (H) und Scannen (J) ganz oben im Menü, dazu ein Scan-Knopf
- **ESP** – Farben einfacher: drei Modus-Knöpfe und eine Palette zum Anklicken
- Kurzes Antippen von Tasten geht nicht mehr verloren, die gebaute Jar enthält keine Metadaten

**Alle Module:** Base Finder, Block ESP, ESP, Full Bright, Butterfly, Free Cam, Free Look, Blink, RTPQ Invsee, Inspect All, Packet Tools, Stronghold Finder, Calculator, Lunge Macro, Air Jump, Auto Totem, Anti Crystal Death, Auto Reconnect, Macros, Aim Lock, Ghost Items, Stack Drop, Lucky Casino

Kein offizielles Projekt eines Servers. Vor der Nutzung die Server-Regeln lesen – Blink, ESP, Packet Tools und ähnliche Module sind oft verboten.

1. [Fabric Loader](https://fabricmc.net/use/) für 1.21.11 und die [Fabric API](https://modrinth.com/mod/fabric-api) installieren
2. `adhd-client-1.8.7.jar` in den `mods`-Ordner legen (ältere ADHD-Client-Jars und die einzelnen Base-Finder-/InspectAll-/LuckyCasino-Jars entfernen)
