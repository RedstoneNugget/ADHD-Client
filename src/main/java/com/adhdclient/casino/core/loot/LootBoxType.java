package com.adhdclient.casino.core.loot;

/**
 * Kaufbare Kisten. Gewichte der Seltenheiten (Common … Legendary):
 * <pre>
 * Holzkiste     (250 Chips):  60 / 25 / 10 /  4 / 1   (%)
 * Diamantkiste (1000 Chips):  25 / 35 / 25 / 11 / 4   (%)
 * </pre>
 * Danach zu 50 % Chip-Bonus der Seltenheit, zu 50 % ein zufälliger kosmetischer
 * Gegenstand dieser Seltenheit (Duplikat = halber Chip-Bonus).
 */
public enum LootBoxType {
    WOODEN("wooden", 250, "minecraft:chest", new int[]{60, 25, 10, 4, 1}),
    DIAMOND("diamond", 1_000, "minecraft:ender_chest", new int[]{25, 35, 25, 11, 4});

    private final String id;
    private final long price;
    private final String itemId;
    private final int[] rarityWeights;

    LootBoxType(String id, long price, String itemId, int[] rarityWeights) {
        this.id = id;
        this.price = price;
        this.itemId = itemId;
        this.rarityWeights = rarityWeights;
    }

    public long price() {
        return price;
    }

    public String itemId() {
        return itemId;
    }

    public int[] rarityWeights() {
        return rarityWeights.clone();
    }

    public String translationKey() {
        return "luckycasino.loot.box." + id;
    }
}
