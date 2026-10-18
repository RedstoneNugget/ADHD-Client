package com.adhdclient.casino.core.loot;

/**
 * Seltenheitsstufen der Lootbox-Inhalte.
 *
 * @see LootBoxType für die Ziehungs-Gewichte je Kiste
 */
public enum Rarity {
    COMMON("common", 0xFFB0B0B0, 75),
    UNCOMMON("uncommon", 0xFF55D655, 200),
    RARE("rare", 0xFF4A9BFF, 500),
    EPIC("epic", 0xFFB45CFF, 1_500),
    LEGENDARY("legendary", 0xFFFFAA00, 5_000);

    private final String id;
    private final int color;
    private final long chipReward;

    Rarity(String id, int color, long chipReward) {
        this.id = id;
        this.color = color;
        this.chipReward = chipReward;
    }

    /** ARGB-Farbe für Rahmen/Texte. */
    public int color() {
        return color;
    }

    /** Chip-Bonus, wenn statt eines kosmetischen Gegenstands Chips gezogen werden. */
    public long chipReward() {
        return chipReward;
    }

    /** Entschädigung für ein Duplikat (die Hälfte des Chip-Bonus). */
    public long duplicateReward() {
        return chipReward / 2;
    }

    public String translationKey() {
        return "luckycasino.rarity." + id;
    }
}
