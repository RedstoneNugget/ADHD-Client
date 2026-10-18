package com.adhdclient.casino.core.coinflip;

/** Seiten der Münze. */
public enum CoinSide {
    HEADS, TAILS;

    public CoinSide opposite() {
        return this == HEADS ? TAILS : HEADS;
    }

    public String translationKey() {
        return "luckycasino.coin." + name().toLowerCase();
    }
}
