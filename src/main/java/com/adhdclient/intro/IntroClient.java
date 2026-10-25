package com.adhdclient.intro;

import com.adhdclient.gui.Theme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/**
 * Client-Intro: beim ersten Hauptmenü nach dem Start eine kurze Animation – Logo zoomt herein, Blitz blitzt,
 * Schriftzug „ADHD Client“ erscheint, dann blendet alles aus. Ein Klick überspringt es.
 */
public final class IntroClient {

    public static boolean enabled = true;
    public static final float LENGTH = 2.6f;
    private static final Identifier LOGO = Identifier.of("adhd_client", "icon.png");

    private static long start = -1;
    private static boolean done;

    private IntroClient() {
    }

    public static boolean running() {
        return enabled && !done;
    }

    public static void skip() {
        done = true;
    }

    /** Für das Menü: Intro noch einmal zeigen. */
    public static void replay() {
        done = false;
        start = -1;
    }

    /** Aus dem Mixin: über das Hauptmenü zeichnen. */
    public static void render(DrawContext ctx, int width, int height) {
        if (!running()) return;
        long now = System.currentTimeMillis();
        if (start < 0) start = now;
        float t = (now - start) / 1000f;
        if (t > LENGTH) {
            done = true;
            return;
        }
        float fadeOut = 1f - MathHelper.clamp((t - (LENGTH - 0.5f)) / 0.5f, 0f, 1f);
        int bg = (int) (255 * fadeOut) << 24;
        ctx.fill(0, 0, width, height, bg | 0x0B0614);
        // Logo: zoomt mit kleinem Überschwingen herein
        float z = t < 0.6f ? ease(t / 0.6f) : 1f;
        float scale = z * (1f + 0.15f * MathHelper.sin(MathHelper.clamp(t / 0.6f, 0, 1) * (float) Math.PI));
        int size = (int) (Math.min(width, height) * 0.32f * scale);
        int cx = width / 2, cy = height / 2 - 14;
        int alpha = (int) (255 * fadeOut * MathHelper.clamp(t / 0.25f, 0f, 1f));
        if (size > 0) {
            ctx.drawTexture(RenderPipelines.GUI_TEXTURED, LOGO, cx - size / 2, cy - size / 2, 0, 0, size, size, size, size, (alpha << 24) | 0xFFFFFF);
        }
        // Blitz: kurzes weißes Aufleuchten bei 0,6 s
        float flash = MathHelper.clamp(1f - Math.abs(t - 0.65f) / 0.12f, 0f, 1f);
        if (flash > 0) ctx.fill(0, 0, width, height, ((int) (150 * flash) << 24) | 0xFFFFFF);
        // Schriftzug
        float textIn = MathHelper.clamp((t - 0.7f) / 0.4f, 0f, 1f) * fadeOut;
        if (textIn > 0.02f) {
            var tr = MinecraftClient.getInstance().textRenderer;
            String title = "ADHD Client";
            int a = (int) (255 * textIn);
            ctx.getMatrices().pushMatrix();
            ctx.getMatrices().translate(cx, cy + size / 2f + 10);
            ctx.getMatrices().scale(2f, 2f);
            ctx.drawTextWithShadow(tr, title, -tr.getWidth(title) / 2, 0, (a << 24) | (Theme.accent() & 0xFFFFFF));
            ctx.getMatrices().popMatrix();
        }
    }

    private static float ease(float x) {
        x = MathHelper.clamp(x, 0f, 1f);
        return 1f - (1f - x) * (1f - x) * (1f - x);
    }
}
