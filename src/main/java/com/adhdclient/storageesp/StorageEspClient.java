package com.adhdclient.storageesp;

import com.adhdclient.extras.Extras;
import com.adhdclient.gui.L;
import com.adhdclient.extras.OptionsScreen;
import com.adhdclient.render.Crosshair;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.DispenserBlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.block.entity.TrappedChestBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.debug.gizmo.GizmoDrawing;

/** Storage ESP: Kisten, Shulker, Fässer und Ender-Kisten durch Wände, jede Art mit eigener Farbe. */
public final class StorageEspClient {

    private static final int MAX = 2048;

    private StorageEspClient() {
    }

    public static Extras.StorageEsp cfg() {
        return Extras.get().storageEsp;
    }

    public static void init() {
        WorldRenderEvents.END_EXTRACTION.register(StorageEspClient::render);
    }

    /** Farbe je Art, 0 = nicht anzeigen. */
    static int colorOf(BlockEntity be, Extras.StorageEsp c) {
        if (be instanceof TrappedChestBlockEntity) return c.chests ? 0xFF5040 : 0;
        if (be instanceof ChestBlockEntity) return c.chests ? 0xFFA030 : 0;
        if (be instanceof ShulkerBoxBlockEntity) return c.shulkers ? 0xD060FF : 0;
        if (be instanceof BarrelBlockEntity) return c.barrels ? 0xB07840 : 0;
        if (be instanceof EnderChestBlockEntity) return c.enderChests ? 0x30E0B0 : 0;
        if (be instanceof HopperBlockEntity || be instanceof DispenserBlockEntity || be instanceof AbstractFurnaceBlockEntity) {
            return c.others ? 0xA0A0A0 : 0;
        }
        return 0;
    }

    private static void render(WorldExtractionContext ctx) {
        Extras.StorageEsp c = cfg();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!c.enabled || mc.world == null || mc.player == null || mc.worldRenderer == null) return;
        BlockPos me = mc.player.getBlockPos();
        int chunks = Math.min(mc.options.getClampedViewDistance(), (c.range >> 4) + 1);
        double r2 = (double) c.range * c.range;
        try (var scope = mc.worldRenderer.startDrawingGizmos()) {
            Vec3d from = c.tracers ? Crosshair.point(ctx, ctx.camera()) : null;
            int drawn = 0;
            for (int cx = (me.getX() >> 4) - chunks; cx <= (me.getX() >> 4) + chunks; cx++) {
                for (int cz = (me.getZ() >> 4) - chunks; cz <= (me.getZ() >> 4) + chunks; cz++) {
                    WorldChunk chunk = mc.world.getChunkManager().getChunk(cx, cz, ChunkStatus.FULL, false);
                    if (chunk == null) continue;
                    for (BlockEntity be : chunk.getBlockEntities().values()) {
                        int color = colorOf(be, c);
                        if (color == 0 || be.getPos().getSquaredDistance(me) > r2) continue;
                        if (++drawn > MAX) return;
                        Box box = new Box(be.getPos()).contract(0.06);
                        DrawStyle style = c.fill ? DrawStyle.filledAndStroked(0xFF000000 | color, 2f, 0x30000000 | color)
                                : DrawStyle.stroked(0xFF000000 | color, 2f);
                        GizmoDrawing.box(box, style).ignoreOcclusion();
                        if (from != null) GizmoDrawing.line(from, box.getCenter(), 0xC0000000 | color, 1.2f).ignoreOcclusion();
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // ein Bild ohne Markierungen ist kein Problem
        }
    }

    public static Screen screen(Screen parent) {
        Extras.StorageEsp c = cfg();
        return OptionsScreen.builder("Storage ESP", c)
                .toggle(L.t("Kisten", "Chests"), null, () -> c.chests, v -> c.chests = v)
                .toggle("Shulker", null, () -> c.shulkers, v -> c.shulkers = v)
                .toggle(L.t("Fässer", "Barrels"), null, () -> c.barrels, v -> c.barrels = v)
                .toggle(L.t("Ender-Kisten", "Ender chests"), null, () -> c.enderChests, v -> c.enderChests = v)
                .toggle(L.t("Trichter, Öfen …", "Hoppers, furnaces …"), L.t("Auch Trichter, Werfer, Spender und Öfen", "Also hoppers, droppers, dispensers and furnaces"),
                        () -> c.others, v -> c.others = v)
                .toggle(L.t("Füllung", "Fill"), null, () -> c.fill, v -> c.fill = v)
                .toggle("Tracer", L.t("Linien vom Fadenkreuz", "Lines from your crosshair"), () -> c.tracers, v -> c.tracers = v)
                .intSlider(L.t("Reichweite", "Range"), 8, 1024, () -> c.range, v -> c.range = v, " m")
                .hint(() -> L.t("Jede Art hat ihre eigene Farbe", "Each type has its own color"))
                .build(parent);
    }
}
