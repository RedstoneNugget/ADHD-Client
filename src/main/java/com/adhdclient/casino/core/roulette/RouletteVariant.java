package com.adhdclient.casino.core.roulette;

/**
 * Kesselvariante – bestimmt den Hausvorteil beim Roulette.
 * Die Auszahlungen sind in allen Varianten gleich (36 für Plein usw.),
 * nur die Anzahl der Fächer ändert sich.
 */
public enum RouletteVariant {
    /** 36 Fächer ohne Null → RTP 36/36 = 100 % (fair). */
    NO_ZERO(new int[]{32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23, 10, 5, 24,
            16, 33, 1, 20, 14, 31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26}),
    /** Europäisch: 37 Fächer mit einer Null → RTP 36/37 ≈ 97,30 %. */
    SINGLE_ZERO(new int[]{0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23, 10, 5,
            24, 16, 33, 1, 20, 14, 31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26}),
    /** Amerikanisch: 38 Fächer mit 0 und 00 → RTP 36/38 ≈ 94,74 %. */
    DOUBLE_ZERO(new int[]{0, 28, 9, 26, 30, 11, 7, 20, 32, 17, 5, 22, 34, 15, 3, 24, 36, 13, 1,
            Roulette.DOUBLE_ZERO, 27, 10, 25, 29, 12, 8, 19, 31, 18, 6, 21, 33, 16, 4, 23, 35, 14, 2});

    private final int[] wheelOrder;

    RouletteVariant(int[] wheelOrder) {
        this.wheelOrder = wheelOrder;
    }

    /** Zahlen in der Reihenfolge, in der sie auf dem Kessel liegen. */
    public int[] wheelOrder() {
        return wheelOrder.clone();
    }

    public int pocketCount() {
        return wheelOrder.length;
    }

    public int pocketAt(int index) {
        return wheelOrder[index];
    }

    public int indexOf(int number) {
        for (int i = 0; i < wheelOrder.length; i++) {
            if (wheelOrder[i] == number) {
                return i;
            }
        }
        return -1;
    }

    public boolean hasZero() {
        return this != NO_ZERO;
    }

    public boolean hasDoubleZero() {
        return this == DOUBLE_ZERO;
    }

    /** Jede Wette deckt k Zahlen mit Auszahlung 36/k ab → RTP = 36 / Fächerzahl. */
    public double returnToPlayer() {
        return 36.0 / wheelOrder.length;
    }
}
