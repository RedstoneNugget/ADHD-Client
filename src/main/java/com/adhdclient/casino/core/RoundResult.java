package com.adhdclient.casino.core;

/**
 * Abgerechnete Runde.
 *
 * @param stake           Einsatz (0 bei Freispielen)
 * @param payout          Gesamtauszahlung inkl. Einsatz und Bonus
 * @param profit          Reingewinn (kann negativ sein)
 * @param bonusMultiplier angewendeter Bonus-Multiplikator (1 = keiner)
 */
public record RoundResult(GameType game, long stake, long payout, long profit, int bonusMultiplier, Outcome outcome) {

    public enum Outcome { WIN, LOSS, PUSH }

    public boolean isWin() {
        return outcome == Outcome.WIN;
    }

    public boolean isLoss() {
        return outcome == Outcome.LOSS;
    }

    public boolean bonusApplied() {
        return bonusMultiplier > 1;
    }
}
