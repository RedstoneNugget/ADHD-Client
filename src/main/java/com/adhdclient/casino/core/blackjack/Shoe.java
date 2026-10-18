package com.adhdclient.casino.core.blackjack;

import com.adhdclient.casino.core.CasinoRng;

import java.util.ArrayList;
import java.util.List;

/**
 * Kartenschlitten mit einem oder mehreren Decks. Wird neu gemischt,
 * sobald weniger als ein Viertel der Karten übrig ist.
 */
public final class Shoe {

    /** Anteil der Karten, ab dem vor einer neuen Runde gemischt wird. */
    private static final double RESHUFFLE_BELOW = 0.25;

    private final int decks;
    private final CasinoRng rng;
    private final List<Card> cards = new ArrayList<>();

    public Shoe(int decks, CasinoRng rng) {
        if (decks < 1) {
            throw new IllegalArgumentException("Mindestens ein Deck");
        }
        this.decks = decks;
        this.rng = rng;
        shuffle();
    }

    public void shuffle() {
        cards.clear();
        for (int d = 0; d < decks; d++) {
            for (Card.Suit suit : Card.Suit.values()) {
                for (int rank = 1; rank <= 13; rank++) {
                    cards.add(new Card(rank, suit));
                }
            }
        }
        rng.shuffle(cards);
    }

    public Card draw() {
        if (cards.isEmpty()) {
            shuffle();
        }
        return cards.removeLast();
    }

    public boolean needsShuffle() {
        return cards.size() < decks * 52 * RESHUFFLE_BELOW;
    }

    public int remaining() {
        return cards.size();
    }

    public int decks() {
        return decks;
    }
}
