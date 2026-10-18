package com.adhdclient.casino.core;

import com.adhdclient.casino.core.plinko.Plinko;
import com.adhdclient.casino.core.roulette.RouletteVariant;
import com.adhdclient.casino.core.scratch.ScratchTicket;
import com.adhdclient.casino.core.slots.SlotMachine;

/**
 * Zentrale Tabelle aller Gewinnchancen, Auszahlungen und Spielparameter.
 *
 * <p>Alle Spiele lesen ihre Wahrscheinlichkeiten ausschließlich über
 * {@link CasinoRng#odds()} aus einem {@link Profile} dieser Klasse. Wer die
 * Chancen ändern will, ändert sie <b>nur hier</b>.</p>
 *
 * <h2>Begriffe</h2>
 * <ul>
 *   <li><b>Auszahlung "x-fach"</b>: Gesamtbetrag, der zurückgezahlt wird, inklusive Einsatz.
 *       2x bedeutet also Einsatz zurück + gleich viel Gewinn.</li>
 *   <li><b>RTP</b> (Return to Player): erwartete Auszahlung pro eingesetztem Chip.</li>
 *   <li><b>Hausvorteil</b> = 1 − RTP.</li>
 * </ul>
 *
 * <h2>Hausvorteil pro Spiel und Modus</h2>
 * <pre>
 * Spiel        | FAIR     | HOUSE    | HARDCORE | Herleitung
 * -------------+----------+----------+----------+-------------------------------------------
 * Slots        |  0,0 %   |  5,0 %   | 12,0 %   | exakt (Aufzählung aller 9^3 Walzenkombinationen)
 * Roulette     |  0,0 %   |  2,7 %   |  5,3 %   | exakt (36/36, 36/37, 36/38 Fächer)
 * Münzwurf     |  0,0 %   |  4,0 %   | 10,0 %   | exakt (1 − 2·p)
 * Blackjack    | ~0,4 %   | ~1,2 %   | ~2,6 %   | Simulation mit Basisstrategie (ohne Split)
 * Crash        |  0,0 %   |  3,0 %   |  8,0 %   | exakt, für jede Cash-out-Strategie gleich
 * Rubbellose   |  0,0 %   |  9,5 %   | 20,0 %   | exakt (Summe Chance × Multiplikator)
 * Plinko       | ~0,0 %   |  3,0 %   |  8,0 %   | exakt (Binomialverteilung, Tabelle "Mittel")
 * Minenfeld    | ~0,0 %   |  3,0 %   |  8,0 %   | exakt für jede Strategie (+ Rundung auf 0,01x)
 * </pre>
 * Die Werte werden von den Unit-Tests ({@code OddsTest}) nachgerechnet.
 * Glücksrad und Lootboxen sind keine Einsatzspiele und hängen nicht vom Modus ab.
 */
public final class Odds {

    private Odds() {
    }

    // ------------------------------------------------------------------
    // Guthaben & Einsätze
    // ------------------------------------------------------------------

    /** Startguthaben für neue Spielstände. */
    public static final long START_BALANCE = 1_000;
    /** Voreingestellter Einsatz. */
    public static final long DEFAULT_BET = 10;
    /** Kleinster erlaubter Einsatz. */
    public static final long MIN_BET = 1;
    /** Größter erlaubter Einsatz (schützt vor Überläufen bei 1000x-Auszahlungen). */
    public static final long MAX_BET = 100_000_000L;

    /** Notkredit: Betrag, der bei Guthaben 0 gewährt wird. */
    public static final long LOAN_AMOUNT = 100;
    /** Notkredit: Wartezeit zwischen zwei Krediten (10 Minuten echte Zeit). */
    public static final long LOAN_COOLDOWN_MS = 10L * 60L * 1000L;

    /** Freispiele (aus dem Glücksrad) laufen an der Slot-Maschine mit diesem festen Einsatz. */
    public static final long FREE_SPIN_BET = 100;

    // ------------------------------------------------------------------
    // Slot-Maschine – Auszahlungstabelle (für alle Modi gleich)
    // Reihenfolge der Symbole: siehe SlotSymbol (Kohle … Netherstern)
    // ------------------------------------------------------------------

