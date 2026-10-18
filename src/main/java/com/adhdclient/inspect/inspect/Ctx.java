package com.adhdclient.inspect.inspect;

import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.world.World;

/** Woher die Daten kommen und womit sie (de)serialisiert werden. */
public record Ctx(World world, RegistryWrapper.WrapperLookup registries, Source source, boolean selfPlayer) {

    public enum Source {
        CLIENT_SP("Client – lade Serverdaten …", "Client – loading server data …"),
        CLIENT_MP("Mehrspieler: nur übertragene Daten", "Multiplayer: only data the server sends"),
        SERVER_SP("Integrierter Server (vollständig)", "Integrated server (complete)"),
        OP_QUERY("Server-Abfrage (OP, wie F3+I)", "Server query (OP, like F3+I)");

        private final String de;
        private final String en;

        Source(String de, String en) {
            this.de = de;
            this.en = en;
        }

        public String label() {
            return com.adhdclient.gui.L.t(de, en);
        }
    }

    /** Client-Daten auf einem fremden Server – hier fehlen ggf. Daten. */
    public boolean mpClient() {
        return source == Source.CLIENT_MP;
    }

    public boolean serverSide() {
        return source == Source.SERVER_SP;
    }

    public RegistryOps<NbtElement> ops() {
        return registries.getOps(NbtOps.INSTANCE);
    }

    public String dimension() {
        return world.getRegistryKey().getValue().toString();
    }
}
