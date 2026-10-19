package com.adhdclient.packettools;

import com.adhdclient.gui.HotKey;
import com.adhdclient.gui.L;
import com.adhdclient.gui.Theme;
import com.adhdclient.gui.ThemedButton;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.common.CommonPongC2SPacket;
import net.minecraft.network.packet.c2s.common.KeepAliveC2SPacket;
import net.minecraft.network.packet.c2s.play.ButtonClickC2SPacket;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Packet Tools: Werkzeuge für Container-Menüs (Truhen, Werkbänke, Server-GUIs …). Links neben jedem Menü erscheint
 * eine Leiste mit Knöpfen: ohne Paket schließen, nur serverseitig schließen, Klick-Pakete senden/verzögern,
 * Menü speichern und wiederherstellen, trennen und dabei senden, eigene Pakete bauen, Titel kopieren.
 */
public final class PacketToolsClient {

    public static final Logger LOGGER = LoggerFactory.getLogger("Packet Tools");

    /** Modul im ADHD-Menü eingeschaltet. */
    public static boolean enabled = false;
    /** Klick-/Knopf-Pakete an den Server schicken (aus = sie werden verworfen). */
    public static boolean sendPackets = true;
    /** Klick-/Knopf-Pakete sammeln statt senden; beim Ausschalten wird alles auf einmal geschickt. */
    public static boolean delayPackets = false;
    /** Alle Pakete anhalten (außer Keep-Alive, wenn eingestellt); beim Ausschalten wird alles gesendet. */
    public static boolean holdAll = false;

    private static final int PANEL_W = 112;
    private static final int ROW = 18;
    private static final HotKey RESTORE = new HotKey();
    private static final HotKey HOLD = new HotKey();
    private static final HotKey DISCARD = new HotKey();
    private static final HotKey LOG = new HotKey();
    private static final List<Packet<?>> held = java.util.Collections.synchronizedList(new ArrayList<>());
    private static long holdStart;
    private static final List<Packet<?>> queue = new ArrayList<>();
    private static PacketToolsConfig config;
    private static boolean bypass;
    @Nullable
    private static Screen savedScreen;
    @Nullable
    private static ScreenHandler savedHandler;

    public static PacketToolsConfig config() {
        if (config == null) config = PacketToolsConfig.load();
        return config;
    }

    public static String keyName() {
        return HotKey.name(config().restoreKey);
    }