    /** Auszahlung bei genau zwei gleichen Symbolen (beliebige Position). */
    public static final double[] SLOT_PAIR_PAYOUT = {0, 1, 2, 2, 3, 4, 5, 8, 10};
    /** Auszahlung bei drei gleichen Symbolen. 3x Netherstern = Jackpot. */
    public static final double[] SLOT_TRIPLE_PAYOUT = {5, 10, 15, 25, 40, 60, 100, 250, 1000};

    /*
     * Walzengewichte je Modus (jede der drei Walzen nutzt dieselben Gewichte).
     * Exakt nachgerechnet:
     *   FAIR     : RTP 100,01 %, Jackpot 1 : 7.762,  Gewinn (>1x) in 18,6 % der Drehungen
     *   HOUSE    : RTP  95,00 %, Jackpot 1 : 11.775, Gewinn (>1x) in 18,7 % der Drehungen
     *   HARDCORE : RTP  88,00 %, Jackpot 1 : 18.610, Gewinn (>1x) in 17,0 % der Drehungen
     */
    //                                         Kohle Redst Lapis Eisen Gold Smar Creep Diam Stern
    private static final int[] SLOT_WEIGHTS_FAIR     = {23, 18, 16, 12, 9, 7, 5, 4, 5};
    private static final int[] SLOT_WEIGHTS_HOUSE    = {23, 14, 13, 12, 9, 7, 5, 4, 4};
    private static final int[] SLOT_WEIGHTS_HARDCORE = {27, 22, 16, 12, 9, 7, 5, 4, 4};

    // ------------------------------------------------------------------
    // Roulette – Auszahlungen (für alle Modi gleich, Modus bestimmt die Null-Fächer)
    // ------------------------------------------------------------------

    /** Rot/Schwarz, Gerade/Ungerade, 1–18/19–36: 2x. */
    public static final int ROULETTE_EVEN_MONEY_PAYOUT = 2;
    /** Dutzend (1–12, 13–24, 25–36): 3x. */
    public static final int ROULETTE_DOZEN_PAYOUT = 3;
    /** Einzelzahl (Plein): 36x. */
    public static final int ROULETTE_STRAIGHT_PAYOUT = 36;

    // ------------------------------------------------------------------
    // Münzwurf
    // ------------------------------------------------------------------

    /** Auszahlung bei richtig getippter Seite. */
    public static final int COIN_PAYOUT = 2;
    /** Maximale Länge einer "Doppelt oder nichts"-Serie (Schutz vor Überlauf). */
    public static final int COIN_MAX_STREAK = 20;

    // ------------------------------------------------------------------
    // Crash
    // ------------------------------------------------------------------

    /** Wachstum des Multiplikators: m(t) = e^(RATE · t[s]). 2x nach ~7,7 s, 10x nach ~25,6 s. */
    public static final double CRASH_GROWTH_PER_SECOND = 0.09;
    /** Obergrenze des Crash-Punkts. */
    public static final double CRASH_MAX_MULTIPLIER = 1000.0;

    // ------------------------------------------------------------------
    // Rubbellose – Multiplikatoren je Symbol (× Lospreis), Reihenfolge wie ScratchSymbol
    // ------------------------------------------------------------------

    /** Gewinn bei 3 gleichen Symbolen: Kohle, Eisen, Gold, Smaragd, Diamant, Netherstern. */
    public static final int[] SCRATCH_MULTIPLIERS = {1, 2, 5, 10, 50, 250};
    /*
     * Chance je Symbol, dass ein Los genau dieses Gewinnsymbol enthält
     * (höchstens ein Gewinn pro Los). RTP = Σ Chance × Multiplikator:
     *   FAIR 100,0 %, HOUSE 90,5 %, HARDCORE 80,0 %
     */
    private static final double[] SCRATCH_CHANCES_FAIR     = {0.20, 0.10, 0.040, 0.0150, 0.0030, 0.00040};
    private static final double[] SCRATCH_CHANCES_HOUSE    = {0.20, 0.09, 0.035, 0.0135, 0.0027, 0.00032};
    private static final double[] SCRATCH_CHANCES_HARDCORE = {0.18, 0.08, 0.030, 0.0120, 0.0024, 0.00028};

    // ------------------------------------------------------------------
    // Profile je Modus
    // ------------------------------------------------------------------

