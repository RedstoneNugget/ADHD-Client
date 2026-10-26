package com.adhdclient.newchunks;

import com.adhdclient.extras.Extras;
import com.adhdclient.extras.OptionsScreen;
import com.adhdclient.gui.L;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.debug.gizmo.GizmoDrawing;

/**
 * Neue/alte Chunks: Wenn ein Chunk gerade erst erzeugt wird, fließen Wasser und Lava darin noch – der Server
 * schickt dann Block-Updates mit fließender Flüssigkeit. Solche Chunks gelten als „neu“ (noch nie besucht),
 * alle anderen geladenen als „alt“ (schon mal jemand da gewesen). Gezeichnet als flache Fläche auf Fußhöhe.
 */
public final class NewChunksClient {

    private static final LongSet NEW = new LongOpenHashSet();
    private static final LongSet SEEN = new LongOpenHashSet();

    private NewChunksClient() {
    }

    public static Extras.NewChunks cfg() {
        return Extras.get().newChunks;
    }

    public static void init() {
        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> SEEN.add(chunk.getPos().toLong()));
        ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> c.execute(NewChunksClient::clear));
        ClientPlayConnectionEvents.JOIN.register((h, s, c) -> c.execute(NewChunksClient::clear));
        WorldRenderEvents.END_EXTRACTION.register(NewChunksClient::render);
    }

    public static void clear() {
        NEW.clear();
        SEEN.clear();
    }

    /** Aus dem Mixin: ein Block-Update vom Server (auf dem Haupt-Thread). */
    public static void onBlockUpdate(BlockPos pos, BlockState state) {
        if (!cfg().enabled) return;
        FluidState fluid = state.getFluidState();
        if (!fluid.isEmpty() && !fluid.isStill()) NEW.add(ChunkPos.toLong(pos.getX() >> 4, pos.getZ() >> 4));
    }

    public static boolean isNew(int cx, int cz) {
        return NEW.contains(ChunkPos.toLong(cx, cz));
    }

    private static void render(WorldExtractionContext ctx) {
        Extras.NewChunks c = cfg();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!c.enabled || mc.world == null || mc.player == null || mc.worldRenderer == null) return;
        int pcx = mc.player.getBlockX() >> 4, pcz = mc.player.getBlockZ() >> 4;
        double y = Math.floor(mc.player.getY()) + 0.02;
        try (var scope = mc.worldRenderer.startDrawingGizmos()) {
            for (int cx = pcx - c.range; cx <= pcx + c.range; cx++) {
                for (int cz = pcz - c.range; cz <= pcz + c.range; cz++) {
                    long key = ChunkPos.toLong(cx, cz);
                    boolean isNew = NEW.contains(key);
                    if (isNew ? !c.showNew : !(c.showOld && SEEN.contains(key))) continue;
                    int color = isNew ? c.newColor : c.oldColor;
                    Box b = new Box(cx * 16 + 0.5, y, cz * 16 + 0.5, cx * 16 + 15.5, y + 0.04, cz * 16 + 15.5);
                    GizmoDrawing.box(b, DrawStyle.filledAndStroked(0xC0000000 | color, 1.5f, 0x28000000 | color));
                }
            }
        } catch (RuntimeException ignored) {
            // ein Bild ohne Markierungen ist kein Problem
        }
    }

    public static Screen screen(Screen parent) {
        Extras.NewChunks c = cfg();
        return OptionsScreen.builder(L.t("Neue Chunks", "New Chunks"), c)
                .toggle(L.t("Neue zeigen", "Show new"), L.t("Chunks, die gerade erst erzeugt wurden", "Chunks that were just generated"), () -> c.showNew, v -> c.showNew = v)
                .toggle(L.t("Alte zeigen", "Show old"), L.t("Chunks, in denen schon mal jemand war", "Chunks someone has been in before"), () -> c.showOld, v -> c.showOld = v)
                .color(L.t("Farbe neu", "New color"), () -> c.newColor, v -> c.newColor = v)
                .color(L.t("Farbe alt", "Old color"), () -> c.oldColor, v -> c.oldColor = v)
                .intSlider(L.t("Reichweite", "Range"), 1, 64, () -> c.range, v -> c.range = v, L.t(" Chunks", " chunks"))
                .action(L.t("Liste leeren", "Clear list"), null, NewChunksClient::clear)
                .hint(() -> L.t("Neu = fließendes Wasser/Lava beim Laden, sonst alt", "New = flowing water/lava when loading, else old"))
                .build(parent);
    }
}
