package com.adhdclient.casino.core.slots;

import com.adhdclient.casino.core.Odds;

/**
 * Symbole der Slot-Maschine, von wenig wertvoll (Kohle) bis Jackpot (Netherstern).
 * Die Auszahlungen stehen zentral in {@link Odds#SLOT_PAIR_PAYOUT} / {@link Odds#SLOT_TRIPLE_PAYOUT}.
 */
public enum SlotSymbol {
    COAL("coal", "minecraft:coal"),
    REDSTONE("redstone", "minecraft:redstone"),
    LAPIS("lapis", "minecraft:lapis_lazuli"),
    IRON("iron", "minecraft:iron_ingot"),
    GOLD("gold", "minecraft:gold_ingot"),
    EMERALD("emerald", "minecraft:emerald"),
    CREEPER("creeper", "minecraft:creeper_head"),
    DIAMOND("diamond", "minecraft:diamond"),
    STAR("star", "minecraft:nether_star");

    private final String id;
    private final String itemId;

    SlotSymbol(String id, String itemId) {
        this.id = id;
        this.itemId = itemId;
    }

    public String itemId() {
        return itemId;
    }

    public String translationKey() {
        return "luckycasino.slots.symbol." + id;
    }

    /** Auszahlung bei genau zwei gleichen Symbolen. */
    public double pairPayout() {
        return Odds.SLOT_PAIR_PAYOUT[ordinal()];
    }

    /** Auszahlung bei drei gleichen Symbolen. */
    public double triplePayout() {
        return Odds.SLOT_TRIPLE_PAYOUT[ordinal()];
    }

    public boolean isJackpot() {
        return this == STAR;
    }
}
