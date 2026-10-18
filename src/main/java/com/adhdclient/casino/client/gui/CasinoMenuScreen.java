package com.adhdclient.casino.client.gui;

import com.adhdclient.casino.client.CasinoSounds;
import com.adhdclient.casino.client.LuckyCasinoClient;
import com.adhdclient.casino.client.gui.widget.TileWidget;
import com.adhdclient.casino.core.Casino;
import com.adhdclient.casino.core.Odds;
import com.adhdclient.casino.core.loot.Cosmetic;
import com.adhdclient.casino.core.progress.Achievement;
import com.adhdclient.casino.core.progress.Missions;
import com.adhdclient.casino.core.progress.PlayerProfile;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.function.Supplier;

/** Hauptmenü des Casinos (Taste K). */
public class CasinoMenuScreen extends CasinoScreen {

    private static final int COLS = 4;
    private static final int TILE_H = 32;
    private static final int GAP = 5;

    private ButtonWidget loanButton;
    private int tileIndex;
    private int tileW;

    public CasinoMenuScreen(Screen parent) {
        super(Text.translatable("luckycasino.title"), parent);
    }

    @Override
    protected void initContent() {
        tileIndex = 0;
        tileW = (pw - 20 - (COLS - 1) * GAP) / COLS;

        // Spiele
        tile("luckycasino.game.slots", "minecraft:diamond", 0xFF4AE0E0, sub("luckycasino.menu.sub.slots"), gray(), () -> new SlotsScreen(this));
        tile("luckycasino.game.roulette", "minecraft:clock", 0xFFE04A4A, sub("luckycasino.menu.sub.roulette"), gray(), () -> new RouletteScreen(this));
        tile("luckycasino.game.coinflip", "minecraft:gold_nugget", 0xFFFFD34D, sub("luckycasino.menu.sub.coinflip"), gray(), () -> new CoinFlipScreen(this));
        tile("luckycasino.game.blackjack", "minecraft:paper", 0xFFE0E0E0, sub("luckycasino.menu.sub.blackjack"), gray(), () -> new BlackjackScreen(this));
        tile("luckycasino.game.crash", "minecraft:firework_rocket", 0xFFFF8C3A, sub("luckycasino.menu.sub.crash"), gray(), () -> new CrashScreen(this));
        tile("luckycasino.game.scratch", "minecraft:brush", 0xFFC0C0C0, sub("luckycasino.menu.sub.scratch"), gray(), () -> new ScratchScreen(this));
        tile("luckycasino.game.plinko", "minecraft:slime_ball", 0xFFFF5AA0, sub("luckycasino.menu.sub.plinko"), gray(), () -> new PlinkoScreen(this));
        tile("luckycasino.game.mines", "minecraft:tnt", 0xFFD02020, sub("luckycasino.menu.sub.mines"), gray(), () -> new MinesScreen(this));
        // Extras
        tile("luckycasino.menu.wheel", "minecraft:sunflower", 0xFFFFB000, this::wheelSubtitle,
                () -> casino.canSpinWheel(LocalDate.now()) ? Ui.GREEN : Ui.GRAY, () -> new WheelScreen(this));
        tile("luckycasino.menu.missions", "minecraft:writable_book", 0xFF6FC3FF, this::missionSubtitle,
                () -> claimableMissions() > 0 ? Ui.GREEN : Ui.GRAY, () -> new MissionsScreen(this));
        tile("luckycasino.menu.loot", "minecraft:chest", 0xFFB45CFF, sub("luckycasino.menu.sub.loot_buy"), gray(), () -> new LootBoxScreen(this));
        tile("luckycasino.loot.collection", "minecraft:name_tag", 0xFFB45CFF,
                () -> Text.translatable("luckycasino.menu.sub.loot", casino.ownedCosmeticCount(), Cosmetic.values().length - 1),
                gray(), () -> new CollectionScreen(this));
        tile("luckycasino.menu.achievements", "minecraft:golden_apple", 0xFFFFD34D,
                () -> Text.translatable("luckycasino.menu.sub.achievements", casino.unlockedCount(), Achievement.values().length),
                gray(), () -> new AchievementsScreen(this));
        tile("luckycasino.menu.stats", "minecraft:knowledge_book", 0xFF4A9BFF,
                () -> Text.translatable("luckycasino.menu.sub.stats", Fmt.percent(casino.profile().stats.winRate())),
                gray(), () -> new StatsScreen(this));
        tile("luckycasino.menu.records", "minecraft:gold_block", 0xFFFFAA00,
                () -> Text.translatable("luckycasino.menu.sub.records", Fmt.chips(casino.profile().records.highestBalance)),
                gray(), () -> new RecordsScreen(this));
        tile("luckycasino.menu.settings", "minecraft:comparator", 0xFF9A9A9A,
                () -> Text.translatable(casino.mode().translationKey()), gray(), () -> new SettingsScreen(this));

        loanButton = addDrawableChild(ButtonWidget.builder(Text.translatable("luckycasino.loan.button", Fmt.chips(Odds.LOAN_AMOUNT)), b -> {
            if (casino.takeLoan()) {
                CasinoSounds.win();
            }
            updateLoanButton();
        }).dimensions(left + pw / 2 - 80, top + ph - 22, 160, 18).build());
        updateLoanButton();
    }

