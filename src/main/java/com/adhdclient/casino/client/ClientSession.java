package com.adhdclient.casino.client;

import com.adhdclient.casino.core.Casino;
import com.adhdclient.casino.core.blackjack.BlackjackGame;
import com.adhdclient.casino.core.coinflip.CoinFlipSeries;
import com.adhdclient.casino.core.coinflip.CoinSide;
import com.adhdclient.casino.core.crash.CrashRound;
import com.adhdclient.casino.core.mines.MinesGame;
import com.adhdclient.casino.core.roulette.RouletteBet;
import com.adhdclient.casino.core.scratch.ScratchTicket;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Flüchtiger Sitzungszustand (nicht gespeichert): laufende Runden und Verläufe,
 * damit man ein Fenster schließen und später weiterspielen kann.
 */
public final class ClientSession {

    public static BlackjackGame blackjack;
    public static CoinFlipSeries coinSeries;
    public static CoinSide coinChoice = CoinSide.HEADS;
    public static final Deque<CoinSide> coinHistory = new ArrayDeque<>();
    public static CrashRound crash;
    public static final Deque<Double> crashHistory = new ArrayDeque<>();
    public static final Deque<Integer> rouletteHistory = new ArrayDeque<>();
    public static Map<RouletteBet, Long> lastRouletteBets = new LinkedHashMap<>();
    public static ScratchTicket scratchTicket;
    /** Freigerubbelte Zellen je Feld (Feld × Zelle). */
    public static boolean[][] scratchCells;
    public static boolean[] scratchRevealed;
    public static MinesGame mines;

    private ClientSession() {
    }

    public static <T> void remember(Deque<T> history, T value, int limit) {
        history.addFirst(value);
        while (history.size() > limit) {
            history.removeLast();
        }
    }

    /** Beim Beenden des Spiels: offene Runden fair abschließen. */
    public static void finishAll(Casino casino) {
        if (blackjack != null && !blackjack.isFinished()) {
            casino.blackjackStand(blackjack);
        }
        if (crash != null && crash.isRunning()) {
            casino.cashOutCrash(crash, net.minecraft.util.Util.getMeasuringTimeMs());
        }
        if (coinSeries != null && coinSeries.canCashOut()) {
            casino.cashOutCoin(coinSeries);
        }
        if (mines != null && mines.canCashOut()) {
            casino.cashOutMines(mines);
        }
    }

    /** Nach einem Zurücksetzen des Spielstands. */
    public static void clear() {
        blackjack = null;
        coinSeries = null;
        coinHistory.clear();
        crash = null;
        crashHistory.clear();
        rouletteHistory.clear();
        lastRouletteBets = new LinkedHashMap<>();
        scratchTicket = null;
        scratchCells = null;
        scratchRevealed = null;
        mines = null;
    }
}
