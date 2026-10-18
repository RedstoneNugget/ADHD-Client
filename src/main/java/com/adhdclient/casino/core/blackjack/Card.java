package com.adhdclient.casino.core.blackjack;

/**
 * Spielkarte.
 *
 * @param rank 1 = Ass, 2–10, 11 = Bube, 12 = Dame, 13 = König
 */
public record Card(int rank, Suit suit) {

    public enum Suit {
        SPADES('♠', false),
        HEARTS('♥', true),
        DIAMONDS('♦', true),
        CLUBS('♣', false);

        private final char symbol;
        private final boolean red;

        Suit(char symbol, boolean red) {
            this.symbol = symbol;
            this.red = red;
        }

        public char symbol() {
            return symbol;
        }

        public boolean isRed() {
            return red;
        }
    }

    public Card {
        if (rank < 1 || rank > 13 || suit == null) {
            throw new IllegalArgumentException("Ungültige Karte: " + rank + " " + suit);
        }
    }

    /** Blackjack-Wert ohne Ass-Sonderregel (Ass = 1, Bildkarten = 10). */
    public int hardValue() {
        return Math.min(rank, 10);
    }

    public boolean isAce() {
        return rank == 1;
    }

    /** Kurzbeschriftung: A, 2–10, J, Q, K. */
    public String rankLabel() {
        return switch (rank) {
            case 1 -> "A";
            case 11 -> "J";
            case 12 -> "Q";
            case 13 -> "K";
            default -> Integer.toString(rank);
        };
    }

    @Override
    public String toString() {
        return rankLabel() + suit.symbol();
    }
}
