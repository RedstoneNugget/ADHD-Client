package com.adhdclient.casino.core.slots;

import com.adhdclient.casino.core.CasinoRng;

/**
 * Logik der 3-Walzen-Slot-Maschine mit einer Gewinnlinie.
 *
 * <p>Regeln: drei gleiche Symbole zahlen {@link SlotSymbol#triplePayout()},
 * genau zwei gleiche (an beliebiger Position) zahlen {@link SlotSymbol#pairPayout()}.
 * Jede Walze wird unabhängig mit den Gewichten des aktuellen Modus gezogen.</p>
 */
public final class SlotMachine {

    private SlotMachine() {
    }

    public static SlotResult spin(CasinoRng rng) {
        int[] weights = rng.odds().slotWeights();
        SlotSymbol[] all = SlotSymbol.values();
        SlotSymbol[] reels = new SlotSymbol[3];
        for (int i = 0; i < 3; i++) {
            reels[i] = all[rng.pickWeighted(weights)];
        }
        return evaluate(reels);
    }

    public static SlotResult evaluate(SlotSymbol[] reels) {
        SlotSymbol a = reels[0];
        SlotSymbol b = reels[1];
        SlotSymbol c = reels[2];
        if (a == b && b == c) {
            return new SlotResult(reels, a, 3, a.triplePayout());
        }
        SlotSymbol pair = null;
        if (a == b || a == c) {
            pair = a;
        } else if (b == c) {
            pair = b;
        }
        if (pair != null) {
            return new SlotResult(reels, pair, 2, pair.pairPayout());
        }
        return new SlotResult(reels, null, 0, 0);
    }

    /** Exakter RTP durch Aufzählung aller Kombinationen. */
    public static double returnToPlayer(int[] weights) {
        double[] p = probabilities(weights);
        SlotSymbol[] all = SlotSymbol.values();
        double rtp = 0;
        for (int a = 0; a < all.length; a++) {
            for (int b = 0; b < all.length; b++) {
                for (int c = 0; c < all.length; c++) {
                    double m = evaluate(new SlotSymbol[]{all[a], all[b], all[c]}).multiplier();
                    rtp += p[a] * p[b] * p[c] * m;
                }
            }
        }
        return rtp;
    }

    /** Wahrscheinlichkeit für den Jackpot (3x Netherstern). */
    public static double jackpotChance(int[] weights) {
        double p = probabilities(weights)[SlotSymbol.STAR.ordinal()];
        return p * p * p;
    }

    private static double[] probabilities(int[] weights) {
        double total = 0;
        for (int w : weights) {
            total += w;
        }
        double[] p = new double[weights.length];
        for (int i = 0; i < weights.length; i++) {
            p[i] = weights[i] / total;
        }
        return p;
    }
}
