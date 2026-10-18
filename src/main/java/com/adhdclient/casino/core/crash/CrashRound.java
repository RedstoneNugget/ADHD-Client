package com.adhdclient.casino.core.crash;

import com.adhdclient.casino.core.CasinoRng;
import com.adhdclient.casino.core.Odds;

/**
 * Eine Crash-Runde: Der Multiplikator wächst exponentiell, bis er den vorab
 * gezogenen Crash-Punkt erreicht. Wer vorher auszahlt, bekommt Einsatz × Multiplikator.
 *
 * <p>Crash-Punkt = (1 − Hausvorteil) / U mit U gleichverteilt in (0, 1].
 * Damit gilt P(Crash-Punkt ≥ x) = (1 − Hausvorteil) / x, d. h. jede Cash-out-Strategie
 * hat genau den RTP (1 − Hausvorteil). Liegt der Punkt unter 1,00x, crasht die Runde sofort.</p>
 *
 * <p>Zeit wird von außen übergeben (Millisekunden), damit die Logik ohne Uhr testbar ist.</p>
 */
public final class CrashRound {

    public enum State { RUNNING, CASHED_OUT, CRASHED }

    private final long stake;
    private final double crashPoint;
    private final long startMs;
    private final double autoCashOut;
    private State state = State.RUNNING;
    private double cashOutMultiplier;
    private long endMs;

    /**
     * @param autoCashOut automatischer Ausstieg bei diesem Multiplikator (≤ 1 = aus)
     */
    public CrashRound(long stake, double crashPoint, long startMs, double autoCashOut) {
        if (stake <= 0) {
            throw new IllegalArgumentException("Einsatz muss positiv sein");
        }
        this.stake = stake;
        this.crashPoint = crashPoint;
        this.startMs = startMs;
        this.autoCashOut = autoCashOut;
        update(startMs);
    }

    /** Zieht einen Crash-Punkt gemäß Hausvorteil des aktuellen Modus. */
    public static double drawCrashPoint(CasinoRng rng) {
        double edge = rng.odds().crashHouseEdge();
        double u = 1.0 - rng.nextDouble(); // (0, 1]
        double point = (1.0 - edge) / u;
        if (point < 1.0) {
            return 1.0; // Sofort-Crash
        }
        return Math.min(point, Odds.CRASH_MAX_MULTIPLIER);
    }

    /** Multiplikator nach {@code elapsedMs} Millisekunden. */
    public static double multiplierAt(long elapsedMs) {
        return Math.exp(Odds.CRASH_GROWTH_PER_SECOND * Math.max(0, elapsedMs) / 1000.0);
    }

    /** Zeit in ms, bis der Multiplikator {@code multiplier} erreicht. */
    public static long timeToReach(double multiplier) {
        return (long) Math.ceil(Math.log(Math.max(1.0, multiplier)) / Odds.CRASH_GROWTH_PER_SECOND * 1000.0);
    }

    /** Fortschreiben bis {@code nowMs}: prüft Crash und Auto-Cash-out. */
    public void update(long nowMs) {
        if (state != State.RUNNING) {
            return;
        }
        double m = multiplierAt(nowMs - startMs);
        boolean autoActive = autoCashOut > 1.0;
        if (autoActive && autoCashOut < crashPoint && m >= autoCashOut) {
            state = State.CASHED_OUT;
            cashOutMultiplier = autoCashOut;
            endMs = startMs + timeToReach(autoCashOut);
        } else if (m >= crashPoint) {
            state = State.CRASHED;
            endMs = startMs + timeToReach(crashPoint);
        }
    }

    /** Manuelles Auszahlen. Liefert true, wenn rechtzeitig (vor dem Crash). */
    public boolean cashOut(long nowMs) {
        update(nowMs);
        if (state != State.RUNNING) {
            return state == State.CASHED_OUT;
        }
        cashOutMultiplier = multiplierAt(nowMs - startMs);
        state = State.CASHED_OUT;
        endMs = nowMs;
        return true;
    }

    /** Aktuell angezeigter Multiplikator (eingefroren nach Ende der Runde). */
    public double currentMultiplier(long nowMs) {
        return switch (state) {
            case RUNNING -> Math.min(multiplierAt(nowMs - startMs), crashPoint);
            case CASHED_OUT -> cashOutMultiplier;
            case CRASHED -> crashPoint;
        };
    }

    public long payout() {
        return state == State.CASHED_OUT ? (long) Math.floor(stake * cashOutMultiplier) : 0;
    }

    public boolean isRunning() {
        return state == State.RUNNING;
    }

    public State state() {
        return state;
    }

    public long stake() {
        return stake;
    }

    /** Erst nach Rundenende anzeigen! */
    public double crashPoint() {
        return crashPoint;
    }

    public double cashOutMultiplier() {
        return cashOutMultiplier;
    }

    public long startMs() {
        return startMs;
    }

    /** Zeitpunkt des Rundenendes (Crash bzw. Auszahlung). */
    public long endMs() {
        return endMs;
    }

    public double autoCashOut() {
        return autoCashOut;
    }
}
