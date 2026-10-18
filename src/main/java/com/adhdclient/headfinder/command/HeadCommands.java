package com.adhdclient.headfinder.command;

import com.adhdclient.headfinder.HeadFinderClient;
import com.adhdclient.headfinder.config.ModConfig;
import com.adhdclient.headfinder.data.HeadEntry;
import com.adhdclient.headfinder.data.HeadStore;
import com.adhdclient.headfinder.scan.HeadScanner;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/**
 * Clientseitige Befehle /headfinder (Kurzform /hf). Sie werden nie an den Server geschickt.
 */
public final class HeadCommands {

    private HeadCommands() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(build("headfinder"));
            dispatcher.register(build("hf"));
        });
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> build(String name) {
        return literal(name)
                .executes(HeadCommands::help)
                .then(literal("help").executes(HeadCommands::help))
                .then(literal("list")
                        .executes(c -> list(c, 10))
                        .then(argument("anzahl", IntegerArgumentType.integer(1, 200)).executes(c -> list(c, IntegerArgumentType.getInteger(c, "anzahl")))))
                .then(literal("status").executes(HeadCommands::status))
                .then(literal("reset")
                        .executes(HeadCommands::resetFound)
                        .then(literal("all").executes(HeadCommands::resetAll)))
                .then(literal("toggle")
                        .executes(c -> toggle(c, "esp"))
                        .then(literal("markierungen").executes(c -> toggle(c, "esp")))
                        .then(literal("esp").executes(c -> toggle(c, "esp")))
                        .then(literal("tracer").executes(c -> toggle(c, "tracer")))
                        .then(literal("hud").executes(c -> toggle(c, "hud")))
                        .then(literal("labels").executes(c -> toggle(c, "labels")))
                        .then(literal("hidefound").executes(c -> toggle(c, "hidefound"))))
                .then(literal("target")
                        .then(literal("nearest").executes(HeadCommands::targetNearest))
                        .then(literal("clear").executes(HeadCommands::targetClear))
                        .then(xyz(c -> c.target)))
                .then(literal("found").then(xyz(c -> c.found)))
                .then(literal("unfound").then(xyz(c -> c.unfound)))
                .then(literal("scan").executes(HeadCommands::scan))
                .then(literal("reload").executes(HeadCommands::reload))
                .then(literal("open").executes(HeadCommands::openList));
    }

    // ---------------------------------------------------------------- x y z

    /** Aktion auf einen Kopf an Koordinaten. */
    private interface PosAction {
        int run(CommandContext<FabricClientCommandSource> c, HeadEntry e);
    }

    private static final class PosActions {
        final PosAction target = (c, e) -> {
            HeadFinderClient.store().setTarget(e);
            ok(c, "Ziel gesetzt: " + e.coords());
            return 1;
        };
        final PosAction found = (c, e) -> {
            HeadFinderClient.store().setFound(e, true, "manuell");
            ok(c, "Als gefunden markiert: " + e.coords());
            return 1;
        };
        final PosAction unfound = (c, e) -> {
            HeadFinderClient.store().setFound(e, false, "");
            ok(c, "Wieder als offen markiert: " + e.coords());
            return 1;
        };
    }

    private static final PosActions ACTIONS = new PosActions();

    private static RequiredArgumentBuilder<FabricClientCommandSource, Integer> xyz(Function<PosActions, PosAction> pick) {
        return argument("x", IntegerArgumentType.integer())
                .then(argument("y", IntegerArgumentType.integer())
                        .then(argument("z", IntegerArgumentType.integer()).executes(c -> {
                            BlockPos pos = new BlockPos(IntegerArgumentType.getInteger(c, "x"),
                                    IntegerArgumentType.getInteger(c, "y"), IntegerArgumentType.getInteger(c, "z"));
                            HeadEntry e = find(c, pos);
                            if (e == null) {
                                c.getSource().sendError(Text.literal("Kein entdeckter Kopf bei " + pos.getX() + " " + pos.getY() + " " + pos.getZ()));
                                return 0;
                            }
                            return pick.apply(ACTIONS).run(c, e);
                        })));
    }

    private static HeadEntry find(CommandContext<FabricClientCommandSource> c, BlockPos pos) {
        MinecraftClient mc = c.getSource().getClient();
        if (mc.world == null) return null;
        String dim = HeadScanner.dimension(mc.world);
        HeadStore store = HeadFinderClient.store();
        HeadEntry e = store.get(HeadEntry.key(dim, pos, HeadEntry.Kind.BLOCK));
        if (e == null) e = store.get(HeadEntry.key(dim, pos, HeadEntry.Kind.FRAME));
        return e;
    }

    // ---------------------------------------------------------------- Befehle

    private static int help(CommandContext<FabricClientCommandSource> c) {
        Consumer<String> line = s -> c.getSource().sendFeedback(Text.literal(s).formatted(Formatting.GRAY));
        c.getSource().sendFeedback(Text.literal("Head Finder – Befehle (/headfinder oder /hf):").formatted(Formatting.GOLD));
        line.accept(" list [anzahl] – nächste Köpfe mit Koordinaten");
        line.accept(" status – Zähler, Suchprofile, Speicherdatei");
        line.accept(" reset – alle wieder als NICHT gefunden markieren");
        line.accept(" reset all – alles vergessen (auch Positionen)");
        line.accept(" toggle [markierungen|tracer|hud|labels|hidefound]");
        line.accept(" target nearest|clear|<x y z> – Ziel für Tracer/Pfeil");
        line.accept(" found <x y z> / unfound <x y z> – Status von Hand setzen");
        line.accept(" scan – sofort neu scannen, reload – Config neu laden, open – Liste öffnen (Taste K)");
        return 1;
    }

    private static int list(CommandContext<FabricClientCommandSource> c, int max) {
        MinecraftClient mc = c.getSource().getClient();
        HeadStore store = HeadFinderClient.store();
        if (mc.player == null || mc.world == null) return 0;
        String dim = HeadScanner.dimension(mc.world);
        Vec3d eye = mc.player.getEyePos();
        List<HeadEntry> all = store.all();
        all.sort(Comparator.<HeadEntry>comparingInt(e -> dim.equals(e.dim) ? 0 : 1)
                .thenComparingDouble(e -> e.center().squaredDistanceTo(eye)));
        c.getSource().sendFeedback(Text.literal("Köpfe: " + store.countFound() + " gefunden / " + store.countDiscovered() + " entdeckt")
                .formatted(Formatting.GOLD));
        if (all.isEmpty()) {
            c.getSource().sendFeedback(Text.literal(" Noch keine Köpfe entdeckt. Nur Köpfe in geladenen Chunks können erkannt werden.").formatted(Formatting.GRAY));
            return 1;
        }
        int n = 0;
        for (HeadEntry e : all) {
            if (n++ >= max) break;
            boolean here = dim.equals(e.dim);
            String dist = here ? Math.round(e.center().distanceTo(eye)) + " m" : e.dimShort();
            MutableText t = Text.literal(" " + (e.found ? "✔ " : "• "))
                    .formatted(e.found ? Formatting.GREEN : Formatting.RED);
            t.append(Text.literal(e.coords()).styled(s -> s.withColor(Formatting.WHITE)
                    .withClickEvent(new ClickEvent.CopyToClipboard(e.coords()))
                    .withHoverEvent(new HoverEvent.ShowText(Text.literal("Klicken: Koordinaten kopieren")))));
            t.append(Text.literal("  " + dist + (e.kind != HeadEntry.Kind.BLOCK ? " (" + e.kind.title + ")" : "")).formatted(Formatting.GRAY));
            if (!e.found && here && e.kind != HeadEntry.Kind.ITEM) {
                t.append(Text.literal("  [Ziel]").styled(s -> s.withColor(Formatting.YELLOW)
                        .withClickEvent(new ClickEvent.SuggestCommand("/headfinder target " + e.coords()))
                        .withHoverEvent(new HoverEvent.ShowText(Text.literal("Befehl einfügen – mit Enter bestätigen")))));
            }
            c.getSource().sendFeedback(t);
        }
        if (all.size() > max) c.getSource().sendFeedback(Text.literal(" … und " + (all.size() - max) + " weitere (Taste K für die ganze Liste)").formatted(Formatting.GRAY));
        return 1;
    }

    private static int status(CommandContext<FabricClientCommandSource> c) {
        HeadStore store = HeadFinderClient.store();
        ModConfig cfg = HeadFinderClient.config();
        c.getSource().sendFeedback(Text.literal("Block Finder – Status").formatted(Formatting.GOLD));
        Consumer<String> line = s -> c.getSource().sendFeedback(Text.literal(" " + s).formatted(Formatting.GRAY));
        line.accept("Funde: " + store.countFound() + " gefunden / " + store.countDiscovered() + " entdeckt");
        line.accept("Geladene Chunks: " + HeadFinderClient.scanner().loadedChunkCount() + ", Treffer darin: " + HeadFinderClient.scanner().lastMatchCount()
                + ", Scan: " + HeadFinderClient.scanner().lastIntervalMicros() + " µs, Zeichnen: " + com.adhdclient.headfinder.render.HeadRenderer.lastRenderMicros + " µs");
        line.accept("Markierungen " + onOff(cfg.espEnabled) + ", Tracer " + cfg.tracerMode.title + ", HUD " + onOff(cfg.hudEnabled)
                + ", gefundene " + (cfg.hideFound ? "ausgeblendet" : "grün"));
        for (String s : HeadFinderClient.matcher().describe()) line.accept("Sucht: " + s);
        if (!HeadFinderClient.matcher().hasCriteria()) line.accept("Achtung: kein aktives Suchprofil!");
        if (store.isOpen()) line.accept("Datei: config/head_finder/found/" + store.file().getFileName());
        return 1;
    }

    private static int resetFound(CommandContext<FabricClientCommandSource> c) {
        int n = HeadFinderClient.store().resetFound();
        ok(c, n + " Köpfe wieder als nicht gefunden markiert – sie werden wieder angezeigt.");
        return 1;
    }

    private static int resetAll(CommandContext<FabricClientCommandSource> c) {
        int n = HeadFinderClient.store().resetAll();
        HeadFinderClient.scanner().requestRescan();
        ok(c, n + " gespeicherte Köpfe gelöscht. Geladene Chunks werden neu gescannt.");
        return 1;
    }

    private static int toggle(CommandContext<FabricClientCommandSource> c, String what) {
        ModConfig cfg = HeadFinderClient.config();
        String msg;
        switch (what) {
            case "tracer" -> {
                cfg.tracerMode = cfg.tracerMode.next();
                msg = "Tracer: " + cfg.tracerMode.title;
            }
            case "hud" -> {
                cfg.hudEnabled = !cfg.hudEnabled;
                msg = "HUD " + onOff(cfg.hudEnabled);
            }
            case "labels" -> {
                cfg.labelsEnabled = !cfg.labelsEnabled;
                msg = "Labels " + onOff(cfg.labelsEnabled);
            }
            case "hidefound" -> {
                cfg.hideFound = !cfg.hideFound;
                msg = "Gefundene Köpfe: " + (cfg.hideFound ? "ausgeblendet" : "grün angezeigt");
            }
            default -> {
                cfg.espEnabled = !cfg.espEnabled;
                msg = "Markierungen " + onOff(cfg.espEnabled);
            }
        }
        HeadFinderClient.onConfigEdited();
        ok(c, msg);
        return 1;
    }

    private static int targetNearest(CommandContext<FabricClientCommandSource> c) {
        MinecraftClient mc = c.getSource().getClient();
        if (mc.player == null || mc.world == null) return 0;
        HeadEntry e = HeadFinderClient.store().nearestOpen(HeadScanner.dimension(mc.world), mc.player.getEyePos());
        if (e == null) {
            c.getSource().sendError(Text.literal("Kein offener Kopf in dieser Dimension bekannt."));
            return 0;
        }
        HeadFinderClient.store().setTarget(e);
        ok(c, "Ziel gesetzt: " + e.coords());
        return 1;
    }

    private static int targetClear(CommandContext<FabricClientCommandSource> c) {
        HeadFinderClient.store().setTarget(null);
        ok(c, "Ziel aufgehoben – Tracer/Pfeil zeigen wieder zum nächsten Kopf.");
        return 1;
    }

    private static int scan(CommandContext<FabricClientCommandSource> c) {
        HeadFinderClient.scanner().requestRescan();
        ok(c, "Geladene Chunks werden neu gescannt.");
        return 1;
    }

    private static int reload(CommandContext<FabricClientCommandSource> c) {
        HeadFinderClient.reloadConfig();
        ok(c, "Config neu geladen (" + HeadFinderClient.config().profiles.size() + " Suchprofile).");
        return 1;
    }

    private static int openList(CommandContext<FabricClientCommandSource> c) {
        MinecraftClient mc = c.getSource().getClient();
        // Chat-Screen schließt sich nach dem Befehl – Liste deshalb im nächsten Tick öffnen
        mc.send(() -> mc.setScreen(new com.adhdclient.headfinder.gui.HeadListScreen(null)));
        return 1;
    }

    private static void ok(CommandContext<FabricClientCommandSource> c, String msg) {
        c.getSource().sendFeedback(Text.literal("[Head Finder] ").formatted(Formatting.GOLD).append(Text.literal(msg).formatted(Formatting.WHITE)));
    }

    private static String onOff(boolean b) {
        return b ? "an" : "aus";
    }
}
