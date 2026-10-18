package com.adhdclient.basefinder.scan;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/** Ein anzuzeigender Fund (fertig ausgewertet), wird vom Renderer jedes Bild gezeichnet. */
public final class Marker {

    public enum Mode {
        AMETHYST("Amethyst", "Amethyst"), BEES("Bienennester", "Bee nests"), SPAWNERS("Spawner", "Spawners"),
        KELP("Kelp", "Kelp"), ANOMALY("Anomalien", "Anomalies");

        private final String de;
        private final String en;

        Mode(String de, String en) {
            this.de = de;
            this.en = en;
        }

        public String title() {
            return com.adhdclient.basefinder.Lang.t(de, en);
        }
    }

    public final Mode mode;
    /** true = ganzer Chunk (großer Rahmen mit Füllung), false = einzelner Block. */
    public final boolean chunkFrame;
    public final Box box;
    public final boolean best;
    public final List<String> lines;
    /** An diese Zeile hängt der Renderer live die Entfernung an (-1 = keine). */
    public final int distanceLine;
    /** Zielpunkt für die Entfernungsangabe. */
    public final Vec3d target;
    /** Eindeutiger Schlüssel für Benachrichtigungen. */
    public final String key;
    /** Text für die Actionbar-Benachrichtigung bei einem neuen Fund. */
    public final String notifyText;
    /** Sortierwert zur Auswahl des besten Fundes in Sammelmeldungen. */
    public final double value;

    public Marker(Mode mode, boolean chunkFrame, Box box, boolean best, List<String> lines, int distanceLine,
                  Vec3d target, String key, String notifyText, double value) {
        this.mode = mode;
        this.chunkFrame = chunkFrame;
        this.box = box;
        this.best = best;
        this.lines = lines;
        this.distanceLine = distanceLine;
        this.target = target;
        this.key = key;
        this.notifyText = notifyText;
        this.value = value;
    }
}
