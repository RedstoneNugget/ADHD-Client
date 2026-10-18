package com.adhdclient.basefinder.gui;

import com.adhdclient.basefinder.Lang;

import java.util.HashMap;
import java.util.Map;

/**
 * Englische Übersetzungen der Menütexte. Im Code stehen die deutschen Texte; ist die Spielsprache
 * nicht Deutsch, liefert {@link #tr(String)} die englische Fassung (fehlt sie, bleibt es Deutsch).
 */
final class MenuText {

    private static final Map<String, String> EN = new HashMap<>();

    private MenuText() {
    }

    static String tr(String de) {
        if (de == null || Lang.german()) return de;
        return EN.getOrDefault(de, de);
    }

    private static void e(String de, String en) {
        EN.put(de, en);
    }

    static {
        // Seitenleiste, Fußzeile
        e("Funde", "Finds");
        e("Amethyst", "Amethyst");
        e("Bienen", "Bees");
        e("Spawner", "Spawners");
        e("Kelp", "Kelp");
        e("Anomalien", "Anomalies");
        e("Allgemein", "General");
        e("Gewichte", "Weights");
        e("aus", "off");
        e("Jetzt neu scannen", "Rescan now");
        e("Fertig", "Done");

        // Fundliste
        e("Suche", "Search");
        e("Suchen …", "Search …");
        e("Sortierung umschalten", "Toggle sorting");
        e("↕ Entfernung", "↕ Distance");
        e("↕ Rang", "↕ Rank");
        e("◎ Fokus aufheben", "◎ Clear focus");
        e("Fokus aufheben", "Clear focus");
        e("Nur diesen Fund in der Welt zeigen", "Show only this find in the world");
        e("Koordinaten kopieren (z. B. für /tp)", "Copy coordinates (e.g. for /tp)");
        e("Fund ausblenden (bis zum Verlassen der Welt)", "Hide this find (until you leave the world)");
        e("Noch keine Funde in den geladenen Chunks. Die Liste füllt sich, sobald gescannt wurde.",
                "No finds in the loaded chunks yet. The list fills up as chunks get scanned.");
        e("Keine Funde passen zur Suche.", "No finds match the search.");

        // Allgemein
        e("Scannen", "Scanning");
        e("Konstantes Scannen", "Constant scanning");
        e("Scannt dauerhaft im Hintergrund, sobald du in einer Welt bist (Taste K pausiert).",
                "Scans in the background whenever you're in a world (K pauses).");
        e("Markierungen sichtbar", "Markers visible");
        e("Alle Markierungen ein-/ausblenden (Taste H). Das Scannen läuft weiter.",
                "Show/hide all markers (H). Scanning keeps running.");
        e("Scan-Radius", "Scan radius");
        e("Höchstens bis zur eingestellten Render-Distanz.", "At most your render distance.");
        e("Scan-Budget", "Scan budget");
        e("Maximale Scan-Arbeit pro Tick. Kleiner = flüssiger, aber langsamer.",
                "Maximum scan work per tick. Lower = smoother but slower.");
        e("Hintergrund-Durchlauf", "Background pass");
        e("Prüft alle gecachten Chunks regelmäßig neu, falls Updates verpasst wurden.",
                "Re-checks all cached chunks regularly in case updates were missed.");
        e("Verzögerung nach Änderung", "Delay after change");
        e("Geänderte Chunks werden gebündelt nach dieser Zeit neu gescannt.",
                "Changed chunks are rescanned in batches after this time.");
        e("Suchmodi", "Search modes");
        e("Chunks mit den meisten ausgewachsenen Amethystclustern.", "Chunks with the most fully grown amethyst clusters.");
        e("Unnatürliche Bienennester", "Unnatural bee nests");
        e("Nester/Stöcke, die dort nicht natürlich entstehen können.", "Nests/hives that can't generate naturally there.");
        e("Unnatürliche Spawner", "Unnatural spawners");
        e("Spawner, die dort nicht natürlich entstehen können (falscher Mob, falsche Umgebung …).",
                "Spawners that can't generate naturally there (wrong mob, wrong surroundings …).");
        e("Übergroßer Kelp", "Oversized kelp");
        e("Die höchsten zusammenhängenden Kelp-Säulen.", "The tallest connected kelp columns.");
        e("Chunks, die nicht wie frisch generierte Welt aussehen (Licht, Bauten).",
                "Chunks that don't look like freshly generated terrain (light, builds).");
        e("Anzeige & Meldungen", "Display and messages");
        e("Benachrichtigungs-Ton", "Notification sound");
        e("Kurzer Ton bei neuen Funden.", "Short sound for new finds.");
        e("Label-Größe", "Label size");
        e("Meldung", "Message");
        e("Actionbar-Meldung bei neuen Funden in diesem Modus.", "Action bar message for new finds in this mode.");
        e("Farbe", "Color");
        e("Farbe als #RRGGBB, z. B. #B061FF", "Color as #RRGGBB, e.g. #B061FF");
        e("Bienennester", "Bee nests");
        e("Config-Datei: config/", "Config file: config/");
        e(" – alle Werte, Gewichte und Listen lassen sich dort auch ohne Menü ändern.",
                " – all values, weights and lists can also be edited there without the menu.");

        // Amethyst
        e("Modus aktiv", "Mode enabled");
        e("Was wird gezählt", "What is counted");
        e("Ausgewachsene Cluster", "Fully grown clusters");
        e("Große Knospen", "Large buds");
        e("Mittlere Knospen", "Medium buds");
        e("Kleine Knospen", "Small buds");
        e("Knospender Amethyst", "Budding amethyst");
        e("Anzeige", "Display");
        e("Mindestanzahl", "Minimum count");
        e("Ein Chunk wird erst ab so vielen gezählten Blöcken markiert.", "A chunk is only marked from this many counted blocks.");
        e("Anzahl Markierungen (N)", "Number of markers (N)");
        e("Nur die N besten Chunks werden angezeigt.", "Only the N best chunks are shown.");
        e("Markiert wird der ganze Chunk (Rahmen vom tiefsten bis zum höchsten Fund). Der beste Chunk ist dicker/heller und heißt \"Bester Chunk\".",
                "The whole chunk is marked (frame from the lowest to the highest find). The best chunk is thicker/brighter and labeled \"Best chunk\".");

        // Bienen
        e("Bienennester (bee_nest)", "Bee nests (bee_nest)");
        e("Bienenstöcke (beehive)", "Beehives (beehive)");
        e("Kriterien (eins reicht für \"unnatürlich\")", "Criteria (one is enough for \"unnatural\")");
        e("Unterirdisch", "Underground");
        e("Unter der Oberfläche ohne Himmelszugang (Höhle, in Stein eingebaut).", "Below the surface without sky access (cave, built into stone).");
        e("Falsches Biom", "Wrong biome");
        e("Biom, in dem keine Nester generieren (Liste unten).", "Biome where nests don't generate (list below).");
        e("Dimension", "Dimension");
        e("Nether oder End.", "Nether or End.");
        e("Keine Baumstruktur", "No tree nearby");
        e("Keine Stämme/Blätter in der Nähe.", "No logs/leaves nearby.");
        e("Beehives immer unnatürlich", "Beehives always unnatural");
        e("Bienenstöcke generieren nie natürlich – mit diesem Schalter immer melden.",
                "Beehives never generate naturally – with this switch they are always reported.");
        e("Leere Nester ausblenden", "Hide empty nests");
        e("Nester mit bekannt 0 Bienen ignorieren. Auf Servern ist die Anzahl meist unbekannt (?), solche Nester bleiben sichtbar.",
                "Ignore nests known to have 0 bees. On servers the count is usually unknown (?), those nests stay visible.");
        e("Anzeige & Feineinstellung", "Display and fine tuning");
        e("Baum-Suchradius", "Tree search radius");
        e("Mind. Baumblöcke", "Min. tree blocks");
        e("So viele Stamm-/Laubblöcke (davon min. 1 Stamm) müssen im Radius sein.",
                "This many log/leaf blocks (at least 1 log) must be within the radius.");
        e("Natürliche Biome", "Natural biomes");
        e("Komma-getrennte Biom-IDs, in denen Nester natürlich generieren.", "Comma-separated biome IDs where nests generate naturally.");
        e("Sortierung: Bienenanzahl, dann Honigstufe, dann Entfernung. Die Bienenanzahl kennt der Client nur im Einzelspieler; auf Servern steht dort \"?\".",
                "Sorted by bee count, then honey level, then distance. The client only knows the bee count in singleplayer; on servers it shows \"?\".");

        // Spawner
        e("Trial-Spawner", "Trial spawners");
        e("Trial-Spawner gehören in Trial Chambers (Tuffziegel, Kupfer).", "Trial spawners belong in trial chambers (tuff bricks, copper).");
        e("Alle anzeigen", "Show all");
        e("Auch natürliche Spawner markieren (Label „natürlich“ + Struktur). Unnatürliche stehen immer zuerst.",
                "Also mark natural spawners (label \"natural\" + structure). Unnatural ones always come first.");
        e("Falscher Mob", "Wrong mob");
        e("Mob, der nie aus natürlichen Spawnern kommt (z. B. Schwein, Creeper, Eisengolem).",
                "A mob that never comes from natural spawners (e.g. pig, creeper, iron golem).");
        e("Falsche Dimension", "Wrong dimension");
        e("Z. B. Lohe in der Oberwelt oder Zombie im Nether.", "E.g. a blaze in the Overworld or a zombie in the Nether.");
        e("Keine passende Struktur", "No matching structure");
        e("Um den Spawner fehlen die typischen Blöcke (Verlies: Bruchstein, Mineshaft: Spinnweben/Holz …). Findet auch umgebaute Mob-Farmen.",
                "The typical blocks around the spawner are missing (dungeon: cobblestone, mineshaft: cobwebs/wood …). Also finds rebuilt mob farms.");
        e("Unter freiem Himmel", "Under open sky");
        e("Natürliche Spawner liegen nie offen unter dem Himmel.", "Natural spawners are never exposed to the open sky.");
        e("Ohne Mob", "No mob");
        e("Leerer Spawner (z. B. im Kreativmodus gesetzt).", "Empty spawner (e.g. placed in creative mode).");
        e("Natürliche Mobs", "Natural mobs");
        e("Mob-ID=Struktur, durch Komma getrennt. Die Strukturen (Verlies, Mineshaft, Festung, Nether-Festung, Bastion, Trial Chambers) und ihre typischen Blöcke stehen in der Config-Datei.",
                "Mob ID=structure, comma-separated. Structure names stay German in the config (Verlies = dungeon, Festung = stronghold, Nether-Festung = Nether fortress, Bastion, Trial Chambers); their typical blocks are in the config file.");
        e("Natürlich: Zombie/Skelett/Spinne im Verlies (Bruchstein), Höhlenspinne im Mineshaft, Silberfischchen in der Festung, Lohe in der Nether-Festung, Magmawürfel in der Bastion. Spawner lassen sich in Vanilla nicht abbauen und umsetzen – ein Fund deutet auf Kreativmodus, Befehle, ein Server-Plugin oder eine umgebaute Farm hin.",
                "Natural: zombie/skeleton/spider in dungeons (cobblestone), cave spider in mineshafts, silverfish in strongholds, blaze in Nether fortresses, magma cube in bastions. Spawners can't be picked up and moved in vanilla – a find points to creative mode, commands, a server plugin or a rebuilt farm.");

        // Kelp
        e("Mindesthöhe", "Minimum height");
        e("Zusammenhängende Kelp-Säulen ab dieser Höhe werden berücksichtigt.", "Connected kelp columns from this height on are considered.");
        e("Markiert wird jeweils die Spitze der Säule, mit der Höhe im Label.", "The top of each column is marked, with its height in the label.");

        // Anomalien
        e("Unnatürliche Chunks (Anomalien)", "Unnatural chunks (anomalies)");
        e("Kategorien", "Categories");
        e("Lichtquellen", "Light sources");
        e("Fackeln, Laternen, Leuchtstein … gewichtet gezählt.", "Torches, lanterns, glowstone … counted with weights.");
        e("Licht-Check unter Oberfläche", "Light check below surface");
        e("Blocklicht in Höhlen/Räumen, das keine natürliche Quelle (Lava, Leuchtflechten …) erklärt.",
                "Block light in caves/rooms that no natural source (lava, glow lichen …) explains.");
        e("Andere künstliche Blöcke", "Other artificial blocks");
        e("Werkbank, Ofen, Truhen, Betten, Glas, Beton … (niedrige Gewichte).", "Crafting table, furnace, chests, beds, glass, concrete … (low weights).");
        e("Strukturfilter", "Structure filter");
        e("Erkennt natürliche Strukturen (Dorf, Mineshaft, Festung …) und blendet sie aus, außer der Score liegt weit darüber.",
                "Detects natural structures (village, mineshaft, stronghold …) and hides them unless the score is far above.");
        e("\"Wahrscheinlich Basis\" markieren", "Mark \"probably a base\"");
        e("Mehrere Lichtquellen-Sorten UND mehrere andere künstliche Blöcke zusammen.", "Several kinds of light sources AND several other artificial blocks together.");
        e("Schwellen", "Thresholds");
        e("Mindestscore", "Minimum score");
        e("Struktur-Toleranz", "Structure tolerance");
        e("Strukturchunks werden nur gemeldet, wenn ihr Score so viel höher als typisch ist.",
                "Structure chunks are only reported if their score is this much higher than typical.");
        e("Licht-Check Punkte/10 Blöcke", "Light check points/10 blocks");
        e("Licht-Check Maximum", "Light check maximum");
        e("Unbekannte Lichtquelle", "Unknown light source");
        e("Leuchtende Blöcke, die weder natürlich noch in einer Liste sind (z. B. Lichtblock).",
                "Glowing blocks that are neither natural nor in a list (e.g. light block).");
        e("Gewichte der einzelnen Blocksorten, eigene Blöcke und die Listen natürlicher Lichtquellen stehen im Tab \"Gewichte\".",
                "Weights per block type, custom blocks and the lists of natural light sources are in the \"Weights\" tab.");

        // Gewichte
        e("Lichtquellen (Gewicht pro Block)", "Light sources (weight per block)");
        e("Schienen zählen nicht in erkannten Mineshafts, Treppen/Stufen nicht in erkannten Strukturen.",
                "Rails don't count in detected mineshafts, stairs/slabs don't count in detected structures.");
        e("Eigene Blöcke", "Custom blocks");
        e("Beliebige Block-ID mit Gewicht, z. B. minecraft:tnt. Auch Platzhalter (minecraft:*_shulker_box), Tags (#minecraft:beds) und Objekte (entity:minecraft:armor_stand).",
                "Any block ID with a weight, e.g. minecraft:tnt. Wildcards (minecraft:*_shulker_box), tags (#minecraft:beds) and entities (entity:minecraft:armor_stand) work too.");
        e("Block", "Block");
        e("Gewicht", "Weight");
        e("Gewicht pro Block", "Weight per block");
        e("Entfernen", "Remove");
        e("+ Eigenen Block hinzufügen", "+ Add custom block");
        e("Natürliche Lichtquellen (pro Dimension)", "Natural light sources (per dimension)");
        e("Oberwelt", "Overworld");
        e("Nether", "Nether");
        e("End", "End");
        e("Diese Blöcke gelten in der Oberwelt als natürliche Lichtquellen.", "These blocks count as natural light sources in the Overworld.");
        e("Diese Blöcke gelten im Nether als natürliche Lichtquellen.", "These blocks count as natural light sources in the Nether.");
        e("Diese Blöcke gelten im End als natürliche Lichtquellen.", "These blocks count as natural light sources in the End.");
        e("Typischer Score natürlicher Strukturen", "Typical score of natural structures");
        e("Typischer Score dieser Struktur", "Typical score of this structure");
        e("Gewichte auf Standard zurücksetzen", "Reset weights to defaults");
        e("\n(nicht in erkannten Strukturen)", "\n(not in detected structures)");
        e("\n(nicht in: ", "\n(not in: ");
    }
}
