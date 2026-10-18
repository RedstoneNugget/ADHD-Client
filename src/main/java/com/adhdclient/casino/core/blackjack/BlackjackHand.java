package com.adhdclient.casino.core.blackjack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Hand eines Spielers oder des Dealers. */
public final class BlackjackHand {

    private final List<Card> cards = new ArrayList<>();

    public void add(Card card) {
        cards.add(card);
    }

    public List<Card> cards() {
        return Collections.unmodifiableList(cards);
    }

    public int size() {
        return cards.size();
    }

    /** Bester Wert ≤ 21 (ein Ass zählt 11, wenn das nicht überkauft). */
    public int total() {
        int sum = 0;
        boolean ace = false;
        for (Card c : cards) {
            sum += c.hardValue();
            ace |= c.isAce();
        }
        return ace && sum + 10 <= 21 ? sum + 10 : sum;
    }

    /** "Soft": ein Ass zählt gerade als 11. */
    public boolean isSoft() {
        int sum = 0;
        boolean ace = false;
        for (Card c : cards) {
            sum += c.hardValue();
            ace |= c.isAce();
        }
        return ace && sum + 10 <= 21;
    }

    public boolean isBlackjack() {
        return cards.size() == 2 && total() == 21;
    }

    public boolean isBust() {
        return total() > 21;
    }

    /** Wert nur der ersten {@code count} Karten (für die verdeckte Dealer-Karte in der GUI). */
    public int totalOfFirst(int count) {
        BlackjackHand h = new BlackjackHand();
        for (int i = 0; i < Math.min(count, cards.size()); i++) {
            h.add(cards.get(i));
        }
        return h.total();
    }
}
