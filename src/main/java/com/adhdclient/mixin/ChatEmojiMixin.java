package com.adhdclient.mixin;

import com.adhdclient.emoji.ChatEmojiClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Chat-Emojis: angezeigte Chat-Zeilen bekommen Symbole statt :namen:. */
@Mixin(ChatHud.class)
public abstract class ChatEmojiMixin {

    @ModifyVariable(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Text adhd_client$emojis(Text message) {
        return ChatEmojiClient.enabled ? ChatEmojiClient.replace(message) : message;
    }
}
