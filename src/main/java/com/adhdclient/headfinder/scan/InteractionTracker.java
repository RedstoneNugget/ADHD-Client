package com.adhdclient.headfinder.scan;

import com.adhdclient.headfinder.HeadFinderClient;
import com.adhdclient.headfinder.config.ModConfig;
import com.adhdclient.headfinder.data.HeadEntry;
import com.adhdclient.headfinder.data.HeadStore;
import com.adhdclient.headfinder.detect.BlockESP;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Markiert Köpfe als gefunden, wenn der Spieler sie anklickt. Die Fabric-Callbacks werden auf dem Client
 * aufgerufen, bevor das Klick-Paket gesendet wird; wir beobachten nur (immer PASS) und verändern nichts.
 */
public final class InteractionTracker {

    private final BlockESP matcher;
    private final HeadStore store;

    public InteractionTracker(BlockESP matcher, HeadStore store) {
        this.matcher = matcher;
        this.store = store;
    }

    public void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world.isClient() && HeadFinderClient.config().foundOnRightClick) onBlock(world, hit.getBlockPos(), "Rechtsklick");
            return ActionResult.PASS;
        });
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (world.isClient() && HeadFinderClient.config().foundOnLeftClick) onBlock(world, pos, "Linksklick");
            return ActionResult.PASS;
        });
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClient() && HeadFinderClient.config().foundOnRightClick) onEntity(world, entity, "Rechtsklick");
            return ActionResult.PASS;
        });
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClient() && HeadFinderClient.config().foundOnLeftClick) onEntity(world, entity, "Linksklick");
            return ActionResult.PASS;
        });
    }

    private void onBlock(World world, BlockPos pos, String reason) {
        if (!(world instanceof ClientWorld cw) || !store.isOpen()) return;
        String dim = HeadScanner.dimension(cw);
        HeadEntry e = store.get(HeadEntry.key(dim, pos, HeadEntry.Kind.BLOCK));
        if (e == null) {
            // Noch nicht gescannt (z. B. gerade erst geladen) → direkt prüfen
            BlockESP.Match m = matcher.match(world.getBlockState(pos));
            if (m == null) return;
            store.discover(dim, pos, HeadEntry.Kind.BLOCK, m.profileName());
            e = store.get(HeadEntry.key(dim, pos, HeadEntry.Kind.BLOCK));
        }
        markFound(e, reason);
    }

    private void onEntity(World world, Entity entity, String reason) {
        if (!(world instanceof ClientWorld cw) || !store.isOpen()) return;
        if (!(entity instanceof ItemFrameEntity frame)) return;
        String dim = HeadScanner.dimension(cw);
        HeadEntry e = store.get(HeadEntry.key(dim, frame.getBlockPos(), HeadEntry.Kind.FRAME));
        if (e == null) {
            BlockESP.Match m = matcher.match(frame.getHeldItemStack());
            if (m == null) return;
            store.discover(dim, frame.getBlockPos(), HeadEntry.Kind.FRAME, m.profileName());
            e = store.get(HeadEntry.key(dim, frame.getBlockPos(), HeadEntry.Kind.FRAME));
        }
        markFound(e, reason);
    }

    private void markFound(HeadEntry e, String reason) {
        if (e == null || e.found) return;
        store.setFound(e, true, reason);
        ModConfig cfg = HeadFinderClient.config();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.inGameHud != null) {
            String hint = cfg.hideFound ? " – wird nicht mehr markiert" : "";
            mc.inGameHud.setOverlayMessage(Text.literal("Kopf gefunden (" + store.countFound() + "/" + store.countDiscovered() + ")" + hint)
                    .formatted(Formatting.GREEN), false);
        }
    }
}
