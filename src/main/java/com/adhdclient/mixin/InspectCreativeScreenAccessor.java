package com.adhdclient.mixin;

import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Damit die Inspect-Taste beim Tippen in der Kreativ-Suche nicht das Panel öffnet. */
@Mixin(CreativeInventoryScreen.class)
public interface InspectCreativeScreenAccessor {
    @Accessor("searchBox")
    TextFieldWidget adhd$inspectGetSearchBox();
}
