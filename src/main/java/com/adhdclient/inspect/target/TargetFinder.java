package com.adhdclient.inspect.target;

import com.adhdclient.gui.L;
import com.adhdclient.inspect.config.InspectConfig;
import com.adhdclient.mixin.InspectHandledScreenAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.boss.dragon.EnderDragonPart;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Ermittelt das Ziel: Item unter dem Mauszeiger (in Inventar-Screens) →
 * angesehene Entity → angesehener Block (Fadenkreuz, notfalls mit größerer
 * Reichweite) → Item in Haupt- bzw. Nebenhand.
 */
public final class TargetFinder {

    private TargetFinder() {
    }

    @Nullable
    public static Target find(MinecraftClient client, @Nullable Screen screen) {
        if (screen instanceof HandledScreen<?> handled) {
            Slot slot = ((InspectHandledScreenAccessor) handled).adhd$inspectGetFocusedSlot();
            if (slot != null && slot.hasStack()) {
                String origin = "Slot " + slot.getIndex() + " in " + screen.getTitle().getString();
                return new Target.ItemTarget(slot.getStack().copy(), origin);
            }
            return null;
        }
        if (client.player == null || client.world == null) {
            return null;
        }
        String dim = client.world.getRegistryKey().getValue().toString();
        HitResult hit = lookTarget(client);
        if (hit instanceof EntityHitResult entityHit) {
            Entity e = entityHit.getEntity();
            if (e instanceof EnderDragonPart part) {
                e = part.owner; // Teile sind keine eigenständigen Entities
            }
            return new Target.EntityTarget(e.getId(), e.getUuid());
        }
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            return new Target.BlockTarget(blockHit.getBlockPos().toImmutable(), blockHit.getSide(), dim);
        }
        ItemStack main = client.player.getStackInHand(Hand.MAIN_HAND);
        if (!main.isEmpty()) {
            return new Target.ItemTarget(main.copy(), L.t("Haupthand", "Main hand"));
        }
        ItemStack off = client.player.getStackInHand(Hand.OFF_HAND);
        if (!off.isEmpty()) {
            return new Target.ItemTarget(off.copy(), L.t("Nebenhand", "Off hand"));
        }
        return null;
    }

    /** Vanilla-Fadenkreuz; wenn das nichts trifft, Raycast mit der eingestellten Reichweite. */
    @Nullable
    private static HitResult lookTarget(MinecraftClient client) {
        HitResult vanilla = client.crosshairTarget;
        if (vanilla instanceof EntityHitResult) {
            return vanilla;
        }
        Entity camera = client.getCameraEntity();
        if (camera == null) {
            return vanilla;
        }
        double range = InspectConfig.get().lookRange;
        float tickDelta = client.getRenderTickCounter().getTickProgress(true);
        HitResult block = camera.raycast(range, tickDelta, false);
        Vec3d start = camera.getCameraPosVec(tickDelta);
        double maxDist = block != null && block.getType() != HitResult.Type.MISS ? block.getPos().distanceTo(start) : range;
        Vec3d dir = camera.getRotationVec(tickDelta);
        Vec3d end = start.add(dir.multiply(maxDist));
        Box box = camera.getBoundingBox().stretch(dir.multiply(maxDist)).expand(1.0);
        Entity root = camera.getRootVehicle();
        EntityHitResult entity = ProjectileUtil.raycast(camera, start, end, box,
                e -> !e.isSpectator() && e.getRootVehicle() != root, maxDist * maxDist);
        if (entity != null) {
            return entity;
        }
        if (vanilla != null && vanilla.getType() == HitResult.Type.BLOCK) {
            return vanilla;
        }
        return block;
    }
}
