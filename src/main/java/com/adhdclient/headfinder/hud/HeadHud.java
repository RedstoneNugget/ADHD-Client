package com.adhdclient.headfinder.hud;

import com.adhdclient.headfinder.HeadFinderClient;
import com.adhdclient.headfinder.config.ModConfig;
import com.adhdclient.headfinder.data.HeadEntry;
import com.adhdclient.headfinder.data.HeadStore;
import com.adhdclient.headfinder.scan.HeadScanner;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3x2fStack;

/**
 * Kleiner Zähler oben in der Bildschirmmitte:
 * "Köpfe: X gefunden / Y entdeckt" und darunter Pfeil + Entfernung zum Ziel bzw. nächsten offenen Kopf.
 */
public final class HeadHud implements HudElement {

    private static final int ARROW_W = 16;

    private HeadHud() {
    }

    public static void register() {
        HudElementRegistry.addLast(Identifier.of(HeadFinderClient.MOD_ID, "hud"), new HeadHud());
    }

    @Override
    public void render(DrawContext ctx, RenderTickCounter tickCounter) {
        if (!HeadFinderClient.enabled) return;
        ModConfig cfg = HeadFinderClient.config();
        MinecraftClient mc = MinecraftClient.getInstance();
        HeadStore store = HeadFinderClient.store();
        if (!cfg.hudEnabled || mc.player == null || mc.world == null || !store.isOpen() || mc.options.hudHidden) return;

        TextRenderer tr = mc.textRenderer;
        String line1 = "Köpfe: " + store.countFound() + " gefunden / " + store.countDiscovered() + " entdeckt";

        Vec3d eye = mc.player.getEyePos();
        String dim = HeadScanner.dimension(mc.world);
        HeadEntry target = store.currentTarget(dim, eye);
        boolean explicit = target != null && target.key().equals(store.targetKey());
        String line2;
        float rel = 0;
        boolean arrow = false;
        if (target == null || target.found) {
            line2 = store.countDiscovered() == 0 ? "Noch keine Köpfe entdeckt" : "Alle entdeckten Köpfe gefunden";
        } else {
            Vec3d c = target.center();
            double dx = c.x - eye.x;
            double dz = c.z - eye.z;
            int dy = (int) Math.round(c.y - eye.y);
            // Minecraft-Yaw: 0 = Süden (+Z), steigt im Uhrzeigersinn; Zielrichtung im selben System
            float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            rel = MathHelper.wrapDegrees(targetYaw - mc.player.getYaw());
            arrow = true;
            String height = dy > 1 ? "  ▲" + dy : (dy < -1 ? "  ▼" + (-dy) : "");
            line2 = (explicit ? "Ziel: " : "Nächster: ") + Math.round(c.distanceTo(eye)) + " m" + height;
        }

        int w1 = tr.getWidth(line1);
        int w2 = tr.getWidth(line2) + (arrow ? ARROW_W + 4 : 0);
        int w = Math.max(w1, w2) + 10;
        int x = ctx.getScaledWindowWidth() / 2 - w / 2;
        int y = cfg.hudY;
        int h = 26;
        ctx.fill(x, y, x + w, y + h, 0x90000000);
        ctx.drawCenteredTextWithShadow(tr, line1, x + w / 2, y + 3, 0xFFFFFFFF);

        int open = ModConfig.rgb(explicit ? cfg.colorTarget : cfg.colorOpen, 0xFF3B3B);
        int lineX = x + w / 2 - w2 / 2;
        if (arrow) {
            drawArrow(ctx, lineX + ARROW_W / 2, y + 18, rel, 0xFF000000 | open);
            ctx.drawTextWithShadow(tr, line2, lineX + ARROW_W + 4, y + 14, 0xFFFFFFFF);
        } else {
            ctx.drawTextWithShadow(tr, line2, lineX, y + 14, 0xFFAAFFAA);
        }
    }

    /** Pfeil aus Rechtecken, um {@code degrees} gedreht (0 = geradeaus/oben, positiv = rechts). */
    private static void drawArrow(DrawContext ctx, int cx, int cy, float degrees, int color) {
        Matrix3x2fStack m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(cx, cy);
        m.rotate((float) Math.toRadians(degrees));
        ctx.fill(-1, -2, 1, 6, color);          // Schaft
        for (int r = 0; r < 5; r++) {           // Spitze (Dreieck)
            int yy = -7 + r;
            ctx.fill(-r - 1, yy, r + 1, yy + 1, color);
        }
        m.popMatrix();
    }
}
