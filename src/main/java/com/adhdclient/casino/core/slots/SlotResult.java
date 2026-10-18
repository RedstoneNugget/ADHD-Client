package com.adhdclient.casino.core.slots;

/**
 * Ergebnis einer Drehung.
 *
 * @param reels      die drei Symbole auf der Gewinnlinie
 * @param winSymbol  Symbol, das gewonnen hat (oder null)
 * @param matches    Anzahl gleicher Symbole (0, 2 oder 3)
 * @param multiplier Auszahlung als Vielfaches des Einsatzes (inkl. Einsatz)
 */
public record SlotResult(SlotSymbol[] reels, SlotSymbol winSymbol, int matches, double multiplier) {

    public boolean isJackpot() {
        return matches == 3 && winSymbol != null && winSymbol.isJackpot();
    }

    /** Auszahlung in Chips (abgerundet). */
    public long payout(long bet) {
        return (long) Math.floor(bet * multiplier);
    }

    /** Liegt die Walze {@code reel} auf einem Gewinnsymbol? (zum Hervorheben in der GUI) */
    public boolean isWinningReel(int reel) {
        return winSymbol != null && multiplier > 0 && reels[reel] == winSymbol;
    }
}