    public void onInitializeClient() {
        config();
        ClientTickEvents.END_CLIENT_TICK.register(PacketToolsClient::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(PacketToolsClient::reset));
        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.addLast(
                net.minecraft.util.Identifier.of("adhd_client", "packet_log"), new PacketLog());
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (enabled && screen instanceof HandledScreen<?> handled) addPanel(client, handled);
        });
        LOGGER.info("Packet Tools geladen (ADHD Client Modul)");
    }

    // ------------------------------------------------------------ Pakete

    /** Aus dem Mixin: true = Paket nicht senden (verworfen oder in die Warteschlange gelegt). */
    public static boolean intercept(Packet<?> packet) {
        if (!enabled || bypass) return false;
        if (holdAll && !(config().passKeepAlive && (packet instanceof KeepAliveC2SPacket || packet instanceof CommonPongC2SPacket))) {
            held.add(packet);
            return true;
        }
        if (!(packet instanceof ClickSlotC2SPacket) && !(packet instanceof ButtonClickC2SPacket)) return false;
        if (!sendPackets) return true;
        if (delayPackets) {
            queue.add(packet);
            return true;
        }
        return false;
    }

    public static int queued() {
        return queue.size();
    }

    /** Paket direkt senden, ohne Sperre und Warteschlange (für gebaute Pakete und das Abschicken der Warteschlange). */
    public static void sendDirect(Packet<?> packet) {
        ClientPlayNetworkHandler net = MinecraftClient.getInstance().getNetworkHandler();
        if (net == null) return;
        bypass = true;
        try {
            net.sendPacket(packet);
        } finally {
            bypass = false;
        }
    }

    private static int flush() {
        int n = queue.size();
        for (Packet<?> p : List.copyOf(queue)) sendDirect(p);
        queue.clear();
        return n;
    }

    public static int heldCount() {
        return held.size();
    }

    /** Alle Pakete anhalten oder (beim Ausschalten) alles Angehaltene in der richtigen Reihenfolge senden. */
    public static void setHoldAll(boolean on) {
        if (on == holdAll) return;
        if (on) {
            held.clear();
            holdStart = System.currentTimeMillis();
            holdAll = true;
            actionbar(L.t("Alle Pakete angehalten", "All packets on hold"));
        } else {
            holdAll = false;
            int n = flushHeld();
            actionbar(n + L.t(" angehaltene Pakete gesendet", " held packets sent"));
        }
    }

    /** Angehaltenes verwerfen statt senden und das Anhalten beenden. */
    public static void discardHeld() {
        int n = held.size();
        held.clear();
        holdAll = false;
        actionbar(n + L.t(" angehaltene Pakete verworfen", " held packets discarded"));
    }

    private static int flushHeld() {
        List<Packet<?>> copy;
        synchronized (held) {
            copy = List.copyOf(held);
            held.clear();
        }
        for (Packet<?> p : copy) sendDirect(p);
        return copy.size();
    }

    public static void setDelay(boolean on) {
        delayPackets = on;
        if (!on) {
            int n = flush();
            if (n > 0) actionbar(n + L.t(" verzögerte Pakete gesendet", " delayed packets sent"));
        }
    }

    // ------------------------------------------------------------ Aktionen

    /** Menü auf dem Client schließen, ohne dem Server Bescheid zu geben (er hält es weiter für offen). */
    public static void closeWithoutPacket(MinecraftClient mc) {
        mc.setScreen(null);
    }

    /** Dem Server sagen, dass das Menü zu ist, es auf dem Client aber offen lassen. */
    public static void desync(MinecraftClient mc) {
        if (mc.player == null) return;
        sendDirect(new CloseHandledScreenC2SPacket(mc.player.currentScreenHandler.syncId));
        actionbar(L.t("Serverseitig geschlossen – das Menü bleibt bei dir offen", "Closed on the server – the menu stays open for you"));
    }

    public static void saveGui(MinecraftClient mc, Screen screen) {
        if (mc.player == null) return;
        savedScreen = screen;
        savedHandler = mc.player.currentScreenHandler;
        actionbar(L.t("Menü gespeichert – ", "Menu saved – ") + keyName() + L.t(" öffnet es wieder", " opens it again"));
    }

    public static boolean hasSaved() {
        return savedScreen != null;
    }

    public static void restoreGui(MinecraftClient mc) {
        if (savedScreen == null || mc.player == null) {
            actionbar(L.t("Kein Menü gespeichert", "No menu saved"));
            return;
        }
        mc.setScreen(savedScreen);
        mc.player.currentScreenHandler = savedHandler;
    }

    /** Verzögerte Pakete schicken und sofort die Verbindung trennen. */
    public static void disconnectAndSend(MinecraftClient mc) {
        ClientPlayNetworkHandler net = mc.getNetworkHandler();
        if (net == null) return;
        holdAll = false;
        int n = flushHeld() + flush();
        delayPackets = false;
        net.getConnection().disconnect(Text.literal(L.t("Getrennt von Packet Tools (", "Disconnected by Packet Tools (") + n + L.t(" Pakete gesendet)", " packets sent)")));
    }

    /** Nachricht oder (mit "/") Befehl senden, ohne das Menü zu schließen. */
    static void sendChat(MinecraftClient mc, String text) {
        String msg = text.trim();
        ClientPlayNetworkHandler net = mc.getNetworkHandler();
        if (msg.isEmpty() || net == null) return;
        mc.inGameHud.getChatHud().addToMessageHistory(msg);
        if (msg.startsWith("/")) net.sendChatCommand(msg.substring(1));
        else net.sendChatMessage(msg);
    }

    public static void copyTitle(MinecraftClient mc, Screen screen) {
        String json;
        try {
            json = TextCodecs.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, screen.getTitle()).getOrThrow().toString();
        } catch (RuntimeException e) {
            json = screen.getTitle().getString();
        }
        mc.keyboard.setClipboard(json);
        actionbar(L.t("Titel als JSON kopiert", "Title copied as JSON"));
    }

    private static void reset() {
        queue.clear();
        held.clear();
        holdAll = false;
        sendPackets = true;
        delayPackets = false;
        savedScreen = null;
        savedHandler = null;
    }

    private static void tick(MinecraftClient mc) {
        PacketToolsConfig cfg = config();
        boolean restore = RESTORE.pressed(cfg.restoreKey);
        boolean hold = HOLD.pressed(cfg.holdKey);
        boolean discard = DISCARD.pressed(cfg.discardKey);
        boolean openLog = LOG.pressed(cfg.logKey);
        if (!enabled) {
            if (delayPackets || !sendPackets || !queue.isEmpty()) {
                sendPackets = true;
                setDelay(false);
            }
            if (holdAll) setHoldAll(false);
            return;
        }
        if (restore) restoreGui(mc);
        if (openLog && mc.currentScreen == null) mc.setScreen(new PacketLogScreen(null));
        if (discard && holdAll) discardHeld();
        else if (hold) setHoldAll(!holdAll);
        if (holdAll && cfg.showHoldInfo && mc.player != null) {
            String info = String.format(java.util.Locale.ROOT, L.t("Pakete angehalten · %.1f s · %d", "Packets on hold · %.1f s · %d"),
                    (System.currentTimeMillis() - holdStart) / 1000.0, held.size());
            mc.player.sendMessage(Text.literal(info).formatted(Formatting.LIGHT_PURPLE), true);
        }
    }

    static void actionbar(String message) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) mc.player.sendMessage(Text.literal(message).formatted(Formatting.LIGHT_PURPLE), true);
    }

    // ------------------------------------------------------------ Leiste neben dem Menü

    private static void addPanel(MinecraftClient mc, HandledScreen<?> screen) {
        int x = 4;
        int y = 22;
        int w = Math.max(80, Math.min(PANEL_W, screen.width / 2 - 96));
        int bottom = y + 9 * ROW + 4;
        int chatY = bottom + 2 + (config().showSyncInfo ? 12 : 0);
        List<ClickableWidget> buttons = Screens.getButtons(screen);
        // Hintergrund zuerst, damit Knöpfe, Feld und Tooltips darüber liegen
        buttons.add(new PanelBackground(mc, x - 2, 4, w + 4, chatY + ROW - 2 - 4 + 2, bottom));
        add(buttons, x, y, w, () -> Text.literal(L.t("Ohne Paket schließen", "Close without packet")), () -> Theme.State.NEUTRAL,
                L.t("Schließt das Menü nur bei dir – der Server hält es weiter für offen", "Closes the menu only for you – the server still thinks it is open"),
                () -> closeWithoutPacket(mc));
        y += ROW;
        add(buttons, x, y, w, () -> Text.literal(L.t("Server-Desync", "Server desync")), () -> Theme.State.NEUTRAL,
                L.t("Schließt das Menü nur auf dem Server – bei dir bleibt es offen", "Closes the menu only on the server – it stays open for you"),
                () -> desync(mc));
        y += ROW;
        add(buttons, x, y, w, () -> Text.literal(L.t("Pakete senden", "Send packets")), () -> sendPackets ? Theme.State.ON : Theme.State.OFF,
                L.t("Aus: Klicks im Menü gehen nicht an den Server", "Off: clicks in the menu are not sent to the server"),
                () -> sendPackets = !sendPackets);
        y += ROW;
        add(buttons, x, y, w, () -> Text.literal(L.t("Verzögern", "Delay") + (queue.isEmpty() ? "" : " (" + queue.size() + ")")),
                () -> delayPackets ? Theme.State.ON : Theme.State.OFF,
                L.t("An: Klicks werden gesammelt. Aus: alle auf einmal senden", "On: clicks are collected. Off: send them all at once"),
                () -> setDelay(!delayPackets));
        y += ROW;
        add(buttons, x, y, w, () -> Text.literal(L.t("Alles anhalten", "Hold all") + (holdAll ? " (" + held.size() + ")" : "")),
                () -> holdAll ? Theme.State.ON : Theme.State.OFF,
                L.t("An: gar keine Pakete mehr an den Server. Aus: alles auf einmal senden", "On: no packets go to the server at all. Off: send everything at once"),
                () -> setHoldAll(!holdAll));
        y += ROW + 4;
        add(buttons, x, y, w, () -> Text.literal(L.t("Menü speichern", "Save menu")), () -> Theme.State.NEUTRAL,
                L.t("Merkt sich dieses Menü – mit der Wiederherstellen-Taste wieder öffnen", "Remembers this menu – reopen it with the restore key"),
                () -> saveGui(mc, screen));
        y += ROW;
        add(buttons, x, y, w, () -> Text.literal(L.t("Trennen + senden", "Disconnect + send")), () -> Theme.State.NEUTRAL,
                L.t("Schickt verzögerte Pakete und trennt sofort die Verbindung", "Sends delayed packets and disconnects right away"),
                () -> disconnectAndSend(mc));
        y += ROW;
        add(buttons, x, y, w, () -> Text.literal(L.t("Paket bauen …", "Build packet …")), () -> Theme.State.NEUTRAL,
                L.t("Eigenes Klick- oder Knopf-Paket zusammenstellen und senden", "Put together and send your own click or button packet"),
                () -> mc.setScreen(new FabricateScreen(screen)));
        y += ROW;
        int halfW = (w - 4) / 2;
        add(buttons, x, y, halfW, () -> Text.literal(L.t("Titel", "Title")), () -> Theme.State.NEUTRAL,
                L.t("Kopiert den Menü-Titel als JSON (mit Farben und Schrift)", "Copies the menu title as JSON (with colors and font)"),
                () -> copyTitle(mc, screen));
        add(buttons, x + halfW + 4, y, w - halfW - 4, () -> Text.literal(L.t("Log", "Log")), () -> Theme.State.NEUTRAL,
                L.t("Großes Paket-Log: alle gesendeten und empfangenen Pakete", "Large packet log: all sent and received packets"),
                () -> mc.setScreen(new PacketLogScreen(screen)));

        // Chat-Feld: schreiben, während das Menü offen bleibt (Enter sendet, "/" am Anfang = Befehl)
        TextFieldWidget chat = new TextFieldWidget(mc.textRenderer, x, chatY, w, ROW - 2, Text.literal("Chat")) {
            @Override
            public void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
                super.renderWidget(ctx, mouseX, mouseY, delta);
                Theme.field(ctx, this);
            }
        };
        chat.setMaxLength(256);
        chat.setPlaceholder(Text.literal(L.t("Chat / Befehl …", "Chat / command …")).withColor(0xFF808080));
        chat.setTooltip(Tooltip.of(Text.literal(L.t("Hier tippen und Enter drücken – das Menü bleibt offen. Mit / am Anfang ein Befehl.",
                "Type here and press Enter – the menu stays open. Start with / for a command."))));
        buttons.add(chat);
        ScreenKeyboardEvents.allowKeyPress(screen).register((s, input) -> {
            if (!chat.isFocused()) return true;
            if (input.isEnter()) {
                sendChat(mc, chat.getText());
                chat.setText("");
                chat.setFocused(false); // wie der normale Chat: nach dem Senden ist das Feld wieder frei
                s.setFocused(null);
            } else if (input.isEscape()) {
                chat.setFocused(false);
                s.setFocused(null);
            } else {
                chat.keyPressed(input);
            }
            return false; // Tasten nicht ans Menü weitergeben (sonst schließt z. B. E das Inventar)
        });
    }

    private static void add(List<ClickableWidget> buttons, int x, int y, int w, Supplier<Text> label, Supplier<Theme.State> state,
                            String tooltip, Runnable action) {
        ThemedButton b = new ThemedButton(x, y, w, ROW - 2, label, state, btn -> action.run());
        b.setTooltip(Tooltip.of(Text.literal(tooltip)));
        buttons.add(b);
    }

    /** Hintergrund der Leiste mit Titel und Sync-ID; nimmt keine Klicks an. */
    private static final class PanelBackground extends ClickableWidget {
        private final MinecraftClient mc;
        private final int infoY;

        PanelBackground(MinecraftClient mc, int x, int y, int w, int h, int infoY) {
            super(x, y, w, h, Text.empty());
            this.mc = mc;
            this.infoY = infoY;
            this.active = false;
        }

        @Override
        protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
            int x1 = getX();
            int y1 = getY();
            int x2 = x1 + getWidth();
            ctx.fill(x1, y1, x2, y1 + getHeight(), Theme.argb(Math.round(Theme.config().opacity * 2.55f), Theme.config().background));
            ctx.fill(x1, y1, x2, y1 + 14, Theme.accent());
            ctx.drawTextWithShadow(mc.textRenderer, "Packet Tools", x1 + 5, y1 + 3, Theme.text());
            if (config().showSyncInfo && mc.player != null) {
                ScreenHandler h = mc.player.currentScreenHandler;
                String info = "Sync-ID " + h.syncId + " · Rev " + h.getRevision();
                ctx.drawTextWithShadow(mc.textRenderer, mc.textRenderer.trimToWidth(info, getWidth() - 4), x1 + 2, infoY + 2, Theme.textDim());
            }
        }

        @Override
        public boolean isMouseOver(double mouseX, double mouseY) {
            return false;
        }

        @Override
        protected void appendClickableNarrations(net.minecraft.client.gui.screen.narration.NarrationMessageBuilder builder) {
        }
    }
}