    /**
     * Alle vom Glücksfaktor abhängigen Parameter.
     *
     * @param coinWinChance          Chance, dass der Münzwurf die getippte Seite zeigt
     * @param rouletteVariant        Roulette ohne Null (fair), mit 0 (europäisch) oder 0 + 00 (amerikanisch)
     * @param crashHouseEdge         Hausvorteil beim Crash; Crash-Punkt = (1 − Vorteil) / U, U ∈ (0, 1]
     * @param blackjackDecks         Anzahl Kartendecks im Schlitten
     * @param blackjackPayout        Gewinnanteil bei Blackjack (1,5 = 3:2, 1,2 = 6:5)
     * @param dealerHitsSoft17       ob der Dealer bei Soft 17 noch zieht
     * @param slotWeights            Walzengewichte der Slot-Maschine
     * @param scratchChances         Gewinnchancen je Rubbellos-Symbol
     * @param plinkoHouseEdge        Abzug auf alle Plinko-Multiplikatoren
     * @param minesHouseEdge         Abzug auf den fairen Minenfeld-Multiplikator
     */
    public record Profile(
            LuckMode mode,
            double coinWinChance,
            RouletteVariant rouletteVariant,
            double crashHouseEdge,
            int blackjackDecks,
            double blackjackPayout,
            boolean dealerHitsSoft17,
            int[] slotWeights,
            double[] scratchChances,
            double plinkoHouseEdge,
            double minesHouseEdge) {
    }

    public static final Profile FAIR = new Profile(LuckMode.FAIR,
            0.50, RouletteVariant.NO_ZERO, 0.00,
            1, 1.5, false,
            SLOT_WEIGHTS_FAIR, SCRATCH_CHANCES_FAIR,
            0.00, 0.00);

    public static final Profile HOUSE = new Profile(LuckMode.HOUSE,
            0.48, RouletteVariant.SINGLE_ZERO, 0.03,
            6, 1.5, true,
            SLOT_WEIGHTS_HOUSE, SCRATCH_CHANCES_HOUSE,
            0.03, 0.03);

    public static final Profile HARDCORE = new Profile(LuckMode.HARDCORE,
            0.45, RouletteVariant.DOUBLE_ZERO, 0.08,
            8, 1.2, true,
            SLOT_WEIGHTS_HARDCORE, SCRATCH_CHANCES_HARDCORE,
            0.08, 0.08);

    public static Profile of(LuckMode mode) {
        return switch (mode) {
            case FAIR -> FAIR;
            case HOUSE -> HOUSE;
            case HARDCORE -> HARDCORE;
        };
    }

    /**
     * Ungefährer Hausvorteil von Blackjack mit Basisstrategie (ohne Split),
     * ermittelt per Simulation ({@code BlackjackTest}). Nur zur Anzeige.
     */
    public static double blackjackHouseEdgeEstimate(LuckMode mode) {
        // Gemessen: 1,5 Mio. Hände je Modus, Standardfehler ≈ 0,1 %
        return switch (mode) {
            case FAIR -> 0.004;   // 1 Deck, 3:2, Dealer steht auf Soft 17
            case HOUSE -> 0.0115; // 6 Decks, 3:2, Dealer zieht auf Soft 17
            case HARDCORE -> 0.0265; // 8 Decks, 6:5, Dealer zieht auf Soft 17
        };
    }

    /** Hausvorteil eines Spiels im gegebenen Modus (exakt bzw. für Blackjack geschätzt). */
    public static double houseEdge(GameType game, LuckMode mode) {
        Profile p = of(mode);
        return switch (game) {
            case SLOTS -> 1.0 - SlotMachine.returnToPlayer(p.slotWeights());
            case ROULETTE -> 1.0 - p.rouletteVariant().returnToPlayer();
            case COINFLIP -> 1.0 - COIN_PAYOUT * p.coinWinChance();
            case BLACKJACK -> blackjackHouseEdgeEstimate(mode);
            case CRASH -> p.crashHouseEdge();
            case SCRATCH -> 1.0 - ScratchTicket.returnToPlayer(p.scratchChances());
            case PLINKO -> 1.0 - Plinko.returnToPlayer(Plinko.Risk.MEDIUM, p.plinkoHouseEdge());
            case MINES -> p.minesHouseEdge();
        };
    }
}
