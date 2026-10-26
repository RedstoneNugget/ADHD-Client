package com.adhdclient.itemesp;

import com.adhdclient.extras.Draw;
import com.adhdclient.extras.Extras;
import com.adhdclient.extras.OptionsScreen;
import com.adhdclient.gui.L;
import com.adhdclient.render.Crosshair;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.debug.gizmo.GizmoDrawing;

/** Item ESP: gedroppte Items leuchten durch Wände, auf Wunsch mit Name und Anzahl. */
public final class ItemEspClient {

    private static final int MAX = 512;

    private ItemEspClient() {
    }

    public static Extras.ItemEsp cfg() {
        return Extras.get().itemEsp;
    }

    public static void init() {
        WorldRenderEvents.END_EXTRACTION.register(ItemEspClient::render);
    }

    private static void render(WorldExtractionContext ctx) {
        Extras.ItemEsp c = cfg();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!c.enabled || mc.world == null || mc.player == null || mc.worldRenderer == null) return;
        float tickDelta = mc.getRenderTickCounter().getTickProgress(true);
        try (var scope = mc.worldRenderer.startDrawingGizmos()) {
            Vec3d from = c.tracers ? Crosshair.point(ctx, ctx.camera()) : null;
            int drawn = 0;
            for (Entity e : mc.world.getEntities()) {
                if (!(e instanceof ItemEntity item) || e.distanceTo(mc.player) > c.range) continue;
                if (++drawn > MAX) break;
                Vec3d pos = e.getLerpedPos(tickDelta);
                Box box = e.getBoundingBox().offset(pos.subtract(e.getEntityPos())).expand(0.05);
                GizmoDrawing.box(box, DrawStyle.filledAndStroked(0xFF000000 | c.color, 1.5f, 0x30000000 | c.color)).ignoreOcclusion();
                if (from != null) GizmoDrawing.line(from, box.getCenter(), 0xC0000000 | c.color, 1.2f).ignoreOcclusion();
                if (c.names) {
                    ItemStack s = item.getStack();
                    String text = s.getName().getString() + (s.getCount() > 1 ? " ×" + s.getCount() : "");
                    Draw.label(text, new Vec3d(pos.x, box.maxY + 0.3, pos.z), c.color, ctx.camera(), 0.8f);
                }
            }
        } catch (RuntimeException ignored) {
            // ein Bild ohne Markierungen ist kein Problem
        }
    }

    public static Screen screen(Screen parent) {
        Extras.ItemEsp c = cfg();
        return OptionsScreen.builder("Item ESP", c)
                .toggle(L.t("Name + Anzahl", "Name + count"), null, () -> c.names, v -> c.names = v)
                .toggle("Tracer", L.t("Linien vom Fadenkreuz", "Lines from your crosshair"), () -> c.tracers, v -> c.tracers = v)
                .color(L.t("Farbe", "Color"), () -> c.color, v -> c.color = v)
                .intSlider(L.t("Reichweite", "Range"), 4, 512, () -> c.range, v -> c.range = v, " m")
                .hint(() -> L.t("Gedroppte Items leuchten durch Wände", "Dropped items glow through walls"))
                .build(parent);
    }
}
