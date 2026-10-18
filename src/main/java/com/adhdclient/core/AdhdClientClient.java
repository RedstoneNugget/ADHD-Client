package com.adhdclient.core;

import com.adhdclient.basefinder.BaseFinderClient;
import com.adhdclient.gui.ClickGuiScreen;
import com.adhdclient.blockesp.BlockESPClient;
import com.adhdclient.esp.EspClient;
import com.adhdclient.blink.BlinkClient;
import com.adhdclient.freecam.FreeCamClient;
import com.adhdclient.inspect.InspectClient;
import com.adhdclient.packettools.PacketToolsClient;
import com.adhdclient.freelook.FreeLookClient;
import com.adhdclient.rtpq.RtpqInvseeClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Einstiegspunkt des ADHD Client: startet die einzelnen Module (Base Finder, Block ESP, ESP, RTPQ Invsee, …)
 * und öffnet über eine Taste das zentrale Menü, in dem jedes Modul einzeln an/aus geschaltet wird.
 */
public final class AdhdClientClient implements ClientModInitializer {

    public static final String MOD_ID = "adhd_client";
    public static final String MOD_NAME = "ADHD Client";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

    public static KeyBinding menuKey;
    public static KeyBinding.Category category;

    @Override
    public void onInitializeClient() {
        AdhdModuleState.load();

        category = KeyBinding.Category.create(Identifier.of(MOD_ID, "main"));
        menuKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.adhd_client.menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_CONTROL, category));

        new BaseFinderClient().onInitializeClient();
        new BlockESPClient().onInitializeClient();
        new EspClient().onInitializeClient();
        new RtpqInvseeClient().onInitializeClient();
        new FreeCamClient().onInitializeClient();
        new FreeLookClient().onInitializeClient();
        new BlinkClient().onInitializeClient();
        new InspectClient().onInitializeClient();
        new PacketToolsClient().onInitializeClient();
        new com.adhdclient.casino.client.LuckyCasinoClient().onInitializeClient();

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (menuKey.wasPressed()) {
                if (mc.currentScreen == null) mc.setScreen(new ClickGuiScreen(null));
            }
        });

        LOGGER.info("{} geladen – Rechte Strg öffnet das Menü", MOD_NAME);
    }
}
