package com.adhdclient.casino.core.blackjack;

import com.adhdclient.casino.core.Odds;

/**
 * Tischregeln (abhängig vom Glücksfaktor, siehe {@link Odds}).
 *
 * @param decks            Anzahl Decks
 * @param blackjackPayout  Gewinnanteil bei Blackjack (1,5 = 3:2; 1,2 = 6:5)
 * @param dealerHitsSoft17 Dealer zieht bei Soft 17
 */
public record BlackjackRules(int decks, double blackjackPayout, boolean dealerHitsSoft17) {

    public static BlackjackRules of(Odds.Profile profile) {
        return new BlackjackRules(profile.blackjackDecks(), profile.blackjackPayout(), profile.dealerHitsSoft17());
    }
}
