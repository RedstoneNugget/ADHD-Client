package com.adhdclient.casino.client;

import com.adhdclient.casino.config.CasinoConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;

/**
 * Soundeffekte des Casinos – ausschließlich Vanilla-Sounds, nur lokal abgespielt.
 * Respektiert die Einstellungen (an/aus, Lautstärke).
 */
public final class CasinoSounds {

    private CasinoSounds() {
    }

    public static void play(SoundEvent sound, float pitch, float volume) {
        CasinoConfig cfg = LuckyCasinoClient.config();
        if (!cfg.soundsEnabled || cfg.soundVolume <= 0.01f) {
            return;
        }
        MinecraftClient.getInstance().getSoundManager()
                .play(PositionedSoundInstance.ui(sound, pitch, volume * cfg.soundVolume));
    }

    public static void play(RegistryEntry<SoundEvent> sound, float pitch, float volume) {
        play(sound.value(), pitch, volume);
    }

    /** Leises Klicken (Einsatz ändern, Chip setzen). */
    public static void click() {
        play(SoundEvents.UI_BUTTON_CLICK, 1.4f, 0.4f);
    }

    /** Ticken (Walze, Roulettekugel, Glücksrad, Lootbox-Streifen). */
    public static void tick(float pitch) {
        play(SoundEvents.BLOCK_NOTE_BLOCK_HAT, pitch, 0.5f);
    }

    /** Walze/Rad rastet ein. */
    public static void stop() {
        play(SoundEvents.BLOCK_STONE_BUTTON_CLICK_ON, 1.2f, 0.6f);
    }

    /** Kleiner Gewinn: Erfahrungsorb. */
    public static void win() {
        play(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 0.8f);
    }

    /** Großer Gewinn: Level-Up + zufriedener Villager. */
    public static void bigWin() {
        play(SoundEvents.ENTITY_PLAYER_LEVELUP, 1.0f, 0.8f);
        play(SoundEvents.ENTITY_VILLAGER_YES, 1.0f, 0.7f);
    }

    /** Jackpot: Glocke + Challenge-Fanfare. */
    public static void jackpot() {
        play(SoundEvents.BLOCK_BELL_USE, 1.0f, 1.0f);
        play(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.8f);
    }

    /** Verloren: enttäuschter Villager. */
    public static void lose() {
        play(SoundEvents.ENTITY_VILLAGER_NO, 1.0f, 0.6f);
    }

    /** Herber Verlust (Bust, Crash): Amboss. */
    public static void heavyLoss() {
        play(SoundEvents.BLOCK_ANVIL_LAND, 0.8f, 0.4f);
    }

    /** Unentschieden. */
    public static void push() {
        play(SoundEvents.ENTITY_VILLAGER_TRADE, 1.0f, 0.5f);
    }

    public static void cardDeal() {
        play(SoundEvents.ITEM_BOOK_PAGE_TURN, 1.3f, 0.8f);
    }

    public static void coinFlip() {
        play(SoundEvents.BLOCK_CHAIN_STEP, 1.6f, 0.8f);
    }

    public static void coinLand() {
        play(SoundEvents.BLOCK_NOTE_BLOCK_BELL, 1.8f, 0.5f);
    }

    public static void scratch() {
        play(SoundEvents.ITEM_BRUSH_BRUSHING_SAND, 1.5f, 0.35f);
    }

    public static void chestOpen() {
        play(SoundEvents.BLOCK_CHEST_OPEN, 1.0f, 0.8f);
    }

    public static void legendary() {
        play(SoundEvents.ITEM_TOTEM_USE, 1.2f, 0.4f);
        play(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.7f);
    }

    public static void crashTick(float pitch) {
        play(SoundEvents.BLOCK_NOTE_BLOCK_BIT, pitch, 0.25f);
    }

    public static void explode() {
        play(SoundEvents.ENTITY_GENERIC_EXPLODE, 1.2f, 0.35f);
    }

    public static void launch() {
        play(SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 0.7f);
    }

    public static void cashOut() {
        play(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 1.2f, 1.0f);
        play(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.2f, 0.7f);
    }

    public static void error() {
        play(SoundEvents.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.6f);
    }
}
