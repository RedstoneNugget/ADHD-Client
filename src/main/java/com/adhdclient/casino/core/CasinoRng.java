package com.adhdclient.casino.core;

import java.util.List;
import java.util.SplittableRandom;

/**
 * Zentrale Zufallsquelle für alle Spiele.
 *
 * <p>Jede Zufallsentscheidung im Casino läuft über diese Klasse. Zusammen mit
 * {@link #odds()} (aktuelles {@link Odds.Profile} des eingestellten
 * {@link LuckMode}) sind damit alle Gewinnchancen an einer Stelle steuerbar.
 * Für Tests kann ein fester Seed übergeben werden.</p>
 */
public final class CasinoRng {

    private final SplittableRandom random;
    private LuckMode mode;

    public CasinoRng(LuckMode mode) {
        this(new SplittableRandom(), mode);
    }

    /** Reproduzierbare Zufallsfolge (für Unit-Tests). */
    public CasinoRng(long seed, LuckMode mode) {
        this(new SplittableRandom(seed), mode);
    }

    private CasinoRng(SplittableRandom random, LuckMode mode) {
        this.random = random;
        this.mode = mode == null ? LuckMode.HOUSE : mode;
    }

    public LuckMode mode() {
        return mode;
    }

    public void setMode(LuckMode mode) {
        if (mode != null) {
            this.mode = mode;
        }
    }

    /** Aktuelle Gewinnchancen/Spielparameter für den eingestellten Modus. */
    public Odds.Profile odds() {
        return Odds.of(mode);
    }

    /** Gleichverteilte Zahl in [0, bound). */
    public int nextInt(int bound) {
        return random.nextInt(bound);
    }

    /** Gleichverteilte Zahl in [min, max] (beide inklusive). */
    public int nextInt(int min, int maxInclusive) {
        return random.nextInt(min, maxInclusive + 1);
    }

    /** Gleichverteilte Kommazahl in [0, 1). */
    public double nextDouble() {
        return random.nextDouble();
    }

    /** true mit Wahrscheinlichkeit {@code probability}. */
    public boolean chance(double probability) {
        return random.nextDouble() < probability;
    }

    /** Index gemäß Gewichten (Gewicht 0 = nie). */
    public int pickWeighted(int[] weights) {
        int total = 0;
        for (int w : weights) {
            total += Math.max(0, w);
        }
        if (total <= 0) {
            throw new IllegalArgumentException("Summe der Gewichte muss > 0 sein");
        }
        int roll = random.nextInt(total);
        for (int i = 0; i < weights.length; i++) {
            roll -= Math.max(0, weights[i]);
            if (roll < 0) {
                return i;
            }
        }
        return weights.length - 1;
    }

    /** Index gemäß Wahrscheinlichkeiten; liefert -1, falls keine getroffen wurde (Rest = "nichts"). */
    public int pickByChance(double[] chances) {
        double roll = random.nextDouble();
        for (int i = 0; i < chances.length; i++) {
            roll -= chances[i];
            if (roll < 0) {
                return i;
            }
        }
        return -1;
    }

    /** Zufälliges Element einer Liste. */
    public <T> T pick(List<T> list) {
        return list.get(random.nextInt(list.size()));
    }

    /** Fisher-Yates-Mischung. */
    public <T> void shuffle(List<T> list) {
        for (int i = list.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            T tmp = list.get(i);
            list.set(i, list.get(j));
            list.set(j, tmp);
        }
    }
}
