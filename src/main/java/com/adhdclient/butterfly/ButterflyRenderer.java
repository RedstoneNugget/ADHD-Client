package com.adhdclient.butterfly;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.state.ArmedEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;

/**
 * Zeichnet das Butterfly-Messer (CS2-Stil) statt des Items – in der ersten Person mit allen Animationen und in der
 * dritten Person in der Hand. Die Teile sind echte 3D-Netze (knife.bin): Klinge mit
 * Keilschliff zur Schneide, Griffe mit gerundeten Kanten, Löchern und vertiefter Einlage. Gelenk-
 * Drehungen werden in die Matrix gerechnet, damit die Teile auch beim verzögerten Zeichnen richtig stehen.
 */
public final class ButterflyRenderer {

    private static final float PI = (float) Math.PI;
    private static final int WHITE = 0xFFFFFFFF;
    private static final float SCALE = 0.5f;
    static final Identifier MESH = Identifier.of("adhd_client", "butterfly/knife.bin");
    /** Texturen je Material: Klinge, Griff, Bolzen, Einlage, Kunststoff. */
    private static final Identifier[] TEXTURES = {tex("blade"), tex("handle"), tex("pin"), tex("inlay"), tex("plastic")};
    /** Teilnetze: Teil (0 Klinge, 1 Griff A, 2 Griff B), Material, Ecken (x, y, z, u, v, nx, ny, nz). */
    private static int[] subPart, subMaterial;
    private static float[][] subVerts;
    private static float[] pinA, pinB;
    /** Nachdrehen von Griff A im geschlossenen Zustand, damit beide Griffenden zusammentreffen (aus dem Modell). */
    private static float closeFix;
    private static boolean loaded, loadFailed;

    private static Identifier tex(String name) {
        return Identifier.of("adhd_client", "textures/butterfly/fade/" + name + ".png");
    }

    private static float lastSwing;
    /** Nur für Testbilder: Messer groß in der Bildmitte zeigen, um showcaseTurn Grad gedreht. */
    public static boolean showcase;
    public static float showcaseTurn;
    /** Grundhaltung (Grad); öffentlich, damit Testbilder verschiedene Haltungen vergleichen können. */
    public static float baseYaw = -24f, basePitch = -5f, baseRoll = 56f;
    public static float baseX = 0.34f, baseY = -0.33f, baseZ = -0.66f;
    /** Griffmitte von Griff B in Modellpixeln: hier hält die Hand das Messer. */
    private static final float GRIP_Y = -6.5f;

    /** Knochen-Namen, wie sie in der Blockbench-Datei heißen. */
    static final String HAND = "hand", BLADE = "blade", HANDLE_A = "handle_a", HANDLE_B = "handle_b";

    /** Haltung des Messers: Gelenkwinkel (Bogenmaß) und Bewegung der ganzen Hand. */
    static final class Pose {
        float blade;
        float handleA;
        float handleB;
        float tx;
        float ty;
        float tz;
        float rotX;
        float rotY;
        float rotZ;
        /** Handgelenk: Drehung um die Längsachse des Messers (Grad). */
        float twist;
        /** Drehung des ganzen Messers ums Gelenk in der Hand (Helikopter, Bogenmaß). */
        float spin;
    }

    private ButterflyRenderer() {
    }

    // ------------------------------------------------------------ erste Person

