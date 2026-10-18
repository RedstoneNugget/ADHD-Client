package com.adhdclient.casino.core.scratch;

/** Preisstufen der Rubbellose. Gewinne skalieren mit dem Lospreis. */
public enum ScratchTier {
    BRONZE("bronze", 50),
    SILVER("silver", 200),
    GOLD("gold", 1_000);

    private final String id;
    private final long price;

    ScratchTier(String id, long price) {
        this.id = id;
        this.price = price;
    }

    public long price() {
        return price;
    }

    public String translationKey() {
        return "luckycasino.scratch.tier." + id;
    }
}
