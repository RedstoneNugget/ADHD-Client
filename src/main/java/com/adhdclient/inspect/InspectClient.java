package com.adhdclient.inspect;

import com.adhdclient.gui.HotKey;
import com.adhdclient.gui.L;
import com.adhdclient.inspect.config.InspectConfig;
import com.adhdclient.inspect.data.Category;
import com.adhdclient.inspect.data.InspectData;
import com.adhdclient.inspect.inspect.Inspector;
import com.adhdclient.inspect.target.Target;
import com.adhdclient.inspect.target.TargetFinder;
import com.adhdclient.inspect.ui.InspectScreen;
import com.adhdclient.inspect.ui.LockedHud;
import com.adhdclient.inspect.world.WorldMarkers;
import com.adhdclient.mixin.InspectCreativeScreenAccessor;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.ParentElement;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Inspect All als Modul des ADHD Client: zeigt alles, was Minecraft über einen Block, ein Item oder eine Entity
 * weiß. Tasten (Panel, Sperren, Markierungen löschen) werden im Modul-Menü eingestellt und wirken nur, wenn das
 * Modul an ist.
 */
public final class InspectClient {

    public static final String MOD_ID = "adhd_client";
    public static final Logger LOGGER = LoggerFactory.getLogger("Inspect All");

    /** Modul an/aus, über das ADHD-Menü umschaltbar. */
    public static boolean enabled = false;

    private static final int LOCK_REFRESH_TICKS = 20;
    private static final HotKey OPEN = new HotKey();
    private static final HotKey LOCK = new HotKey();
    private static final HotKey CLEAR = new HotKey();

    @Nullable
    private static Target lockedTarget;
    @Nullable
    private static InspectData lockedData;
    private static int lockRefresh;

