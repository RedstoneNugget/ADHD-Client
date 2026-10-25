package com.adhdclient.mixin;

import com.adhdclient.emoji.ChatEmojiClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Chat-Emojis (Option „beim Senden“): eigene Nachrichten werden vor dem Senden umgewandelt. */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class ChatEmojiSendMixin {

    @ModifyVariable(method = "sendChatMessage", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private String adhd_client$emojisOnSend(String content) {
        return ChatEmojiClient.enabled && ChatEmojiClient.onSend ? ChatEmojiClient.replace(content) : content;
    }
}
