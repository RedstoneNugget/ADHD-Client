package com.adhdclient.casino.core.mines;

import com.adhdclient.casino.core.CasinoRng;

import java.util.ArrayList;
import java.util.List;

/**
 * Minenfeld: 5×5 verdeckte Felder, darunter {@code mines} Minen. Jedes aufgedeckte
 * sichere Feld erhöht den Multiplikator; man kann jederzeit auszahlen. Trifft man eine Mine,
 * ist der Einsatz verloren.
 *
 * <p>Fairer Multiplikator nach k sicheren Feldern = 1 / P(k sichere Felder in Folge)
 * = Π (25 − i) / (25 − Minen − i). Der Modus zieht einmalig den Hausvorteil ab, damit
 * ist der RTP für jede Strategie genau (1 − Hausvorteil).</p>
 */
public final class MinesGame {

    public static final int TILES = 25;
    /** Wählbare Minenanzahl in der GUI. */
    public static final int[] MINE_OPTIONS = {1, 3, 5, 10, 20};

    public enum State { ACTIVE, LOST, CASHED_OUT }

    private final long stake;
    private final int mines;
    private final double houseEdge;
    private final boolean[] mine = new boolean[TILES];
    private final boolean[] revealed = new boolean[TILES];
    private int safeRevealed;
    private State state = State.ACTIVE;
    private int explodedTile = -1;

    public MinesGame(CasinoRng rng, long stake, int mines) {
        if (stake <= 0 || mines < 1 || mines >= TILES) {
            throw new IllegalArgumentException("Ungültige Runde");
        }
        this.stake = stake;
        this.mines = mines;
        this.houseEdge = rng.odds().minesHouseEdge();
        List<Integer> tiles = new ArrayList<>();
        for (int i = 0; i < TILES; i++) {
            tiles.add(i);
        }
        rng.shuffle(tiles);
        for (int i = 0; i < mines; i++) {
            mine[tiles.get(i)] = true;
        }
    }

    /** Fairer Multiplikator × (1 − Hausvorteil), abgerundet auf Hundertstel. */
    public static double multiplierFor(int mines, int safe, double houseEdge) {
        if (safe <= 0) {
            return 1.0;
        }
        double m = 1;
        for (int i = 0; i < safe; i++) {
            m *= (double) (TILES - i) / (TILES - mines - i);
        }
        return Math.floor(m * (1 - houseEdge) * 100.0 + 1e-9) / 100.0;
    }

    /**
     * Deckt ein Feld auf.
     *
     * @return true = sicher, false = Mine (Runde verloren)
     */
    public boolean reveal(int tile) {
        if (state != State.ACTIVE || revealed[tile]) {
            throw new IllegalStateException("Feld nicht aufdeckbar");
        }
        revealed[tile] = true;
        if (mine[tile]) {
            explodedTile = tile;
            state = State.LOST;
            return false;
        }
        safeRevealed++;
        if (safeRevealed == TILES - mines) {
            state = State.CASHED_OUT; // alles gefunden → automatisch ausgezahlt
        }
        return true;
    }

    public long cashOut() {
        if (!canCashOut()) {
            throw new IllegalStateException("Auszahlen nicht möglich");
        }
        state = State.CASHED_OUT;
        return payout();
    }

    public boolean canCashOut() {
        return state == State.ACTIVE && safeRevealed > 0;
    }

    public boolean canReveal(int tile) {
        return state == State.ACTIVE && !revealed[tile];
    }

    public double currentMultiplier() {
        return multiplierFor(mines, safeRevealed, houseEdge);
    }

    public double nextMultiplier() {
        return multiplierFor(mines, safeRevealed + 1, houseEdge);
    }

    /** Chance, dass das nächste Feld sicher ist. */
    public double nextSafeChance() {
        int hidden = TILES - safeRevealed;
        return (double) (hidden - mines) / hidden;
    }

    public long payout() {
        return state == State.CASHED_OUT ? (long) Math.floor(stake * currentMultiplier()) : 0;
    }

    public boolean isMine(int tile) {
        return mine[tile];
    }

    public boolean isRevealed(int tile) {
        return revealed[tile];
    }

    public int explodedTile() {
        return explodedTile;
    }

    public boolean isFinished() {
        return state != State.ACTIVE;
    }

    public State state() {
        return state;
    }

    public long stake() {
        return stake;
    }

    public int mines() {
        return mines;
    }

    public int safeRevealed() {
        return safeRevealed;
    }
}
