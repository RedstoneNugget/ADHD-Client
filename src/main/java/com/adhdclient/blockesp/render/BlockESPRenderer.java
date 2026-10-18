package com.adhdclient.blockesp.render;

import com.adhdclient.blockesp.BlockESPClient;
import com.adhdclient.blockesp.BlockESPConfig;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.DrawStyle;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.debug.gizmo.GizmoDrawing;
import com.adhdclient.render.Crosshair;

/**
 * Zeichnet um jeden gefundenen Block eine Box (Farbe = Kartenfarbe des Blocks) und optional eine Linie
 * vom Fadenkreuz – über das Gizmo-System von 1.21.11, mit ignoreOcclusion() durch Wände sichtbar.
 */
public final class BlockESPRenderer {

    private static final int MAX_BOXES = 4096;
    private static final int MAX_TRACERS = 256;
    private static final int FALLBACK_COLOR = 0xFF3B3B;

    private BlockESPRenderer() {
    }

    public static void register() {
        WorldRenderEvents.END_EXTRACTION.register(BlockESPRenderer::render);
    }

    private static void render(WorldExtractionContext ctx) {
        if (!BlockESPClient.enabled) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.worldRenderer == null) return;
        var found = BlockESPClient.scanner().found();
        if (found.isEmpty()) return;

        BlockESPConfig cfg = BlockESPClient.config();
        Camera camera = ctx.camera();
        Vec3d cam = camera.getCameraPos();
        double range = cfg.range;
        double rangeSq = range * range;
        int camCx = ((int) Math.floor(cam.x)) >> 4;
        int camCz = ((int) Math.floor(cam.z)) >> 4;
        int chunkRange = (int) Math.ceil(range / 16.0) + 1;

        try (var scope = mc.worldRenderer.startDrawingGizmos()) {
            Vec3d from = cfg.tracers ? Crosshair.point(ctx, camera) : null;
            int boxes = 0;
            int tracers = 0;
            BlockPos.Mutable pos = new BlockPos.Mutable();
            for (Long2ObjectMap.Entry<LongArrayList> e : found.long2ObjectEntrySet()) {
                long key = e.getLongKey();
                if (Math.abs(ChunkPos.getPackedX(key) - camCx) > chunkRange || Math.abs(ChunkPos.getPackedZ(key) - camCz) > chunkRange) continue;
                LongArrayList list = e.getValue();
                for (int i = 0; i < list.size() && boxes < MAX_BOXES; i++) {
                    pos.set(list.getLong(i));
                    double dx = pos.getX() + 0.5 - cam.x;
                    double dy = pos.getY() + 0.5 - cam.y;
                    double dz = pos.getZ() + 0.5 - cam.z;
                    if (dx * dx + dy * dy + dz * dz > rangeSq) continue;

                    BlockState state = mc.world.getBlockState(pos);
                    int color = state.getBlock().getDefaultMapColor().color;
                    if (color == 0) color = FALLBACK_COLOR;
                    Box box = shape(state, pos).expand(0.02);
                    DrawStyle style = cfg.fill
                            ? DrawStyle.filledAndStroked(0xFF000000 | color, 2.0f, 0x40000000 | color)
                            : DrawStyle.stroked(0xFF000000 | color, 2.0f);
                    GizmoDrawing.box(box, style).ignoreOcclusion();
                    boxes++;

                    if (from != null && tracers < MAX_TRACERS) {
                        GizmoDrawing.line(from, box.getCenter(), 0xFF000000 | color, 1.5f).ignoreOcclusion();
                        tracers++;
                    }
                }
                if (boxes >= MAX_BOXES) break;
            }
        } catch (RuntimeException ex) {
            BlockESPClient.LOGGER.debug("Block ESP konnte nicht gezeichnet werden", ex);
        }
    }

    private static Box shape(BlockState state, BlockPos pos) {
        VoxelShape s = state.getOutlineShape(MinecraftClient.getInstance().world, pos);
        if (s.isEmpty()) return new Box(pos);
        return s.getBoundingBox().offset(pos);
    }

}
