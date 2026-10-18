package com.adhdclient.casino.core.progress;

import com.adhdclient.casino.core.GameType;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Tägliche Missionen: Jeden (echten) Tag werden {@link #PER_DAY} Aufgaben aus einem Pool gezogen.
 * Die Auswahl hängt nur vom Datum ab – am selben Tag gibt es also immer dieselben Missionen.
 */
public final class Missions {

    public static final int PER_DAY = 3;

    public enum Kind {
        /** Runden spielen. */
        PLAY,
        /** Runden gewinnen. */
        WIN,
        /** Chips einsetzen. */
        WAGER,
        /** Reingewinn erzielen (nur Gewinne zählen). */
        PROFIT
    }

    /**
     * @param game Spiel, auf das sich die Mission bezieht (null = beliebig)
     */
    public record Def(String id, Kind kind, GameType game, long target, long reward) {

        public String translationKey() {
            String base = "luckycasino.mission." + kind.name().toLowerCase();
            return (kind == Kind.PLAY || kind == Kind.WIN) && game == null ? base + "_any" : base;
        }
    }

    public static final List<Def> POOL;

    static {
        List<Def> pool = new ArrayList<>();
        pool.add(new Def("play_any", Kind.PLAY, null, 25, 200));
        pool.add(new Def("win_any", Kind.WIN, null, 10, 250));
        pool.add(new Def("wager", Kind.WAGER, null, 2_000, 200));
        pool.add(new Def("profit", Kind.PROFIT, null, 1_000, 300));
        for (GameType g : GameType.values()) {
            pool.add(new Def("play_" + g.id(), Kind.PLAY, g, 10, 150));
            pool.add(new Def("win_" + g.id(), Kind.WIN, g, 3, 200));
        }
        POOL = Collections.unmodifiableList(pool);
    }

    private Missions() {
    }

    /** Die Missionen eines Tages (deterministisch, drei verschiedene). */
    public static List<Def> forDay(LocalDate day) {
        List<Def> copy = new ArrayList<>(POOL);
        Collections.shuffle(copy, new Random(day.toEpochDay() * 7_919L + 13));
        return new ArrayList<>(copy.subList(0, PER_DAY));
    }

    public static Def byId(String id) {
        for (Def d : POOL) {
            if (d.id().equals(id)) {
                return d;
            }
        }
        return null;
    }

    /** Fortschritt einer Mission durch eine abgerechnete Runde. */
    public static long progressFor(Def def, GameType game, long stake, long profit, boolean win) {
        if (def.game() != null && def.game() != game) {
            return 0;
        }
        return switch (def.kind()) {
            case PLAY -> 1;
            case WIN -> win ? 1 : 0;
            case WAGER -> stake;
            case PROFIT -> Math.max(0, profit);
        };
    }

    /** Gespeicherter Fortschritt einer Mission. */
    public static class Progress {
        public String id;
        public long progress;
        public boolean claimed;

        public Progress() {
        }

        public Progress(String id) {
            this.id = id;
        }
    }
}
