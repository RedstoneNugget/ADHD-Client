package com.adhdclient.butterfly;

import com.adhdclient.gui.L;

/** Verfügbare Looks für Waffen in der Hand. Weitere Looks kommen hier dazu. */
public enum ButterflyLook {
    BUTTERFLY("butterfly", "Butterfly-Messer", "Butterfly knife");

    public final String id;
    private final String de;
    private final String en;

    ButterflyLook(String id, String de, String en) {
        this.id = id;
        this.de = de;
        this.en = en;
    }

    public String title() {
        return L.t(de, en);
    }
}
