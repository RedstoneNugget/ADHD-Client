package com.adhdclient.packettools;

import com.adhdclient.gui.L;
import com.adhdclient.gui.Theme;
import com.adhdclient.gui.ThemedScreen;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.network.packet.c2s.play.ButtonClickC2SPacket;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.screen.sync.ItemStackHash;
import net.minecraft.text.Text;

/**
 * Eigenes Paket bauen: Slot-Klick (Sync-ID, Revision, Slot, Maustaste, Aktion) oder Knopf-Klick (Sync-ID, Knopf-ID),
 * beliebig oft senden. "Zurück" öffnet das Menü von vorher wieder.
 */
public final class FabricateScreen extends ThemedScreen {

    private static final int W = 300;
    private static final int ROW = 20;

    private static boolean clickSlot = true;
    private static String slotText = "0";
    private static String buttonText = "0";
    private static String timesText = "1";
    private static SlotActionType action = SlotActionType.PICKUP;

    private TextFieldWidget syncField;
    private TextFieldWidget revisionField;
    private TextFieldWidget slotField;
    private TextFieldWidget buttonField;
    private TextFieldWidget timesField;

    public FabricateScreen(Screen parent) {
        super(Text.literal(L.t("Paket bauen", "Build packet")), parent);
    }

    @Override
    protected void build() {
        int h = Theme.TITLE_H + 6 + 12 + ROW + 6 + 12 + 3 * ROW + 6 + ROW + 16;
        int y = centeredPanel(W, h);
        int x = panelX1 + 8;
        int inner = W - 16;
        int half = (inner - 4) / 2;
        ScreenHandler handler = client != null && client.player != null ? client.player.currentScreenHandler : null;

        section(L.t("Paket-Art", "Packet type"), x, y, inner);
        y += 12;
        toggle(L.t("Slot-Klick", "Slot click"), () -> clickSlot, v -> clickSlot = true, x, y, half, ROW - 2,
                L.t("Klick auf einen Slot (wie ein Mausklick im Menü)", "Click on a slot (like a mouse click in the menu)"), this::clearAndInit);
        toggle(L.t("Knopf-Klick", "Button click"), () -> !clickSlot, v -> clickSlot = false, x + half + 4, y, inner - half - 4, ROW - 2,
                L.t("Knopf in Menüs wie Verzauberungstisch, Webstuhl oder Steinsäge", "Button in menus like the enchanting table, loom or stonecutter"), this::clearAndInit);
        y += ROW + 6;

        section(L.t("Werte", "Values"), x, y, inner);
        y += 12;
        syncField = number(x, y, half, "Sync-ID", handler == null ? "0" : String.valueOf(handler.syncId));
        if (clickSlot) {
            revisionField = number(x + half + 4, y, inner - half - 4, "Revision", handler == null ? "0" : String.valueOf(handler.getRevision()));
            y += ROW;
            slotField = number(x, y, half, "Slot", slotText);
            buttonField = number(x + half + 4, y, inner - half - 4, L.t("Maustaste", "Mouse button"), buttonText);
            y += ROW;
            ButtonWidget act = ButtonWidget.builder(Text.literal(actionText()), b -> {
                action = SlotActionType.values()[(action.ordinal() + 1) % SlotActionType.values().length];
                b.setMessage(Text.literal(actionText()));
            }).dimensions(x, y, half, ROW - 2).build();
            act.setTooltip(Tooltip.of(Text.literal(L.t("PICKUP = normaler Klick, QUICK_MOVE = Shift-Klick, SWAP = Zahlentaste, THROW = Q …",
                    "PICKUP = normal click, QUICK_MOVE = shift-click, SWAP = number key, THROW = Q …"))));
            addDrawableChild(act);
        } else {
            revisionField = null;
            buttonField = number(x + half + 4, y, inner - half - 4, L.t("Knopf-ID", "Button ID"), buttonText);
            y += 2 * ROW;
        }
        timesField = number(x + half + 4, y, inner - half - 4, L.t("Wie oft", "Times"), timesText);
        y += ROW + 6;

        addDrawableChild(ButtonWidget.builder(Text.literal(L.t("Senden", "Send")), b -> send()).dimensions(x, y, half, ROW - 2).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(L.t("Zurück", "Back")), b -> close()).dimensions(x + half + 4, y, inner - half - 4, ROW - 2).build());
        status(L.t("Gebaute Pakete gehen immer raus – auch wenn \"Pakete senden\" aus ist", "Built packets are always sent – even when \"Send packets\" is off"));
    }

    private static String actionText() {
        return L.t("Aktion: ", "Action: ") + action.name();
    }

    /** Zahlenfeld mit Namen links daneben. */
    private TextFieldWidget number(int x, int y, int w, String label, String value) {
        int labelW = Math.max(56, textRenderer.getWidth(label) + 6);
        line(label, x, y + 5, Theme.text());
        TextFieldWidget f = new TextFieldWidget(textRenderer, x + labelW, y, w - labelW, ROW - 2, Text.literal(label));
        f.setMaxLength(6);
        f.setTextPredicate(s -> s.isEmpty() || s.equals("-") || s.matches("-?\\d{1,6}"));
        f.setText(value);
        addDrawableChild(f);
        return f;
    }

    private static int parse(TextFieldWidget f, int fallback) {
        try {
            return f == null ? fallback : Integer.parseInt(f.getText().trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void send() {
        if (client == null || client.getNetworkHandler() == null || client.player == null) return;
        int times = Math.max(1, Math.min(100, parse(timesField, 1)));
        int sync = parse(syncField, client.player.currentScreenHandler.syncId);
        slotText = slotField != null ? slotField.getText() : slotText;
        buttonText = buttonField.getText();
        timesText = timesField.getText();
        for (int i = 0; i < times; i++) {
            if (clickSlot) {
                ScreenHandler h = client.player.currentScreenHandler;
                ItemStackHash cursor = ItemStackHash.fromItemStack(h.getCursorStack(), client.getNetworkHandler().getComponentHasher());
                PacketToolsClient.sendDirect(new ClickSlotC2SPacket(sync, parse(revisionField, h.getRevision()), (short) parse(slotField, 0),
                        (byte) parse(buttonField, 0), action, new Int2ObjectOpenHashMap<>(), cursor));
            } else {
                PacketToolsClient.sendDirect(new ButtonClickC2SPacket(sync, parse(buttonField, 0)));
            }
        }
        status(times + L.t(" Paket(e) gesendet", " packet(s) sent"));
    }
}
