package com.adhdclient.radar;

import com.adhdclient.extras.Extras;
import com.adhdclient.extras.OptionsScreen;
import com.adhdclient.gui.L;
import com.adhdclient.gui.Theme;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Spieler-Radar: kleines Fenster mit den nächsten Spielern, Entfernung und Richtung (Pfeil relativ zu deinem Blick). */
public final class RadarClient {

    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    private RadarClient() {
    }

    public static Extras.Radar cfg() {
        return Extras.get().radar;
    }

    public static void init() {
        HudElementRegistry.addLast(Identifier.of("adhd_client", "player_radar"), RadarClient::render);
    }

    /** Pfeil von dir zum Ziel, relativ zur Blickrichtung (↑ = geradeaus). */
    static String arrow(double dx, double dz, float yaw) {
        double angle = Math.toDegrees(Math.atan2(dz, dx)) - 90 - yaw;
        int i = Math.floorMod((int) Math.round(MathHelper.wrapDegrees(angle) / 45.0), 8);
        return ARROWS[i];
    }

    static List<String> lines(MinecraftClient mc, Extras.Radar c) {
        List<PlayerEntity> list = new ArrayList<>();
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p != mc.player && !p.isSpectator() && p.distanceTo(mc.player) <= c.range) list.add(p);
        }
        list.sort(Comparator.comparingDouble(p -> p.squaredDistanceTo(mc.player)));
        List<String> out = new ArrayList<>();
        for (PlayerEntity p : list.subList(0, Math.min(c.maxPlayers, list.size()))) {
            String s = arrow(p.getX() - mc.player.getX(), p.getZ() - mc.player.getZ(), mc.player.getYaw()) + " "
                    + p.getName().getString() + " " + Math.round(p.distanceTo(mc.player)) + "m";
            if (c.coords) s += " (" + p.getBlockX() + ", " + p.getBlockY() + ", " + p.getBlockZ() + ")";
            out.add(s);
        }
        return out;
    }

    private static void render(DrawContext ctx, RenderTickCounter tick) {
        Extras.Radar c = cfg();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!c.enabled || mc.world == null || mc.player == null || mc.options.hudHidden) return;
        List<String> lines = lines(mc, c);
        TextRenderer tr = mc.textRenderer;
        String title = L.t("Spieler in der Nähe", "Players nearby") + " (" + lines.size() + ")";
        int w = tr.getWidth(title);
        for (String s : lines) w = Math.max(w, tr.getWidth(s));
        w += 8;
        int h = 14 + Math.max(1, lines.size()) * 10;
        int x = c.right ? ctx.getScaledWindowWidth() - w - 4 : 4;
        int y = c.right ? 4 : 40;
        ctx.fill(x, y, x + w, y + h, 0x90000000);
        ctx.fill(x, y, x + w, y + 1, 0xFF000000 | Theme.accent());
        ctx.drawTextWithShadow(tr, title, x + 4, y + 3, 0xFF000000 | Theme.accent());
        if (lines.isEmpty()) {
            ctx.drawTextWithShadow(tr, L.t("niemand", "nobody"), x + 4, y + 13, 0xFFA0A0A0);
            return;
        }
        for (int i = 0; i < lines.size(); i++) ctx.drawTextWithShadow(tr, lines.get(i), x + 4, y + 13 + i * 10, 0xFFFFFFFF);
    }

    public static Screen screen(Screen parent) {
        Extras.Radar c = cfg();
        return OptionsScreen.builder(L.t("Spieler-Radar", "Player Radar"), c)
                .toggle(L.t("Rechts oben", "Top right"), L.t("Sonst links unter der Ecke", "Otherwise on the left"), () -> c.right, v -> c.right = v)
                .toggle(L.t("Koordinaten", "Coordinates"), null, () -> c.coords, v -> c.coords = v)
                .intSlider(L.t("Max. Spieler", "Max players"), 1, 100, () -> c.maxPlayers, v -> c.maxPlayers = v, "")
                .intSlider(L.t("Reichweite", "Range"), 8, 4096, () -> c.range, v -> c.range = v, " m")
                .hint(() -> L.t("Pfeil zeigt die Richtung relativ zu deinem Blick", "The arrow shows the direction relative to your view"))
                .build(parent);
    }
}
