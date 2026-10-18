package com.adhdclient.mixin;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Liefert den Slot unter dem Mauszeiger in Inventar-/Container-Screens. */
@Mixin(HandledScreen.class)
public interface InspectHandledScreenAccessor {
    @Accessor("focusedSlot")
    Slot adhd$inspectGetFocusedSlot();
}
