package com.adhdclient.casino.core.wheel;

import com.adhdclient.casino.core.CasinoRng;

import java.time.LocalDate;
import java.util.List;

/**
 * Tägliches Gratis-Glücksrad mit 16 gleich großen Feldern.
 *
 * <p>Jedes Feld hat dieselbe Chance (1/16) – was man sieht, ist also die echte
 * Wahrscheinlichkeit. Erwarteter Chip-Gewinn: 5.100 / 16 ≈ 319 Chips plus
 * Boni und Freispiele. Unabhängig vom Glücksfaktor-Modus.</p>
 */
public final class LuckyWheel {

    public enum PrizeType { CHIPS, BONUS, FREE_SPINS }

    /**
     * @param type   Art des Preises
     * @param amount Chips, Bonus-Multiplikator oder Anzahl Freispiele
     */
    public record Segment(PrizeType type, int amount) {
        public boolean isJackpot() {
            return type == PrizeType.CHIPS && amount >= 2_500;
        }
    }

    public static final List<Segment> SEGMENTS = List.of(
            new Segment(PrizeType.CHIPS, 50),
            new Segment(PrizeType.FREE_SPINS, 1),
            new Segment(PrizeType.CHIPS, 100),
            new Segment(PrizeType.BONUS, 2),
            new Segment(PrizeType.CHIPS, 50),
            new Segment(PrizeType.CHIPS, 250),
            new Segment(PrizeType.CHIPS, 100),
            new Segment(PrizeType.FREE_SPINS, 3),
            new Segment(PrizeType.CHIPS, 50),
            new Segment(PrizeType.CHIPS, 500),
            new Segment(PrizeType.CHIPS, 100),
            new Segment(PrizeType.BONUS, 3),
            new Segment(PrizeType.CHIPS, 150),
            new Segment(PrizeType.CHIPS, 1_000),
            new Segment(PrizeType.CHIPS, 250),
            new Segment(PrizeType.CHIPS, 2_500));

    private LuckyWheel() {
    }

    /** Liefert den Index des gewonnenen Feldes. */
    public static int spin(CasinoRng rng) {
        return rng.nextInt(SEGMENTS.size());
    }

    /** Darf heute (echtes Datum) gedreht werden? */
    public static boolean isAvailable(String lastSpinDay, LocalDate today) {
        return lastSpinDay == null || !lastSpinDay.equals(today.toString());
    }
}
