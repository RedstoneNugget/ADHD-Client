package com.adhdclient.casino.core.scratch;

import com.adhdclient.casino.core.CasinoRng;

import java.util.ArrayList;
import java.util.List;

/**
 * Ein Rubbellos mit 3×3 Feldern. Drei gleiche Symbole gewinnen.
 *
 * <p>Das Ergebnis steht beim Kauf fest: Zuerst wird (gemäß den Chancen des
 * Modus) ein Gewinnsymbol oder "kein Gewinn" gezogen, danach werden die Felder
 * passend befüllt. Andere Symbole kommen höchstens zweimal vor, damit es nie
 * mehr als einen Gewinn pro Los gibt.</p>
 */
public final class ScratchTicket {

    public static final int FIELDS = 9;

    private final ScratchTier tier;
    private final ScratchSymbol[] symbols;
    private final ScratchSymbol winSymbol;

    private ScratchTicket(ScratchTier tier, ScratchSymbol[] symbols, ScratchSymbol winSymbol) {
        this.tier = tier;
        this.symbols = symbols;
        this.winSymbol = winSymbol;
    }

    public static ScratchTicket generate(CasinoRng rng, ScratchTier tier) {
        ScratchSymbol[] all = ScratchSymbol.values();
        int winIndex = rng.pickByChance(rng.odds().scratchChances());
        ScratchSymbol win = winIndex >= 0 ? all[winIndex] : null;

        List<ScratchSymbol> fields = new ArrayList<>(FIELDS);
        int[] counts = new int[all.length];
        if (win != null) {
            for (int i = 0; i < 3; i++) {
                fields.add(win);
            }
            counts[win.ordinal()] = 3;
        }
        while (fields.size() < FIELDS) {
            // Füller: jedes andere Symbol höchstens zweimal
            List<ScratchSymbol> allowed = new ArrayList<>();
            for (ScratchSymbol s : all) {
                if (s != win && counts[s.ordinal()] < 2) {
                    allowed.add(s);
                }
            }
            ScratchSymbol s = rng.pick(allowed);
            counts[s.ordinal()]++;
            fields.add(s);
        }
        rng.shuffle(fields);
        return new ScratchTicket(tier, fields.toArray(new ScratchSymbol[0]), win);
    }

    /** RTP = Σ Chance × Multiplikator (höchstens ein Gewinn je Los). */
    public static double returnToPlayer(double[] chances) {
        double rtp = 0;
        ScratchSymbol[] all = ScratchSymbol.values();
        for (int i = 0; i < all.length; i++) {
            rtp += chances[i] * all[i].multiplier();
        }
        return rtp;
    }

    public ScratchTier tier() {
        return tier;
    }

    public ScratchSymbol symbol(int field) {
        return symbols[field];
    }

    /** Gewinnsymbol oder null. */
    public ScratchSymbol winSymbol() {
        return winSymbol;
    }

    public boolean isWinningField(int field) {
        return winSymbol != null && symbols[field] == winSymbol;
    }

    public long prize() {
        return winSymbol == null ? 0 : tier.price() * winSymbol.multiplier();
    }
}
