# Butterfly-Animationen selbst machen (Blockbench)

Mit Blockbench kannst du alle Animationen des Butterfly-Messers selbst bauen: **Ziehen, Inspect, Schlag und Spielen im Stand**.
Der ADHD Client liest sie direkt aus deiner Datei – Speichern reicht, kein Neustart nötig.

## 1. Blockbench installieren

1. Auf <https://www.blockbench.net> gehen und **Download** klicken (kostenlos).
2. Installieren und starten.

## 2. Vorlage erstellen

1. Minecraft mit dem ADHD Client starten und eine Welt betreten.
2. ADHD-Menü öffnen (**Rechte Strg**) und die Einstellungen von **Butterfly** öffnen.
3. Unten bei **Eigene Animationen (Blockbench)** auf **Vorlage erstellen** klicken.
4. Es öffnet sich der Ordner `config/adhd_client/butterfly/` mit der Datei **`butterfly.bbmodel`**.

In der Datei stecken das Messer und alle aktuellen Animationen als Startpunkt.
Du musst also nicht bei null anfangen, sondern kannst meine Animationen verändern.

> **Achtung:** Wenn die Datei schon da ist, heißt der Knopf **Vorlage neu erstellen**. Er **überschreibt** deine Datei.
> Mach vorher eine Kopie, wenn du deine Arbeit behalten willst.

## 3. Datei in Blockbench öffnen

1. `butterfly.bbmodel` auf das Blockbench-Fenster ziehen (oder **Datei → Öffnen**).
2. Oben rechts auf **Animate** klicken. Jetzt bist du im Animations-Modus.

## 4. Was du siehst

**Links oben – die Animationen:**

| Name | Wann sie läuft |
|---|---|
| `draw` | wenn du das Messer in die Hand nimmst |
| `inspect` | wenn du die Inspect-Taste drückst (R) |
| `attack_1` | erster Schlag (danach abwechselnd) |
| `attack_2` | zweiter Schlag |
| `idle` | Spielen, wenn du still stehst |

Eine Animation anklicken, um sie zu bearbeiten.

**Links unten – die Teile (Knochen):**

| Knochen | Was er bewegt | Drehpunkt |
|---|---|---|
| `hand` | das **ganze Messer** in deiner Hand | Griffmitte |
| `blade` | die **Klinge** (mit Griff A dran) | Bolzen von Griff B |
| `handle_a` | den **schwingenden Griff** | sein eigener Bolzen |
| `handle_b` | den **Griff in der Hand** – lieber nicht drehen | Bolzen von Griff B |

**Unten – die Zeitleiste:** jede Raute ist ein **Schlüsselbild** (Keyframe).
Zwischen zwei Schlüsselbildern rechnet Blockbench die Bewegung von selbst aus.

## 5. Animieren

1. Eine Animation links anklicken, z. B. `inspect`.
2. Mit der **Leertaste** abspielen, nochmal Leertaste zum Anhalten.
3. In der Zeitleiste auf die Stelle klicken, wo sich etwas ändern soll.
4. Links einen Knochen auswählen (z. B. `blade`).
5. Drehen mit dem **Drehen-Werkzeug (R)** oder rechts bei **Rotation** Zahlen eintippen.
   Verschieben mit dem **Bewegen-Werkzeug (V)** oder bei **Position**.
   Blockbench legt dabei automatisch ein Schlüsselbild an.
6. Schlüsselbilder kannst du anklicken und mit **Entf** löschen oder mit der Maus verschieben (schneller/langsamer).
7. **Speichern mit Strg+S.**

### Wichtige Werte für Flips

- `blade` **Rotation Z**: `0` = offen, `180` (oder `-180`) = zugeklappt.
- `handle_a` **Rotation Z**: dreht den schwingenden Griff. Ein ganzer Flip ist eine volle Runde (`-360`).
  Damit er nicht durch den anderen Griff geht, lass ihn zur **freien Seite** schwingen (negative Zahlen).
- `hand`: kleine Werte reichen. Ein paar Grad Drehung und 1–3 Einheiten Verschiebung wirken schon viel.

### Länge einer Animation ändern

Rechtsklick auf die Animation links → **Properties** (Eigenschaften) → **Length** (Länge in Sekunden).
Wenn die Animation zu Ende ist, geht das Messer zurück in die normale Haltung.

### Weich oder hart

Ein Schlüsselbild anklicken → rechts **Interpolation**:
**Smooth** (weich, Standard in der Vorlage), **Linear** (gleichmäßig) oder **Step** (springt sofort).

## 6. Im Spiel testen

1. In Blockbench **Strg+S** drücken.
2. In Minecraft die Animation auslösen (z. B. **R** für Inspect). Die neue Version ist sofort da.
3. Im Butterfly-Menü zeigt der Knopf **Ordner öffnen** beim Drüberfahren, welche eigenen Animationen geladen sind.

## 7. Zurück zu den eingebauten Animationen

- **Eine** Animation zurück: sie in Blockbench löschen (Rechtsklick → Delete) und speichern.
  Dann nimmt der Client für sie wieder die eingebaute.
- **Alle** zurück: `butterfly.bbmodel` löschen oder umbenennen.

## Wenn etwas komisch aussieht

- Dreht sich etwas im Spiel **andersherum** als in Blockbench: Sag mir Bescheid, welcher Knochen und welche Achse.
- Wenn die Datei kaputt ist, nutzt der Client automatisch die eingebauten Animationen.
  Der Tooltip von **Ordner öffnen** zeigt dann „Blockbench-Datei fehlerhaft“.
- Formeln (Molang) in den Feldern werden nicht ausgewertet – bitte nur Zahlen eintragen.
