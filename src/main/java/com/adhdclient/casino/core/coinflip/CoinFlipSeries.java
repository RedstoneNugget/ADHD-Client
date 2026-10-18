package com.adhdclient.casino.core.coinflip;

import com.adhdclient.casino.core.CasinoRng;
import com.adhdclient.casino.core.Odds;

/**
 * Münzwurf mit optionaler "Doppelt oder nichts"-Serie.
 *
 * <p>Ablauf: Einsatz setzen → werfen. Bei Treffer verdoppelt sich der Topf
 * ({@link Odds#COIN_PAYOUT}x) und der Spieler entscheidet: auszahlen oder
 * den ganzen Topf erneut riskieren. Bei einem Fehlwurf ist der Topf weg.</p>
 */
public final class CoinFlipSeries {

    public enum State { ACTIVE, LOST, CASHED_OUT }

    private final long stake;
    private long pot;
    private int streak;
    private State state = State.ACTIVE;
    private CoinSide lastResult;

    public CoinFlipSeries(long stake) {
        if (stake <= 0) {
            throw new IllegalArgumentException("Einsatz muss positiv sein");
        }
        this.stake = stake;
        this.pot = stake;
    }

    /**
     * Wirft die Münze. Die Chance auf die getippte Seite kommt aus dem aktuellen Modus.
     *
     * @return true bei Treffer
     */
    public boolean flip(CasinoRng rng, CoinSide choice) {
        if (!canFlip()) {
            throw new IllegalStateException("Serie ist beendet");
        }
        boolean win = rng.chance(rng.odds().coinWinChance());
        lastResult = win ? choice : choice.opposite();
        if (win) {
            pot *= Odds.COIN_PAYOUT;
            streak++;
        } else {
            pot = 0;
            state = State.LOST;
        }
        return win;
    }

    /** Auszahlen ist nach mindestens einem Treffer möglich. */
    public long cashOut() {
        if (!canCashOut()) {
            throw new IllegalStateException("Nichts auszuzahlen");
        }
        state = State.CASHED_OUT;
        return pot;
    }

    public boolean canFlip() {
        return state == State.ACTIVE && streak < Odds.COIN_MAX_STREAK;
    }

    public boolean canCashOut() {
        return state == State.ACTIVE && streak > 0;
    }

    /** Läuft noch (vor dem ersten Wurf oder nach Treffern)? */
    public boolean isActive() {
        return state == State.ACTIVE;
    }

    public long stake() {
        return stake;
    }

    public long pot() {
        return pot;
    }

    public int streak() {
        return streak;
    }

    public State state() {
        return state;
    }

    public CoinSide lastResult() {
        return lastResult;
    }
}