    private static Supplier<Text> sub(String key) {
        return () -> Text.translatable(key);
    }

    private static Supplier<Integer> gray() {
        return () -> Ui.GRAY;
    }

    private void tile(String nameKey, String icon, int accent, Supplier<Text> sub, Supplier<Integer> subColor, Supplier<Screen> target) {
        int n = tileIndex++;
        int x = left + 10 + (n % COLS) * (tileW + GAP);
        int y = top + HEADER_H + 18 + (n / COLS) * (TILE_H + GAP);
        addDrawableChild(new TileWidget(x, y, tileW, TILE_H, Text.translatable(nameKey), Icons.of(icon),
                sub, subColor, accent, () -> client.setScreen(target.get())));
    }

    private Text wheelSubtitle() {
        if (casino.canSpinWheel(LocalDate.now())) {
            return Text.translatable("luckycasino.menu.sub.wheel_ready");
        }
        return Text.translatable("luckycasino.menu.sub.wheel_wait", Fmt.duration(untilMidnightMs()));
    }

    private int claimableMissions() {
        int n = 0;
        for (Missions.Progress mp : casino.missions()) {
            Missions.Def d = Missions.byId(mp.id);
            if (d != null && !mp.claimed && mp.progress >= d.target()) {
                n++;
            }
        }
        return n;
    }

    private Text missionSubtitle() {
        if (claimableMissions() > 0) {
            return Text.translatable("luckycasino.menu.sub.missions_ready");
        }
        long done = casino.missions().stream().filter(m -> m.claimed).count();
        return Text.translatable("luckycasino.menu.sub.missions", done, Missions.PER_DAY);
    }

    static long untilMidnightMs() {
        LocalDateTime now = LocalDateTime.now();
        return Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay()).toMillis();
    }

    private void updateLoanButton() {
        if (loanButton == null) {
            return;
        }
        loanButton.visible = casino.balance() == 0;
        loanButton.active = casino.canTakeLoan();
        loanButton.setMessage(casino.canTakeLoan()
                ? Text.translatable("luckycasino.loan.button", Fmt.chips(Odds.LOAN_AMOUNT))
                : Text.translatable("luckycasino.loan.wait", Fmt.duration(casino.loanCooldownRemaining())));
    }

    @Override
    public void tick() {
        updateLoanButton();
    }

    @Override
    protected void renderContent(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int y = top + HEADER_H + 5;
        Cosmetic title = casino.equippedTitle();
        Text greet = title != null
                ? Text.translatable("luckycasino.menu.greeting_title", Text.translatable(title.translationKey()).withColor(title.color()))
                : Text.translatable("luckycasino.menu.greeting");
        ctx.drawText(textRenderer, greet, left + 10, y, Ui.WHITE, true);

        // Level-Abzeichen mit XP-Balken
        PlayerProfile p = casino.profile();
        int barW = 70;
        int bx = left + pw - 10 - barW;
        ctx.fill(bx, y + 2, bx + barW, y + 7, 0xFF202020);
        ctx.fill(bx, y + 2, bx + (int) (barW * Math.min(1.0, p.xp / (double) Casino.xpForNextLevel(p.level))), y + 7, 0xFF7CFC6A);
        Ui.right(ctx, textRenderer, Text.translatable("luckycasino.level", p.level), bx - 4, y, 0xFF7CFC6A);

        // Fußzeile: Pleite-Hinweis oder Status
        int fy = top + ph - 36;
        if (casino.balance() == 0) {
            Ui.centered(ctx, textRenderer, Text.translatable("luckycasino.loan.broke"), left + pw / 2, fy, Ui.RED);
        } else {
            Ui.centered(ctx, textRenderer, statusLine(), left + pw / 2, fy, 0xFFB8E0B8);
            Ui.centered(ctx, textRenderer, Text.translatable("luckycasino.menu.tip",
                            Text.literal(com.adhdclient.gui.HotKey.name(LuckyCasinoClient.config().adhdOpenKey))),
                    left + pw / 2, fy + 13, Ui.DARK_GRAY);
        }
    }

    private Text statusLine() {
        var p = casino.profile();
        if (p.bonusMultiplier > 1 && p.freeSpins > 0) {
            return Text.translatable("luckycasino.menu.status_both", p.bonusMultiplier, p.freeSpins);
        }
        if (p.bonusMultiplier > 1) {
            return Text.translatable("luckycasino.menu.status_bonus", p.bonusMultiplier);
        }
        if (p.freeSpins > 0) {
            return Text.translatable("luckycasino.menu.status_spins", p.freeSpins);
        }
        return Text.translatable("luckycasino.menu.status_none");
    }
}
