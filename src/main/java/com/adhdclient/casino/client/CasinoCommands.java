package com.adhdclient.casino.client;

import com.mojang.brigadier.CommandDispatcher;
import com.adhdclient.casino.client.gui.Fmt;
import com.adhdclient.casino.core.Casino;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/**
 * Client-seitige Chat-Befehle: /casino open | balance | reset.
 * Werden komplett lokal ausgeführt und nie an den Server geschickt.
 */
public final class CasinoCommands {

    private CasinoCommands() {
    }

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandRegistryAccess access) {
        dispatcher.register(literal("casino").requires(src -> LuckyCasinoClient.enabled)
                .executes(ctx -> help(ctx.getSource()))
                .then(literal("help").executes(ctx -> help(ctx.getSource())))
                .then(literal("open").executes(ctx -> {
                    LuckyCasinoClient.requestOpen();
                    return 1;
                }))
                .then(literal("balance").executes(ctx -> {
                    Casino c = LuckyCasinoClient.casino();
                    ctx.getSource().sendFeedback(prefix().append(
                            Text.translatable("luckycasino.command.balance", Fmt.chips(c.balance())).formatted(Formatting.GOLD)));
                    if (c.balance() == 0) {
                        ctx.getSource().sendFeedback(prefix().append(c.canTakeLoan()
                                ? Text.translatable("luckycasino.command.loan_ready").formatted(Formatting.YELLOW)
                                : Text.translatable("luckycasino.command.loan_wait", Fmt.duration(c.loanCooldownRemaining()))
                                .formatted(Formatting.GRAY)));
                    }
                    return 1;
                }))
                .then(literal("reset")
                        .executes(ctx -> {
                            Text confirm = Text.translatable("luckycasino.command.reset.click").formatted(Formatting.RED, Formatting.UNDERLINE)
                                    .styled(s -> s.withClickEvent(new ClickEvent.SuggestCommand("/casino reset confirm"))
                                            .withHoverEvent(new HoverEvent.ShowText(Text.literal("/casino reset confirm"))));
                            ctx.getSource().sendFeedback(prefix()
                                    .append(Text.translatable("luckycasino.command.reset.warn").formatted(Formatting.YELLOW))
                                    .append(" ").append(confirm));
                            return 1;
                        })
                        .then(literal("confirm").executes(ctx -> {
                            ClientSession.clear();
                            LuckyCasinoClient.casino().reset();
                            LuckyCasinoClient.saveNow();
                            ctx.getSource().sendFeedback(prefix().append(
                                    Text.translatable("luckycasino.command.reset.done").formatted(Formatting.GREEN)));
                            return 1;
                        }))));
    }

    private static int help(FabricClientCommandSource source) {
        source.sendFeedback(prefix().append(Text.translatable("luckycasino.command.help").formatted(Formatting.GRAY)));
        return 1;
    }

    private static net.minecraft.text.MutableText prefix() {
        return Text.literal("[LuckyCasino] ").formatted(Formatting.GOLD);
    }
}
