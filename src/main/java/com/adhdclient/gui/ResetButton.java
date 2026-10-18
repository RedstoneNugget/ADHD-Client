package com.adhdclient.gui;

import net.minecraft.client.gui.DrawContext;

/** Kleiner Knopf mit gezeichnetem Kreispfeil (zurücksetzen) – das Unicode-Zeichen wäre in Minecraft winzig. */
public class ResetButton extends ThemedButton {

    /** 7×7-Pixel-Kreispfeil im Uhrzeigersinn, Spitze oben rechts. */
    private static final String[] ICON = {
            "..###.#",
            ".#...##",
            "#...###",
            "#......",
            "#.....#",
            ".#...#.",
            "..###.."
    };

    public ResetButton(int x, int y, int width, int height, PressAction onPress) {
        super(x, y, width, height, net.minecraft.text.Text::empty, () -> Theme.State.NEUTRAL, onPress);
    }

    @Override
    protected void drawIcon(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        super.drawIcon(context, mouseX, mouseY, deltaTicks);
        int color = isHovered() ? Theme.accent() : Theme.text();
        int ox = getX() + (getWidth() - ICON[0].length()) / 2;
        int oy = getY() + (getHeight() - ICON.length) / 2;
        for (int row = 0; row < ICON.length; row++) {
            for (int col = 0; col < ICON[row].length(); col++) {
                if (ICON[row].charAt(col) == '#') context.fill(ox + col, oy + row, ox + col + 1, oy + row + 1, color);
            }
        }
    }
}
