package com.adhdclient.casino.core.roulette;

import com.adhdclient.casino.core.Odds;

/**
 * Eine Wette auf dem Roulette-Tisch.
 *
 * @param kind   Wettart
 * @param number nur bei {@link Kind#STRAIGHT}: die gesetzte Zahl (0–36 oder {@link Roulette#DOUBLE_ZERO})
 */
public record RouletteBet(Kind kind, int number) {

    public enum Kind {
        RED(Odds.ROULETTE_EVEN_MONEY_PAYOUT),
        BLACK(Odds.ROULETTE_EVEN_MONEY_PAYOUT),
        EVEN(Odds.ROULETTE_EVEN_MONEY_PAYOUT),
        ODD(Odds.ROULETTE_EVEN_MONEY_PAYOUT),
        LOW(Odds.ROULETTE_EVEN_MONEY_PAYOUT),
        HIGH(Odds.ROULETTE_EVEN_MONEY_PAYOUT),
        DOZEN_1(Odds.ROULETTE_DOZEN_PAYOUT),
        DOZEN_2(Odds.ROULETTE_DOZEN_PAYOUT),
        DOZEN_3(Odds.ROULETTE_DOZEN_PAYOUT),
        STRAIGHT(Odds.ROULETTE_STRAIGHT_PAYOUT);

        private final int payout;

        Kind(int payout) {
            this.payout = payout;
        }

        /** Auszahlung als Vielfaches des Einsatzes (inkl. Einsatz). */
        public int payout() {
            return payout;
        }

        public String translationKey() {
            return "luckycasino.roulette.bet." + name().toLowerCase();
        }
    }

    public static RouletteBet of(Kind kind) {
        if (kind == Kind.STRAIGHT) {
            throw new IllegalArgumentException("Einzelzahl braucht eine Zahl");
        }
        return new RouletteBet(kind, -1);
    }

    public static RouletteBet straight(int number) {
        if (number < 0 || number > Roulette.DOUBLE_ZERO) {
            throw new IllegalArgumentException("Ungültige Zahl: " + number);
        }
        return new RouletteBet(Kind.STRAIGHT, number);
    }

    /** Gewinnt diese Wette, wenn {@code pocket} fällt? Bei 0/00 gewinnen nur Einzelzahl-Wetten darauf. */
    public boolean wins(int pocket) {
        if (kind == Kind.STRAIGHT) {
            return pocket == number;
        }
        if (Roulette.isZero(pocket)) {
            return false;
        }
        return switch (kind) {
            case RED -> Roulette.colorOf(pocket) == Roulette.Color.RED;
            case BLACK -> Roulette.colorOf(pocket) == Roulette.Color.BLACK;
            case EVEN -> pocket % 2 == 0;
            case ODD -> pocket % 2 == 1;
            case LOW -> pocket <= 18;
            case HIGH -> pocket >= 19;
            case DOZEN_1 -> pocket <= 12;
            case DOZEN_2 -> pocket >= 13 && pocket <= 24;
            case DOZEN_3 -> pocket >= 25;
            case STRAIGHT -> false;
        };
    }
}
