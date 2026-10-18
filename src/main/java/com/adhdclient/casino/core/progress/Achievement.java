package com.adhdclient.casino.core.progress;

/**
 * Freischaltbare Erfolge. Die Bedingungen prüft {@link com.adhdclient.casino.core.Casino}.
 */
public enum Achievement {
    FIRST_WIN("first_win", "minecraft:emerald"),
    FIRST_JACKPOT("first_jackpot", "minecraft:nether_star"),
    WIN_STREAK_10("win_streak_10", "minecraft:blaze_powder"),
    BROKE("broke", "minecraft:bowl"),
    EMERGENCY_LOAN("emergency_loan", "minecraft:paper"),
    HIGH_ROLLER("high_roller", "minecraft:gold_block"),
    RICH("rich", "minecraft:emerald_block"),
    TYCOON("tycoon", "minecraft:diamond_block"),
    NATURAL("natural", "minecraft:book"),
    BULLSEYE("bullseye", "minecraft:target"),
    TO_THE_MOON("to_the_moon", "minecraft:firework_rocket"),
    DOUBLE_TROUBLE("double_trouble", "minecraft:gold_nugget"),
    SCRATCH_LUCK("scratch_luck", "minecraft:brush"),
    WHEEL_SPIN("wheel_spin", "minecraft:clock"),
    LEGENDARY_LOOT("legendary_loot", "minecraft:dragon_egg"),
    ALL_ROUNDER("all_rounder", "minecraft:compass"),
    REGULAR("regular", "minecraft:cake"),
    COLLECTOR("collector", "minecraft:chest"),
    PLINKO_PRO("plinko_pro", "minecraft:slime_ball"),
    MINESWEEPER("minesweeper", "minecraft:tnt"),
    LEVEL_10("level_10", "minecraft:experience_bottle"),
    DILIGENT("diligent", "minecraft:writable_book");

    // Schwellenwerte der Bedingungen
    public static final int WIN_STREAK = 10;
    public static final long HIGH_ROLLER_BET = 5_000;
    public static final long RICH_BALANCE = 10_000;
    public static final long TYCOON_BALANCE = 100_000;
    public static final double MOON_MULTIPLIER = 10.0;
    public static final int COIN_STREAK = 5;
    public static final int SCRATCH_MULTIPLIER = 10;
    public static final int REGULAR_ROUNDS = 100;
    public static final int COLLECTOR_ITEMS = 8;
    public static final double PLINKO_MULTIPLIER = 10.0;
    public static final double MINES_MULTIPLIER = 5.0;
    public static final int LEVEL_GOAL = 10;
    public static final int MISSIONS_GOAL = 10;

    private final String id;
    private final String iconItem;

    Achievement(String id, String iconItem) {
        this.id = id;
        this.iconItem = iconItem;
    }

    public String id() {
        return id;
    }

    public String iconItem() {
        return iconItem;
    }

    public String translationKey() {
        return "luckycasino.achievement." + id;
    }

    public String descriptionKey() {
        return "luckycasino.achievement." + id + ".desc";
    }
}
