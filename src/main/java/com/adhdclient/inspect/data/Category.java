package com.adhdclient.inspect.data;

/** Ein-/ausblendbare Kategorien im Info-Panel (siehe Einstellungen). */
public enum Category {
    GENERAL("Allgemein", "General"),
    SUMMARY("Zusammenfassung", "Summary"),
    BLOCKSTATE("Blockstate", "Block state"),
    BLOCK_PROPERTIES("Block-Eigenschaften", "Block properties"),
    ENVIRONMENT("Umgebung", "Surroundings"),
    ITEM_PROPERTIES("Item-Eigenschaften", "Item properties"),
    ENTITY_STATS("Entity-Werte", "Entity stats"),
    EQUIPMENT("Ausrüstung", "Equipment"),
    EFFECTS("Effekte", "Effects"),
    ATTRIBUTES("Attribute", "Attributes"),
    TAGS("Tags", "Tags"),
    COMPONENTS("Komponenten", "Components"),
    NBT("Block-/Entity-SNBT", "Block/entity SNBT");

    private final String de;
    private final String en;

    Category(String de, String en) {
        this.de = de;
        this.en = en;
    }

    public String label() {
        return com.adhdclient.gui.L.t(de, en);
    }
}
