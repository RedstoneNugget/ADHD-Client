package com.adhdclient.inspect.inspect;

import com.adhdclient.inspect.InspectClient;
import com.adhdclient.inspect.config.InspectConfig;
import com.adhdclient.inspect.data.InspectData;
import com.adhdclient.inspect.target.Target;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.command.DefaultPermissions;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Baut die Panel-Daten für ein Ziel: sofort aus den Client-Daten, danach
 * (asynchron) vollständig – in Singleplayer über den integrierten Server,
 * im Mehrspieler mit OP-Rechten optional über die Vanilla-NBT-Abfrage
 * (dieselbe wie F3+I; keine eigenen Pakete, keine Server-Komponente).
 */
public final class Inspector {

    private static final long SERVER_TIMEOUT_MS = 5000;
    private static final long QUERY_TIMEOUT_MS = 3000;

    private Inspector() {
    }

    /** @return null, wenn das Ziel nicht (mehr) existiert */
    @Nullable
    public static InspectData inspect(MinecraftClient client, Target target) {
        ClientWorld world = client.world;
        if (world == null) {
            return null;
        }
        IntegratedServer server = client.getServer();
        boolean sp = server != null;
        Ctx clientCtx = new Ctx(world, world.getRegistryManager(), sp ? Ctx.Source.CLIENT_SP : Ctx.Source.CLIENT_MP, false);

        return switch (target) {
            case Target.ItemTarget item -> ItemInspector.inspect(item.stack(), world, item.origin());
            case Target.BlockTarget block -> {
                if (!block.dimension().equals(world.getRegistryKey().getValue().toString())) {
                    yield null;
                }
                BlockPos pos = block.pos();
                InspectData data = BlockInspector.inspect(clientCtx, pos, block.side(), null);
                if (sp) {
                    RegistryKey<World> key = world.getRegistryKey();
                    data.pending = callOnServer(server, () -> {
                        ServerWorld sw = server.getWorld(key);
                        if (sw == null) {
                            return null;
                        }
                        return BlockInspector.inspect(new Ctx(sw, sw.getRegistryManager(), Ctx.Source.SERVER_SP, false), pos, block.side(), null);
                    });
                } else if (opQueryAllowed(client) && world.getBlockEntity(pos) != null) {
                    data.pending = queryBlock(client, pos).thenApply(nbt -> nbt == null || client.world != world ? null
                            : BlockInspector.inspect(new Ctx(world, world.getRegistryManager(), Ctx.Source.OP_QUERY, false), pos, block.side(), nbt));
                }
                yield data;
            }
            case Target.EntityTarget et -> {
                Entity entity = world.getEntityById(et.networkId());
                if (entity == null || !entity.getUuid().equals(et.uuid()) || entity.isRemoved()) {
                    yield null;
                }
                boolean self = entity == client.player;
                Ctx ctx = new Ctx(world, world.getRegistryManager(), clientCtx.source(), self);
                InspectData data = EntityInspector.inspect(ctx, entity, null, entity);
                if (sp) {
                    RegistryKey<World> key = world.getRegistryKey();
                    data.pending = callOnServer(server, () -> {
                        ServerWorld sw = server.getWorld(key);
                        Entity se = sw != null ? sw.getEntity(et.uuid()) : null;
                        if (se == null) {
                            return null;
                        }
                        return EntityInspector.inspect(new Ctx(sw, sw.getRegistryManager(), Ctx.Source.SERVER_SP, self), se, null, null);
                    });
                } else if (opQueryAllowed(client)) {
                    data.pending = queryEntity(client, entity.getId()).thenApply(nbt -> nbt == null || entity.isRemoved() ? null
                            : EntityInspector.inspect(new Ctx(world, world.getRegistryManager(), Ctx.Source.OP_QUERY, self), entity, nbt, entity));
                }
                yield data;
            }
        };
    }

    /** OP-Rechte (wie für F3+I nötig) und in den Einstellungen erlaubt? */
    public static boolean opQueryAllowed(MinecraftClient client) {
        try {
            return InspectConfig.get().opQuery && client.player != null && client.getNetworkHandler() != null
                    && client.player.getPermissions().hasPermission(DefaultPermissions.GAMEMASTERS);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static CompletableFuture<NbtCompound> queryBlock(MinecraftClient client, BlockPos pos) {
        CompletableFuture<NbtCompound> future = new CompletableFuture<>();
        ClientPlayNetworkHandler handler = client.getNetworkHandler();
        if (handler == null) {
            return CompletableFuture.completedFuture(null);
        }
        handler.getDataQueryHandler().queryBlockNbt(pos, future::complete);
        return future.completeOnTimeout(null, QUERY_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    private static CompletableFuture<NbtCompound> queryEntity(MinecraftClient client, int entityId) {
        CompletableFuture<NbtCompound> future = new CompletableFuture<>();
        ClientPlayNetworkHandler handler = client.getNetworkHandler();
        if (handler == null) {
            return CompletableFuture.completedFuture(null);
        }
        handler.getDataQueryHandler().queryEntityNbt(entityId, future::complete);
        return future.completeOnTimeout(null, QUERY_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * Führt den Code auf dem Server-Thread aus (thread-sicher), ohne den
     * Client zu blockieren. Bei Fehler oder Timeout liefert das Future null.
     */
    private static <T> CompletableFuture<T> callOnServer(IntegratedServer server, Supplier<T> task) {
        try {
            return server.submit(() -> {
                        try {
                            return task.get();
                        } catch (RuntimeException e) {
                            InspectClient.LOGGER.warn("Serverdaten konnten nicht gelesen werden", e);
                            return null;
                        }
                    })
                    .completeOnTimeout(null, SERVER_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .exceptionally(e -> {
                        InspectClient.LOGGER.debug("Integrierter Server: Abfrage fehlgeschlagen", e);
                        return null;
                    });
        } catch (RuntimeException e) {
            return CompletableFuture.completedFuture(null);
        }
    }
}
