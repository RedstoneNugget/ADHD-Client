package com.adhdclient.casino.core;

/**
 * Alle Spiele mit Einsatz, für die Statistiken (Runden, Win-Rate, Netto) geführt werden.
 * Glücksrad und Lootboxen sind keine Einsatz-Spiele und tauchen hier deshalb nicht auf.
 */
public enum GameType {
    SLOTS("slots", "minecraft:diamond"),
    ROULETTE("roulette", "minecraft:clock"),
    COINFLIP("coinflip", "minecraft:gold_nugget"),
    BLACKJACK("blackjack", "minecraft:paper"),
    CRASH("crash", "minecraft:firework_rocket"),
    SCRATCH("scratch", "minecraft:brush"),
    PLINKO("plinko", "minecraft:slime_ball"),
    MINES("mines", "minecraft:tnt");

    private final String id;
    private final String iconItem;

    GameType(String id, String iconItem) {
        this.id = id;
        this.iconItem = iconItem;
    }

    public String id() {
        return id;
    }

    /** Item-ID für das Symbol in der GUI (wird erst dort aufgelöst). */
    public String iconItem() {
        return iconItem;
    }

    public String translationKey() {
        return "luckycasino.game." + id;
    }
}
