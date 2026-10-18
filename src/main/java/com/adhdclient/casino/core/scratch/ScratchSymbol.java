package com.adhdclient.casino.core.scratch;

import com.adhdclient.casino.core.Odds;

/** Symbole auf dem Rubbellos. Drei gleiche gewinnen {@link #multiplier()} × Lospreis. */
public enum ScratchSymbol {
    COAL("coal", "minecraft:coal"),
    IRON("iron", "minecraft:iron_ingot"),
    GOLD("gold", "minecraft:gold_ingot"),
    EMERALD("emerald", "minecraft:emerald"),
    DIAMOND("diamond", "minecraft:diamond"),
    STAR("star", "minecraft:nether_star");

    private final String id;
    private final String itemId;

    ScratchSymbol(String id, String itemId) {
        this.id = id;
        this.itemId = itemId;
    }

    public String itemId() {
        return itemId;
    }

    public int multiplier() {
        return Odds.SCRATCH_MULTIPLIERS[ordinal()];
    }

    public String translationKey() {
        return "luckycasino.slots.symbol." + id;
    }
}
