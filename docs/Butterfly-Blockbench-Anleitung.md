# Eigene Modelle und Animationen (Blockbench)

Du kannst das Butterfly durch **eigene Modelle** ersetzen und ihnen **eigene Animationen** geben:
**Ziehen, Inspect, Schlag und Spielen im Stand**. Der ADHD Client liest alles direkt aus deinen Dateien –
Speichern reicht, kein Neustart nötig.

## 1. Blockbench installieren

1. Auf <https://www.blockbench.net> gehen und **Download** klicken (kostenlos).
2. Installieren und starten.

## 2. Die Knöpfe im Menü

ADHD-Menü (**Rechte Strg**) → Einstellungen von **Butterfly** → unten **Eigene Modelle (Blockbench)**:

| Knopf | Was er macht |
|---|---|
| **Modell: …** | wechselt zwischen dem eingebauten Butterfly und deinen Modellen |
| **Modelle-Ordner** | öffnet `config/adhd_client/butterfly/models/` |
| **Vorlage erstellen** | legt das Butterfly mit allen Animationen als `Butterfly-Vorlage.bbmodel` an und wählt es aus |
| **glTF umwandeln** | macht aus `.gltf`, `.glb` oder einem Sketchfab-`.zip` im Ordner eine `.bbmodel` |

Jede `.bbmodel` im Modelle-Ordner ist ein eigenes Modell.

## 3. Modell aus dem Internet (glTF / Sketchfab)

1. Das Modell als **glTF** herunterladen (bei Sketchfab: *Download → glTF*, du bekommst ein `.zip`).
2. Das `.zip` (oder `.gltf` / `.glb`) **in den Modelle-Ordner legen**. Entpacken ist nicht nötig.
3. Im Butterfly-Menü auf **glTF umwandeln** klicken.
4. Daneben liegt jetzt eine `.bbmodel` mit gleichem Namen, und sie ist schon ausgewählt.
   Das Modell wird automatisch aufgerichtet (längste Seite nach oben) und auf Messergröße gebracht.
5. Die `.bbmodel` in Blockbench öffnen, um es anzupassen und zu animieren.

Jedes Teil des Modells wird ein eigener Knochen, den du animieren kannst.
**Lizenz beachten:** nur Modelle nehmen, die du benutzen darfst (z. B. CC BY oder CC0) und den Ersteller nennen, wenn verlangt.

## 4. In Blockbench öffnen

1. Die `.bbmodel` auf das Blockbench-Fenster ziehen (oder **Datei → Öffnen**).
2. Oben rechts auf **Animate** klicken.

**Links oben – die Animationen** (diese Namen benutzt der Client):

| Name | Wann sie läuft |
|---|---|
| `draw` | wenn du das Messer in die Hand nimmst |
| `inspect` | Inspect-Taste (R) |
| `attack_1` | erster Schlag (danach abwechselnd) |
| `attack_2` | zweiter Schlag (oder nur eine `attack` für beide) |
| `idle` | Spielen, wenn du still stehst |

Bei einem umgewandelten Modell ist die Liste leer: Mit dem **+** neue Animationen anlegen und genau so benennen.

**Links unten – die Knochen** (in der Butterfly-Vorlage):

| Knochen | Was er bewegt |
|---|---|
| `hand` | das ganze Messer in der Hand |
| `blade` | die Klinge (Griff A hängt dran) |
| `handle_a` | den schwingenden Griff |
| `handle_b` | den Griff in der Hand – lieber nicht drehen |

## 5. Animieren

1. Eine Animation links anklicken.
2. **Leertaste** spielt ab und hält an.
3. In der Zeitleiste unten auf die Stelle klicken, wo sich etwas ändern soll.
4. Links einen Knochen wählen.
5. Drehen mit **R** (oder rechts bei *Rotation* Zahlen eintippen), verschieben mit **V** (*Position*).
   Blockbench legt dabei automatisch ein Schlüsselbild (Raute) an.
6. Rauten anklicken und mit **Entf** löschen oder mit der Maus verschieben (schneller/langsamer).
7. **Strg+S** speichern – im Spiel ist die neue Version sofort da.

**Tipps für Flips (Vorlage):** `blade` Rotation Z: `0` = offen, `-180` = zu. `handle_a` Rotation Z `-360` = ein ganzer Schwung.
Klinge und Griff A in **dieselbe Richtung** drehen, dann geht nichts durcheinander.

**Länge:** Rechtsklick auf die Animation → *Properties* → *Length* (Sekunden).
Danach geht das Messer zurück in die normale Haltung.

## 6. Eigenes Modell bauen

- Die Hand hält das Modell bei **X 0, Y −6,5, Z 0**, die Spitze zeigt nach **oben (+Y)**.
  Das Butterfly ist etwa 27 Einheiten lang (Y −14 bis +13).
- Gruppen (Ordner links) sind Knochen. Ihr **Drehpunkt** (Pivot) ist der Punkt, um den sie sich drehen.
- Fehlt eine Animation, steht das Modell in dem Moment einfach still.

## 7. Zurück zum eingebauten Butterfly

Im Menü auf **Modell: …** klicken, bis **Butterfly Fade** dasteht.

> **Achtung:** **Vorlage erstellen** überschreibt `Butterfly-Vorlage.bbmodel`. Willst du deine Arbeit behalten,
> in Blockbench mit *Datei → Speichern unter* einen anderen Namen im Modelle-Ordner wählen.

## Wenn etwas nicht klappt

- Fährst du mit der Maus über **Modell: …**, siehst du, ob es geladen ist und welche Animationen es hat.
- „Modell fehlerhaft“: Datei in Blockbench öffnen und neu speichern, oder zurück auf **Butterfly Fade** schalten.
- Formeln (Molang) in den Feldern werden nicht ausgewertet – bitte nur Zahlen.
