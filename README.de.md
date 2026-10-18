# ADHD Client

[English](README.md) · **Deutsch**

Clientseitige Fabric-Mod für **Minecraft 1.21.11**. Statt mehrerer einzelner Mods bündelt der ADHD Client sie als Module, die sich einzeln über ein Menü an- und ausschalten lassen – ähnlich wie bei einem Client wie NoRisk, der viele Funktionen an einer Stelle vereint.

> **Hinweis:** Dies ist ein Fan-Projekt und kein offizielles Projekt eines bestimmten Servers. Viele Server erlauben solche Mods nicht. Vorher die Serverregeln prüfen; Nutzung auf eigenes Risiko.

## Installation
1. [Fabric Loader](https://fabricmc.net/use/) für 1.21.11 und die [Fabric API](https://modrinth.com/mod/fabric-api) installieren
2. `adhd-client-1.0.0.jar` in den `mods`-Ordner legen

Auf dem Server ist nichts nötig. Die Mod liest nur Chunks, die der Client ohnehin geladen hat, und schickt nichts an den Server.

## Tasten
| Taste | Aktion |
|---|---|
| **Rechte Strg** | Öffnet das ADHD-Client-Menü |

Änderbar unter *Optionen → Steuerung → Tastenbelegung*.

### Menü
Listet jedes Modul mit einem An/Aus-Schalter und einem Knopf zu dessen eigenen Einstellungen (bzw. Liste). Schaltet man ein Modul aus, stoppt dessen Scan und die Markierungen verschwinden; seine Einstellungen bleiben erhalten.

## Module

### Base Finder
Scannt die Chunks, die der Client geladen hat, im Hintergrund und markiert die besten Funde: auffällige Chunks, die nicht frisch generiert aussehen (künstliches Licht, Spielerblöcke), unnatürliche Spawner, unnatürliche Bienennester, Amethyst-Chunks und hohen Kelp. Einstellungen, Schwellenwerte und Farben im eigenen Einstellungs-Bildschirm des Moduls, gespeichert unter `config/adhd_client/base_finder.json`.

### Block Finder
Durchsucht geladene Chunks nach einem beliebigen, selbst eingestellten Block – exakte ID (`minecraft:diamond_ore`), Platzhalter (`*diamond_ore` trifft auch die Deepslate-Variante) oder Block-Tag (`#minecraft:logs`). Treffer werden durch Wände markiert, in einer Gefunden/Offen-Liste mit Entfernung geführt und mit einem Pfeil zum nächsten Fund versehen; per Klick lassen sie sich abhaken. Einstellungen unter `config/adhd_client/head_finder/config.json`.

Beide Module arbeiten nur mit Daten, die der Client ohnehin hat (geladene Chunks, Blockzustände) – kein zusätzlicher Netzwerkverkehr, nichts wird an den Server geschickt.

## Bauen
Benötigt Java 21.
```
./gradlew build
```
Die Jar landet in `build/libs/`. `./gradlew runClient` startet einen lokalen Testclient (standardmäßig stumm).

## Lizenz
MIT, siehe [LICENSE](LICENSE). Autor: redstone_nugget.