    public void onInitializeClient() {
        InspectConfig.load();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            InspectConfig cfg = InspectConfig.get();
            boolean open = OPEN.pressed(cfg.openKey);
            boolean lock = LOCK.pressed(cfg.lockKey);
            boolean clear = CLEAR.pressed(cfg.clearKey);
            if (!enabled) {
                if (lockedTarget != null) unlock();
                return;
            }
            if (open) open(client, null);
            if (lock) toggleLockFromWorld(client, null);
            if (clear) {
                int n = WorldMarkers.clear();
                actionbar(client, n + L.t(" Markierung(en) gelöscht", " marker(s) removed"));
            }
            tickLock(client);
        });

        // In Inventar-/Container-Screens: Taste wirkt auf das Item unter dem Mauszeiger
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (screen instanceof HandledScreen<?>) {
                ScreenKeyboardEvents.afterKeyPress(screen).register((s, key) -> {
                    if (enabled && !isTyping(s)) inScreen(client, s, key.key());
                });
                ScreenMouseEvents.afterMouseClick(screen).register((s, click, consumed) -> {
                    if (enabled && !isTyping(s)) inScreen(client, s, HotKey.mouse(click.button()));
                    return consumed;
                });
            }
        });

        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            InspectConfig cfg = InspectConfig.get();
            if (enabled && cfg.tooltipHint && cfg.openKey != HotKey.NONE) {
                lines.add(Text.literal(HotKey.name(cfg.openKey) + L.t(": alle Daten", ": all data")).formatted(Formatting.DARK_GRAY));
            }
        });

        WorldRenderEvents.END_EXTRACTION.register(ctx -> {
            if (enabled) WorldMarkers.render(ctx);
        });
        HudElementRegistry.addLast(Identifier.of(MOD_ID, "inspect_locked_target"), new LockedHud());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
            WorldMarkers.clear();
            unlock();
        }));

        LOGGER.info("Inspect All geladen (ADHD Client Modul)");
    }

    private static void inScreen(MinecraftClient client, Screen screen, int code) {
        InspectConfig cfg = InspectConfig.get();
        if (code == HotKey.NONE) return;
        if (code == cfg.openKey) open(client, screen);
        else if (code == cfg.lockKey) toggleLockFromWorld(client, screen);
    }

    public static String keyName(int code) {
        return HotKey.name(code);
    }

    // ------------------------------------------------------------ Panel

    public static void open(MinecraftClient client, @Nullable Screen screen) {
        Target target = null;
        try {
            target = screen == null && lockedTarget != null ? lockedTarget : TargetFinder.find(client, screen);
        } catch (RuntimeException e) {
            LOGGER.warn("Ziel konnte nicht ermittelt werden", e);
        }
        if (target == null) {
            actionbar(client, screen != null ? L.t("Kein Item unter dem Mauszeiger.", "No item under the mouse cursor.")
                    : L.t("Kein Ziel: Block oder Entity ansehen bzw. ein Item in die Hand nehmen.",
                    "No target: look at a block or entity, or hold an item."));
            return;
        }
        InspectData data = inspectSafely(client, target);
        if (data == null) {
            actionbar(client, L.t("Ziel ist nicht mehr verfügbar (entladen oder entfernt).", "Target is no longer available (unloaded or removed)."));
            return;
        }
        client.setScreen(new InspectScreen(screen, target, data));
    }

    /** Wie {@link Inspector#inspect}, aber stürzt nie ab: Fehler landen als Hinweis im Panel. */
    @Nullable
    public static InspectData inspectSafely(MinecraftClient client, Target target) {
        try {
            return Inspector.inspect(client, target);
        } catch (RuntimeException | LinkageError e) {
            LOGGER.warn("Daten konnten nicht vollständig gelesen werden", e);
            InspectData data = new InspectData(target instanceof Target.ItemTarget ? InspectData.Kind.ITEM
                    : target instanceof Target.EntityTarget ? InspectData.Kind.ENTITY : InspectData.Kind.BLOCK);
            data.name = Text.literal(L.t("Fehler beim Lesen", "Error while reading"));
            data.icon = target instanceof Target.ItemTarget item ? item.stack() : new ItemStack(Items.BARRIER);
            data.section(Category.GENERAL, L.t("Fehler", "Error"))
                    .warn(L.t("Die Daten konnten nicht gelesen werden: ", "The data could not be read: ") + e)
                    .dim(L.t("Details stehen im Log (logs/latest.log).", "Details are in the log (logs/latest.log)."));
            return data;
        }
    }

    // ------------------------------------------------------------ Sperre

    public static boolean hasLock() {
        return lockedTarget != null;
    }

    @Nullable
    public static InspectData lockedData() {
        return lockedData;
    }

    public static boolean isLocked(Target target) {
        return lockedTarget != null && sameTarget(lockedTarget, target);
    }

    private static void unlock() {
        lockedTarget = null;
        lockedData = null;
    }

    /** Sperren/Entsperren aus dem Panel. Rückgabe: true = jetzt gesperrt. */
    public static boolean toggleLock(Target target, InspectData data) {
        if (isLocked(target)) {
            unlock();
            return false;
        }
        lockedTarget = target;
        lockedData = data;
        lockRefresh = LOCK_REFRESH_TICKS;
        return true;
    }

    public static void updateLockedData(InspectData data) {
        lockedData = data;
    }

    private static void toggleLockFromWorld(MinecraftClient client, @Nullable Screen screen) {
        if (lockedTarget != null && screen == null) {
            unlock();
            actionbar(client, L.t("Ziel entsperrt", "Target unlocked"));
            return;
        }
        Target target = TargetFinder.find(client, screen);
        if (target == null) {
            actionbar(client, L.t("Kein Ziel zum Sperren.", "No target to lock."));
            return;
        }
        InspectData data = inspectSafely(client, target);
        if (data == null) {
            actionbar(client, L.t("Ziel ist nicht verfügbar.", "Target is not available."));
            return;
        }
        lockedTarget = target;
        lockedData = data;
        lockRefresh = LOCK_REFRESH_TICKS;
        InspectConfig cfg = InspectConfig.get();
        actionbar(client, L.t("Gesperrt: ", "Locked: ") + data.name.getString() + " – "
                + keyName(cfg.openKey) + L.t(" öffnet das Panel, ", " opens the panel, ")
                + keyName(cfg.lockKey) + L.t(" entsperrt", " unlocks"));
    }

    /** Hält die Overlay-Daten des gesperrten Ziels aktuell (ca. 1× pro Sekunde). */
    private static void tickLock(MinecraftClient client) {
        if (lockedTarget == null || client.world == null) {
            return;
        }
        if (lockedData != null && lockedData.pending != null && lockedData.pending.isDone()) {
            InspectData result = null;
            try {
                result = lockedData.pending.getNow(null);
            } catch (RuntimeException ignored) {
            }
            lockedData.pending = null;
            if (result != null) {
                if (result.iconEntity == null) {
                    result.iconEntity = lockedData.iconEntity;
                }
                lockedData = result;
            }
        }
        if (client.currentScreen instanceof InspectScreen || !InspectConfig.get().hudWhenLocked) {
            return;
        }
        if (--lockRefresh > 0) {
            return;
        }
        lockRefresh = LOCK_REFRESH_TICKS;
        if (lockedData != null && lockedData.isPending()) {
            return;
        }
        if (lockedTarget instanceof Target.ItemTarget) {
            return; // Item-Kopie ändert sich nicht
        }
        InspectData fresh = inspectSafely(client, lockedTarget);
        if (fresh != null) {
            if (fresh.iconEntity == null && lockedData != null) {
                fresh.iconEntity = lockedData.iconEntity;
            }
            lockedData = fresh;
        } else {
            lockedData = null; // Ziel weg – Overlay zeigt "nicht verfügbar"
        }
    }

    private static boolean sameTarget(Target a, Target b) {
        if (a instanceof Target.BlockTarget x && b instanceof Target.BlockTarget y) {
            return x.pos().equals(y.pos()) && x.dimension().equals(y.dimension());
        }
        if (a instanceof Target.EntityTarget x && b instanceof Target.EntityTarget y) {
            return x.uuid().equals(y.uuid());
        }
        if (a instanceof Target.ItemTarget x && b instanceof Target.ItemTarget y) {
            return x == y || x.origin().equals(y.origin()) && ItemStack.areEqual(x.stack(), y.stack());
        }
        return false;
    }

    // ------------------------------------------------------------ Helfer

    private static boolean isTyping(Screen screen) {
        // fokussierte Elemente bis ins innerste Widget verfolgen (z. B. Rezeptbuch-Suche)
        Element focused = screen.getFocused();
        for (int depth = 0; focused != null && depth < 8; depth++) {
            if (focused instanceof TextFieldWidget field) {
                return field.isFocused();
            }
            focused = focused instanceof ParentElement parent ? parent.getFocused() : null;
        }
        if (screen instanceof CreativeInventoryScreen creative) {
            TextFieldWidget search = ((InspectCreativeScreenAccessor) creative).adhd$inspectGetSearchBox();
            return search != null && search.isFocused() && search.isVisible();
        }
        return false;
    }

    public static void actionbar(MinecraftClient client, String message) {
        if (client.player != null) {
            client.player.sendMessage(Text.literal(message).formatted(Formatting.GOLD), true);
        }
    }
}