    /** true = gezeichnet, das normale Item nicht mehr zeichnen. */
    public static boolean render(AbstractClientPlayerEntity player, Hand hand, float swingProgress, ItemStack item, float equipProgress,
                                 MatrixStack matrices, OrderedRenderCommandQueue queue, int light) {
        ButterflyLook look = ButterflyClient.lookFor(item);
        if (look == null) return false;
        if (player.isUsingItem() && player.getActiveHand() == hand) return false;

        boolean main = hand == Hand.MAIN_HAND;
        Arm arm = main ? player.getMainArm() : player.getMainArm().getOpposite();
        int s = arm == Arm.RIGHT ? 1 : -1;
        if (main) {
            if (swingProgress > 0 && lastSwing == 0) ButterflyClient.onSwingStart();
            lastSwing = swingProgress;
        }
        // Eigene Animationen aus Blockbench haben Vorrang vor den eingebauten
        float t = ButterflyClient.animTime();
        ButterflyAnimations.Clip clip = main ? ButterflyAnimations.forCurrent() : null;
        if (clip != null && t > clip.length()) ButterflyClient.finish();
        Pose p = clip != null ? new Pose() : pose(main);
        if (showcase) {
            // Testbild: Messer groß in der Bildmitte, flache Seite zur Kamera
            matrices.push();
            matrices.translate(0.02f, 0.03f, -0.62f);
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-43f));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(showcaseTurn));
            matrices.translate(0f, -1.5f / 16f * 0.3f, 0f);
            matrices.scale(0.3f, 0.3f, 0.3f);
            drawKnife(matrices, queue, light, new Pose(), null, 0f);
            matrices.pop();
            return true;
        }

        matrices.push();
        view(matrices, s, p, equipProgress, main);
        if (clip != null) bone(matrices, clip, HAND, grip(), t);
        drawKnife(matrices, queue, light, p, clip, t);
        matrices.pop();
        return true;
    }

    /**
     * Haltung wie in CS2: Hand rechts unten, Klinge schräg nach links oben zur Bildmitte, flache Seite sichtbar.
     * Gedreht wird um die Hand (Griffmitte), nicht ums Gelenk – so bewegt sich das Messer wie aus dem Handgelenk.
     * Den Schwert-Schwung von Minecraft ersetzt die eigene Schnitt-Animation.
     */
    private static void view(MatrixStack matrices, int s, Pose p, float equipProgress, boolean breathe) {
        float now = (System.currentTimeMillis() % 100000L) / 1000f;
        float b = breathe ? 1f : 0f;                                      // leichtes Atmen, damit es nie starr wirkt
        matrices.translate(s * (baseX + p.tx), baseY + p.ty + 0.006f * b * MathHelper.sin(now * 1.9f) + equipProgress * -0.25f, baseZ + p.tz);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(s * (baseYaw + p.rotY)));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(basePitch + p.rotX + 1.2f * b * MathHelper.sin(now * 1.3f)));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(s * (baseRoll + p.rotZ)));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f + p.twist)); // Schneide nach unten links, Handgelenk
        matrices.scale(SCALE, SCALE, SCALE);
        matrices.translate(0f, -GRIP_Y / 16f, 0f);                        // Griffmitte in die Hand
        matrices.multiply(RotationAxis.POSITIVE_Z.rotation(p.spin));
    }

    /** Matrix der Hand für eine Haltung (ohne Atmen und Ziehen) – für die Blockbench-Vorlage. */
    static Matrix4f viewMatrix(Pose p) {
        MatrixStack m = new MatrixStack();
        view(m, 1, p, 0f, false);
        return new Matrix4f(m.peek().getPositionMatrix());
    }

    /** Ein Knochen aus Blockbench: verschieben, dann um seinen Drehpunkt drehen (Reihenfolge Z, Y, X wie in Blockbench). */
    private static void bone(MatrixStack m, ButterflyAnimations.Clip clip, String name, float[] pivot, float t) {
        float[] v = clip.sample(name, t);
        m.translate((pivot[0] + v[0]) / 16f, (pivot[1] + v[1]) / 16f, (pivot[2] + v[2]) / 16f);
        m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(v[5]));
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(v[4]));
        m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(v[3]));
        m.translate(-pivot[0] / 16f, -pivot[1] / 16f, -pivot[2] / 16f);
    }

    static float[] grip() {
        return new float[]{0f, GRIP_Y, 0f};
    }

    // ------------------------------------------------------------ dritte Person

    /** Ist das der eigene Spieler? (Nur bei dir wird das Messer gezeigt.) */
    public static boolean isOwnPlayer(ArmedEntityRenderState state) {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.player != null && state instanceof PlayerEntityRenderState player && player.id == mc.player.getId();
    }

    /**
     * Messer in der Hand der Spielerfigur (F5). Die Matrix steht schon an der Hand (setArmAngle und die
     * Drehungen von HeldItemFeatureRenderer); hier nur noch Griff in die Faust, Klinge nach vorn.
     */
    public static void renderThirdPerson(ArmedEntityRenderState state, Arm arm, MatrixStack matrices, OrderedRenderCommandQueue queue, int light) {
        boolean left = arm == Arm.LEFT;
        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90f));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f));
        matrices.translate((left ? -1 : 1) / 16f, 0.125f, -0.625f);
        // Griff liegt in der Hand, Klinge zeigt schräg nach vorn oben
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(40f));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(left ? -90f : 90f));
        matrices.scale(0.5f, 0.5f, 0.5f);
        matrices.translate(0f, -GRIP_Y / 16f, 0f); // Griffmitte in die Faust
        boolean main = arm == state.mainArm;
        ButterflyAnimations.Clip clip = main ? ButterflyAnimations.forCurrent() : null;
        Pose p = clip != null || !main ? new Pose() : pose(true);
        p.tx = p.ty = p.tz = 0;
        drawKnife(matrices, queue, light, p, clip, ButterflyClient.animTime());
        matrices.pop();
    }

    // ------------------------------------------------------------ Zeichnen

    private static void drawKnife(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, Pose p,
                                  ButterflyAnimations.Clip clip, float t) {
        if (!loadMesh()) return;
        for (int i = 0; i < subVerts.length; i++) {
            int part = subPart[i], mat = subMaterial[i];
            float[] verts = subVerts[i];
            matrices.push();
            // Wie ein echtes Butterfly: die Hand hält Griff B, die Klinge dreht sich um dessen Bolzen,
            // Griff A hängt an der Klinge (um seinen eigenen Bolzen) und schwingt mit ihr.
            if (clip != null) {
                if (part == 2) {
                    bone(matrices, clip, HANDLE_B, pinB, t);
                } else {
                    bone(matrices, clip, BLADE, pinB, t);
                    if (part == 1) bone(matrices, clip, HANDLE_A, pinA, t);
                }
            } else if (part == 2) {
                turn(matrices, pinB, p.handleB);
            } else {
                turn(matrices, pinB, p.blade);
                if (part == 1) turn(matrices, pinA, p.handleA + closed(p.blade) * closeFix - p.blade);
            }
            queue.submitCustom(matrices, RenderLayers.entityCutoutNoCull(TEXTURES[mat]), (e, vc) -> {
                for (int o = 0; o < verts.length; o += 8) {
                    vertex(e, vc, verts[o], verts[o + 1], verts[o + 2], verts[o + 3], verts[o + 4], WHITE, light, verts[o + 5], verts[o + 6], verts[o + 7]);
                }
            });
            matrices.pop();
        }
    }

    /** 0 = offen, 1 = geschlossen (Klinge um eine halbe Drehung umgeklappt). */
    static float closed(float blade) {
        return (1f - MathHelper.cos(blade)) / 2f;
    }

    /**
     * Beim Schließen wandert der Bolzen von Griff A auf die andere Seite von Griff B. Damit Griff A dann parallel
     * neben Griff B liegt (statt ihn zu kreuzen), dreht er um diesen Winkel nach: auf dieselbe Richtung wie Griff B.
     */
    private static float computeCloseFix() {
        float[] endA = lowest(1), endB = lowest(2);
        if (endA == null || endB == null) return 0f;
        double before = Math.atan2(endA[1] - pinA[1], endA[0] - pinA[0]);
        double after = Math.atan2(endB[1] - pinB[1], endB[0] - pinB[0]);
        double d = after - before;
        while (d > Math.PI) d -= 2 * Math.PI;
        while (d < -Math.PI) d += 2 * Math.PI;
        return (float) d;
    }

    /** Tiefster Punkt (Griffende) eines Teils. */
    private static float[] lowest(int part) {
        float[] best = null;
        for (int i = 0; i < subVerts.length; i++) {
            if (subPart[i] != part) continue;
            float[] v = subVerts[i];
            for (int o = 0; o < v.length; o += 8) if (best == null || v[o + 1] < best[1]) best = new float[]{v[o], v[o + 1]};
        }
        return best;
    }

    /** Um einen Bolzen (in Modellpixeln) in der Messerebene drehen. */
    private static void turn(MatrixStack matrices, float[] c, float angle) {
        matrices.translate(c[0] / 16f, c[1] / 16f, c[2] / 16f);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotation(angle));
        matrices.translate(-c[0] / 16f, -c[1] / 16f, -c[2] / 16f);
    }

    // ------------------------------------------------------------ für die Blockbench-Vorlage

    static boolean ensureMesh() {
        return loadMesh();
    }

    static int parts() {
        return subVerts.length;
    }

    static int partOf(int i) {
        return subPart[i];
    }

    static int materialOf(int i) {
        return subMaterial[i];
    }

    static float[] vertsOf(int i) {
        return subVerts[i];
    }

    static float[] pinA() {
        return pinA.clone();
    }

    static float[] pinB() {
        return pinB.clone();
    }

    static float closeFix() {
        return closeFix;
    }

    static Identifier texture(int material) {
        return TEXTURES[material];
    }

    static int textureCount() {
        return TEXTURES.length;
    }

    /** Modell aus knife.bin laden: Bolzen A und B, dann die Teilnetze. */
    private static boolean loadMesh() {
        if (loaded) return true;
        if (loadFailed) return false;
        try (java.io.DataInputStream in = new java.io.DataInputStream(new java.util.zip.GZIPInputStream(
                MinecraftClient.getInstance().getResourceManager().getResourceOrThrow(MESH).getInputStream()))) {
            pinA = new float[]{in.readFloat(), in.readFloat(), in.readFloat()};
            pinB = new float[]{in.readFloat(), in.readFloat(), in.readFloat()};
            int n = in.readInt();
            subPart = new int[n];
            subMaterial = new int[n];
            subVerts = new float[n][];
            for (int i = 0; i < n; i++) {
                subPart[i] = in.readByte();
                subMaterial[i] = in.readByte();
                float[] v = new float[in.readInt() * 8];
                for (int k = 0; k < v.length; k++) v[k] = in.readFloat();
                subVerts[i] = v;
            }
            closeFix = computeCloseFix();
            loaded = true;
        } catch (Exception ex) {
            ButterflyClient.LOGGER.error("Butterfly-Modell konnte nicht geladen werden", ex);
            loadFailed = true;
        }
        return loaded;
    }

    private static void vertex(MatrixStack.Entry e, VertexConsumer vc, float x, float y, float z, float u, float v, int color, int light,
                               float nx, float ny, float nz) {
        vc.vertex(e, x / 16f, y / 16f, z / 16f).color(color).texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(e, nx, ny, nz);
    }

    // ------------------------------------------------------------ Animationen

    private static float ease(float t) {
        t = MathHelper.clamp(t, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    private static float phase(float t, float from, float to) {
        return ease((t - from) / (to - from));
    }

    /**
     * Wert entlang von Stützstellen (Zeit, Wert) als glatte Kurve (kubisch Die Kurve schießt nie über:
     * wo der Wert hält oder umkehrt, steht sie kurz still.
     */
    private static float keys(float t, float... tv) {
        int n = tv.length / 2;
        if (t <= tv[0]) return tv[1];
        if (t >= tv[(n - 1) * 2]) return tv[(n - 1) * 2 + 1];
        int i = 0;
        while (t > tv[(i + 1) * 2]) i++;
        float t0 = tv[i * 2], v0 = tv[i * 2 + 1], t1 = tv[i * 2 + 2], v1 = tv[i * 2 + 3];
        float m0 = slope(tv, i, n), m1 = slope(tv, i + 1, n);
        float h = t1 - t0, u = (t - t0) / h, u2 = u * u, u3 = u2 * u;
        return (2 * u3 - 3 * u2 + 1) * v0 + (u3 - 2 * u2 + u) * h * m0 + (-2 * u3 + 3 * u2) * v1 + (u3 - u2) * h * m1;
    }

    /** Steigung an Stützstelle i (monoton, ohne Überschwingen). */
    private static float slope(float[] tv, int i, int n) {
        if (i <= 0 || i >= n - 1) return 0f;
        float d0 = (tv[i * 2 + 1] - tv[i * 2 - 1]) / (tv[i * 2] - tv[i * 2 - 2]);
        float d1 = (tv[i * 2 + 3] - tv[i * 2 + 1]) / (tv[i * 2 + 2] - tv[i * 2]);
        if (d0 * d1 <= 0f) return 0f;
        return 2f / (1f / d0 + 1f / d1);
    }

    private static Pose pose(boolean main) {
        Pose p = new Pose();
        if (!main) return p;
        float t = ButterflyClient.animTime();
        ButterflyClient.Anim anim = ButterflyClient.anim();
        builtIn(anim, p, t, ButterflyClient.slashDirection());
        if (anim != ButterflyClient.Anim.NONE && t > duration(anim)) ButterflyClient.finish();
        return p;
    }

    /** Eingebaute Animation zum Zeitpunkt t in die Haltung schreiben (dir: Schlagrichtung +1/-1). */
    static void builtIn(ButterflyClient.Anim anim, Pose p, float t, int dir) {
        switch (anim) {
            case EQUIP -> equip(p, t);
            case ATTACK -> slash(p, t, dir);
            case INSPECT -> inspect(p, t);
            case IDLE -> idle(p, t);
            default -> {
            }
        }
    }

    /** Länge der eingebauten Animationen in Sekunden. */
    static float duration(ButterflyClient.Anim anim) {
        return switch (anim) {
            case EQUIP -> 1.0f;
            case ATTACK -> 0.5f;
            case INSPECT -> ButterflyClient.INSPECT_SECONDS;
            case IDLE -> 1.2f;
            default -> 0f;
        };
    }

    /**
     * Ziehen: geschlossen von unten herein, das Handgelenk kippt, Griff A schwingt einmal ganz herum und die
     * Klinge klappt auf; die Hand federt aus.
     */
    private static void equip(Pose p, float t) {
        p.ty = keys(t, 0f, -0.32f, 0.28f, 0.03f, 0.45f, -0.01f, 0.6f, 0f);
        p.rotX = keys(t, 0f, -55f, 0.3f, 8f, 0.5f, -2f, 0.65f, 0f);
        p.rotZ = keys(t, 0f, 30f, 0.35f, -8f, 0.6f, 3f, 0.85f, 0f);
        p.twist = keys(t, 0f, 0f, 0.3f, -35f, 0.55f, 20f, 0.8f, 0f);
        // Klinge und Griff A klappen im Gleichtakt zur selben Seite auf – so streift nichts aneinander
        p.blade = keys(t, 0f, -PI, 0.15f, -PI, 0.65f, 0f);
        p.handleA = keys(t, 0f, -2f * PI, 0.15f, -2f * PI, 0.65f, 0f);
    }

    /**
     * Schnitt wie in CS2: kurz ausholen, dann ein schneller Zug quer durchs Bild (abwechselnd von rechts und von
     * links), das Handgelenk dreht die Schneide nach vorn, Griff A schwingt nach.
     */
    private static void slash(Pose p, float t, int dir) {
        p.tx = keys(t, 0f, 0f, 0.07f, 0.12f * dir, 0.2f, -0.38f * dir, 0.32f, -0.3f * dir, 0.5f, 0f);
        p.ty = keys(t, 0f, 0f, 0.07f, 0.06f, 0.2f, 0f, 0.5f, 0f);
        p.tz = keys(t, 0f, 0f, 0.07f, 0.04f, 0.2f, -0.16f, 0.5f, 0f);
        p.rotZ = keys(t, 0f, 0f, 0.07f, 25f * dir, 0.2f, -55f * dir, 0.35f, -40f * dir, 0.5f, 0f);
        p.rotY = keys(t, 0f, 0f, 0.07f, 12f * dir, 0.2f, -40f * dir, 0.5f, 0f);
        p.twist = keys(t, 0f, 0f, 0.07f, -20f * dir, 0.2f, 35f * dir, 0.5f, 0f);
        // Griff A schwingt nach – nur zur freien Seite, nie in Griff B hinein
        p.handleA = keys(t, 0f, 0f, 0.15f, dir > 0 ? -0.6f : -0.25f, 0.3f, dir > 0 ? -0.2f : -0.6f, 0.5f, 0f);
    }

    /**
     * Inspect: zur Mitte holen, zuklappen und wieder aufklappen (Griff A schwingt dabei ganz herum, die Klinge klappt
     * nur hin und zurück – wie bei einem echten Butterfly), das Ganze noch einmal mit der Klinge zur anderen Seite, zurück.
     */
    private static void inspect(Pose p, float t) {
        float in = phase(t, 0f, 0.35f) * (1f - phase(t, 3.2f, 3.6f));
        p.tx = -0.24f * in;
        p.ty = 0.1f * in;
        p.tz = 0.05f * in;
        p.rotY = 40f * in;
        p.rotX = 8f * in;
        p.rotZ = -12f * in;
        // Klinge klappt nur hin und zurück; Griff A schwingt im Gleichtakt einmal ganz herum und wieder zurück.
        // Beide zur selben Seite – das ist die einzige Richtung, in der nichts aneinander streift (nachgerechnet).
        p.blade = keys(t, 0f, 0f, 0.5f, 0f, 0.85f, -PI, 1.2f, 0f, 1.7f, 0f, 2.05f, -PI, 2.4f, 0f, 3.6f, 0f);
        p.handleA = keys(t, 0f, 0f, 0.5f, 0f, 0.85f, -2f * PI, 1.2f, 0f, 1.7f, 0f, 2.05f, -2f * PI, 2.4f, 0f, 3.6f, 0f);
        p.twist = keys(t, 0f, 0f, 0.4f, -20f, 0.85f, 25f, 1.2f, -10f, 1.5f, 0f, 2.05f, -25f, 2.4f, 10f, 2.8f, 0f, 3.6f, 0f);
        // zum Schluss die Klinge noch einmal ins Licht drehen (2,6–3,2 s)
        p.rotY += keys(t, 0f, 0f, 2.6f, 0f, 2.9f, 25f, 3.2f, 0f);
    }

    /** Spielen im Stand: Griff A schwingt zum halben Flip auf und schnappt zurück, das Handgelenk dreht mit. */
    private static void idle(Pose p, float t) {
        p.handleA = keys(t, 0f, 0f, 0.35f, -0.9f * PI, 0.7f, -0.1f, 0.95f, -0.03f, 1.2f, 0f);
        p.blade = keys(t, 0f, 0f, 0.35f, 0.12f, 1.2f, 0f);
        p.twist = keys(t, 0f, 0f, 0.35f, -15f, 0.8f, 6f, 1.2f, 0f);
        p.rotZ = keys(t, 0f, 0f, 0.35f, -5f, 0.8f, 2f, 1.2f, 0f);
        p.ty = keys(t, 0f, 0f, 0.35f, 0.015f, 1.2f, 0f);
    }
}
