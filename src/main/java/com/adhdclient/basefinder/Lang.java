package com.adhdclient.basefinder;

import net.minecraft.client.MinecraftClient;

import java.util.HashMap;
import java.util.Map;

/**
 * Zweisprachige Texte: Deutsch, wenn die Spielsprache Deutsch ist (de_de, de_at, de_ch …),
 * sonst Englisch. Wechselt man die Sprache im Spiel, passen sich Menü und Markierungen an.
 */
public final class Lang {

    /** Englische Anzeigenamen für Namen, die (deutsch) in der Config stehen. */
    private static final Map<String, String> NAMES = new HashMap<>();

    static {
        // Lichtquellen
        n("Fackeln", "Torches");
        n("Seelenfackeln", "Soul torches");
        n("Kupferfackeln", "Copper torches");
        n("Redstone-Fackeln", "Redstone torches");
        n("Laternen", "Lanterns");
        n("Seelenlaternen", "Soul lanterns");
        n("Kupferlaternen", "Copper lanterns");
        n("Lagerfeuer", "Campfires");
        n("Seelenlagerfeuer", "Soul campfires");
        n("Kürbislaternen", "Jack o'lanterns");
        n("Redstone-Lampen", "Redstone lamps");
        n("Endstäbe", "End rods");
        n("Kerzen", "Candles");
        n("Kupferleuchten", "Copper bulbs");
        n("Leuchtstein", "Glowstone");
        n("Seelaternen", "Sea lanterns");
        n("Pilzlicht", "Shroomlights");
        n("Froschlichter", "Froglights");
        n("Leuchtfeuer", "Beacons");
        n("Aquisitoren", "Conduits");
        n("Lichtblöcke", "Light blocks");
        // andere künstliche Blöcke
        n("Werkbänke", "Crafting tables");
        n("Öfen", "Furnaces");
        n("Schmelzöfen", "Blast furnaces");
        n("Räucheröfen", "Smokers");
        n("Truhen", "Chests");
        n("Fässer", "Barrels");
        n("Betten", "Beds");
        n("Ambosse", "Anvils");
        n("Zaubertische", "Enchanting tables");
        n("Schilder", "Signs");
        n("Rahmen", "Item frames");
        n("Schienen", "Rails");
        n("Glas", "Glass");
        n("Beton", "Concrete");
        n("Wolle", "Wool");
        n("Glasierte Keramik", "Glazed terracotta");
        n("Treppen", "Stairs");
        n("Stufen", "Slabs");
        // Strukturen
        n("Mineshaft", "Mineshaft");
        n("Festung", "Stronghold");
        n("Dorf", "Village");
        n("Plünderer-Außenposten", "Pillager outpost");
        n("Waldanwesen", "Woodland mansion");
        n("Wüstentempel", "Desert temple");
        n("Dschungeltempel", "Jungle temple");
        n("Trial Chambers", "Trial chambers");
        n("Ancient City", "Ancient city");
        n("Bastion", "Bastion");
        n("Nether-Festung", "Nether fortress");
        n("End-Stadt", "End city");
        n("Ozeanmonument", "Ocean monument");
        n("Verlies", "Dungeon");
        n("Iglu", "Igloo");
        n("Hexenhütte", "Witch hut");
        n("Trail Ruins", "Trail ruins");
    }

    private Lang() {
    }

    private static void n(String de, String en) {
        NAMES.put(de, en);
    }

    public static boolean german() {
        return com.adhdclient.gui.L.german();
    }

    /** Text in der passenden Sprache. */
    public static String t(String de, String en) {
        return german() ? de : en;
    }

    /** Anzeigename für einen in der Config gespeicherten (deutschen) Namen. */
    public static String name(String germanName) {
        if (germanName == null || german()) return germanName;
        return NAMES.getOrDefault(germanName, germanName);
    }
}
