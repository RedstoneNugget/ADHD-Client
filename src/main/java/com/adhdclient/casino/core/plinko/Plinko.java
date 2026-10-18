package com.adhdclient.casino.core.plinko;

import com.adhdclient.casino.core.CasinoRng;

/**
 * Plinko: Eine Kugel fällt durch {@link #ROWS} Stiftreihen und springt an jedem Stift
 * mit 50 % nach links oder rechts. Das Fach unten (0 … ROWS) bestimmt den Multiplikator.
 *
 * <p>Die Wahrscheinlichkeit für Fach k ist binomial: C(12, k) / 4096. Die Grundtabellen
 * sind so abgestimmt, dass der RTP ≈ 100 % beträgt; der Glücksfaktor-Modus multipliziert
 * alle Felder mit (1 − Hausvorteil), siehe {@link com.adhdclient.casino.core.Odds}.</p>
 */
public final class Plinko {

    public static final int ROWS = 12;

    public enum Risk {
        /** RTP 100,03 %, Gewinn (>1x) in 38,8 % der Würfe, max. 8,4x. */
        LOW(new double[]{8.4, 3, 1.6, 1.4, 1.1, 1.0, 0.55}),
        /** RTP 99,94 %, max. 24x. */
        MEDIUM(new double[]{24, 11, 4, 2, 1.1, 0.63, 0.31}),
        /** RTP 100,02 %, Gewinn (>1x) nur in 14,6 % der Würfe, max. 170x. */
        HIGH(new double[]{170, 24, 8.1, 2, 0.7, 0.2, 0.24});

        private final double[] table;

        Risk(double[] half) {
            // symmetrische Tabelle aus der linken Hälfte inkl. Mitte bauen
            table = new double[ROWS + 1];
            for (int k = 0; k <= ROWS; k++) {
                table[k] = half[Math.min(k, ROWS - k)];
            }
        }

        /** Grund-Multiplikatoren (Fair-Modus) für die Fächer 0 … ROWS. */
        public double[] baseMultipliers() {
            return table.clone();
        }

        public String translationKey() {
            return "luckycasino.plinko.risk." + name().toLowerCase();
        }

        public Risk next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    /**
     * Ergebnis eines Wurfs.
     *
     * @param path       Richtung an jeder Reihe (true = rechts)
     * @param bucket     Fach (Anzahl der Rechts-Sprünge)
     * @param multiplier angewendeter Multiplikator (inkl. Hausvorteil)
     */
    public record Drop(boolean[] path, int bucket, double multiplier, long payout) {
    }

    private Plinko() {
    }

    /** Multiplikator eines Fachs im aktuellen Modus (abgerundet auf Hundertstel). */
    public static double multiplier(Risk risk, int bucket, double houseEdge) {
        return Math.floor(risk.table[bucket] * (1.0 - houseEdge) * 100.0 + 1e-9) / 100.0;
    }

    public static Drop drop(CasinoRng rng, Risk risk, long stake) {
        boolean[] path = new boolean[ROWS];
        int bucket = 0;
        for (int i = 0; i < ROWS; i++) {
            path[i] = rng.chance(0.5);
            if (path[i]) {
                bucket++;
            }
        }
        double m = multiplier(risk, bucket, rng.odds().plinkoHouseEdge());
        return new Drop(path, bucket, m, (long) Math.floor(stake * m));
    }

    /** Wahrscheinlichkeit für Fach k: C(ROWS, k) / 2^ROWS. */
    public static double bucketChance(int k) {
        double c = 1;
        for (int i = 0; i < k; i++) {
            c = c * (ROWS - i) / (i + 1);
        }
        return c / (1L << ROWS);
    }

    /** Exakter RTP einer Risikostufe bei gegebenem Hausvorteil. */
    public static double returnToPlayer(Risk risk, double houseEdge) {
        double rtp = 0;
        for (int k = 0; k <= ROWS; k++) {
            rtp += bucketChance(k) * multiplier(risk, k, houseEdge);
        }
        return rtp;
    }
}
