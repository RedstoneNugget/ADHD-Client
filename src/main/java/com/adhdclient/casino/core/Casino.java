package com.adhdclient.casino.core;

import com.adhdclient.casino.core.blackjack.BlackjackGame;
import com.adhdclient.casino.core.blackjack.BlackjackRules;
import com.adhdclient.casino.core.blackjack.Shoe;
import com.adhdclient.casino.core.coinflip.CoinFlipSeries;
import com.adhdclient.casino.core.coinflip.CoinSide;
import com.adhdclient.casino.core.crash.CrashRound;
import com.adhdclient.casino.core.loot.Cosmetic;
import com.adhdclient.casino.core.loot.LootBox;
import com.adhdclient.casino.core.loot.LootBoxType;
import com.adhdclient.casino.core.loot.LootReward;
import com.adhdclient.casino.core.loot.Rarity;
import com.adhdclient.casino.core.mines.MinesGame;
import com.adhdclient.casino.core.plinko.Plinko;
import com.adhdclient.casino.core.progress.Achievement;
import com.adhdclient.casino.core.progress.GameStats;
import com.adhdclient.casino.core.progress.Missions;
import com.adhdclient.casino.core.progress.PlayerProfile;
import com.adhdclient.casino.core.progress.Records;
import com.adhdclient.casino.core.progress.WinEntry;
import com.adhdclient.casino.core.roulette.Roulette;
import com.adhdclient.casino.core.roulette.RouletteBet;
import com.adhdclient.casino.core.scratch.ScratchTicket;
import com.adhdclient.casino.core.scratch.ScratchTier;
import com.adhdclient.casino.core.slots.SlotMachine;
import com.adhdclient.casino.core.slots.SlotResult;
import com.adhdclient.casino.core.wheel.LuckyWheel;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.LongSupplier;

/**
 * Fassade des Casinos: verbindet Spiellogik, Guthaben, Statistik, Rekorde und Erfolge.
 *
 * <p>Grundsatz: Einsätze werden beim Start abgebucht, Auszahlungen sofort bei
 * Rundenende gutgeschrieben. Animationen in der GUI sind rein optisch – schließt
 * man ein Fenster mitten in der Animation, geht nichts verloren.</p>
 *
 * <p>Enthält keine Minecraft-Klassen und ist vollständig per Unit-Test prüfbar.</p>
 */
public final class Casino {

    /** Rückmeldungen an GUI/Speicherung. */
    public interface Listener {
        default void onAchievement(Achievement achievement) {
        }

        /** Spielstand hat sich geändert (→ speichern). */
        default void onChanged() {
        }

        /** Neues Casino-Level erreicht. */
        default void onLevelUp(int level, long reward) {
        }

        /** Tagesmission erfüllt (Belohnung kann abgeholt werden). */
        default void onMissionComplete(Missions.Def mission) {
        }
    }

    /** Ergebnis einer Slot-Drehung samt Abrechnung. */
    public record SlotSpin(SlotResult result, RoundResult round) {
    }

    /** Ergebnis einer Roulette-Drehung samt Abrechnung. */
    public record RouletteSpin(int pocket, RoundResult round) {
    }

    /** Ergebnis des Glücksrads. */
    public record WheelSpin(int index, LuckyWheel.Segment segment) {
    }

    private PlayerProfile profile;
    private final CasinoRng rng;
    private final LongSupplier clock;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private Shoe shoe;
    private RoundResult lastRound;

    /**
     * @param clock liefert die aktuelle Zeit in Epoch-Millisekunden (für Notkredit, Bestenliste)
     */
    public Casino(PlayerProfile profile, CasinoRng rng, LongSupplier clock) {
        this.profile = profile;
        this.profile.sanitize();
        this.rng = rng;
        this.clock = clock;
    }

    // ------------------------------------------------------------------
    // Allgemeines
    // ------------------------------------------------------------------

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public PlayerProfile profile() {
        return profile;
    }

    public CasinoRng rng() {
        return rng;
    }

    public LuckMode mode() {
        return rng.mode();
    }

    public void setMode(LuckMode mode) {
        rng.setMode(mode);
        changed();
    }

    public long balance() {
        return profile.balance;
    }

    public long now() {
        return clock.getAsLong();
    }

    public boolean canAfford(long amount) {
        return amount > 0 && amount <= profile.balance;
    }

    /** Gespeicherter Einsatz (wird in der GUI ans Guthaben angepasst). */
    public long bet() {
        return profile.bet;
    }

    public void setBet(long bet) {
        long clamped = Math.max(Odds.MIN_BET, Math.min(bet, Odds.MAX_BET));
        if (clamped != profile.bet) {
            profile.bet = clamped;
            changed();
        }
    }

    /** Setzt den kompletten Spielstand zurück (Einstellungen bleiben). */
    public void reset() {
        profile = new PlayerProfile();
        profile.sanitize();
        shoe = null;
        changed();
    }

    /** Ersetzt den Spielstand (z. B. nach dem Laden). */
    public void replaceProfile(PlayerProfile newProfile) {
        newProfile.sanitize();
        profile = newProfile;
        shoe = null;
        changed();
    }

    public void addPlayTime(long ms) {
        if (ms > 0) {
            profile.stats.playTimeMs += ms;
        }
    }

    // ------------------------------------------------------------------
    // Einsatz und Abrechnung
    // ------------------------------------------------------------------

    private boolean withdraw(long amount) {
        if (!canAfford(amount)) {
            return false;
        }
        profile.balance -= amount;
        changed();
        return true;
    }

    /**
     * Rechnet eine Runde ab: zahlt aus (inkl. Bonus), aktualisiert Statistik,
     * Serien, Rekorde, Verlauf und Erfolge.
     *
     * @param stake  bereits abgebuchter Gesamteinsatz
     * @param payout Gesamtauszahlung laut Spiel (inkl. Einsatz)
     * @param free   Freispiel (kein echter Einsatz)
     */
    private RoundResult settle(GameType game, long stake, long payout, boolean free) {
        long profit = payout - stake;
        int bonus = 1;
        if (profit > 0 && profile.bonusMultiplier > 1) {
            bonus = profile.bonusMultiplier;
            long extra = profit * (bonus - 1);
            payout += extra;
            profit += extra;
            profile.bonusMultiplier = 1;
        }
        profile.balance += payout;

        RoundResult.Outcome outcome;
        if (free) {
            outcome = payout > 0 ? RoundResult.Outcome.WIN : RoundResult.Outcome.LOSS;
        } else {
            outcome = profit > 0 ? RoundResult.Outcome.WIN
                    : profit < 0 ? RoundResult.Outcome.LOSS : RoundResult.Outcome.PUSH;
        }

        GameStats gs = profile.stats.get(game);
        gs.rounds++;
        gs.wagered += stake;
        gs.paidOut += payout;
        switch (outcome) {
            case WIN -> gs.wins++;
            case LOSS -> gs.losses++;
            case PUSH -> gs.pushes++;
        }
        gs.biggestWin = Math.max(gs.biggestWin, profit);

        Records r = profile.records;
        if (outcome == RoundResult.Outcome.WIN) {
            r.currentWinStreak++;
            r.longestWinStreak = Math.max(r.longestWinStreak, r.currentWinStreak);
        } else if (outcome == RoundResult.Outcome.LOSS) {
            r.currentWinStreak = 0;
        }
        if (profit > 0) {
            if (profit > r.biggestWin) {
                r.biggestWin = profit;
                r.biggestWinGame = game.name();
            }
            r.addWin(new WinEntry(game.name(), profit, stake, now()));
        }
        recordBalance();

        if (outcome == RoundResult.Outcome.WIN) {
            unlock(Achievement.FIRST_WIN);
        }
        if (r.currentWinStreak >= Achievement.WIN_STREAK) {
            unlock(Achievement.WIN_STREAK_10);
        }
        if (stake >= Achievement.HIGH_ROLLER_BET) {
            unlock(Achievement.HIGH_ROLLER);
        }
        if (profile.balance == 0) {
            unlock(Achievement.BROKE);
        }
        if (profile.stats.gamesPlayed() == GameType.values().length) {
            unlock(Achievement.ALL_ROUNDER);
        }
        if (profile.stats.totalRounds() >= Achievement.REGULAR_ROUNDS) {
            unlock(Achievement.REGULAR);
        }
        progressMissions(game, stake, profit, outcome == RoundResult.Outcome.WIN);
        gainXp(10 + (long) Math.sqrt(Math.max(0, stake)));
        changed();
        lastRound = new RoundResult(game, stake, payout, profit, bonus, outcome);
        return lastRound;
    }

    /** Zuletzt abgerechnete Runde (oder null). */
    public RoundResult lastRound() {
        return lastRound;
    }

    /** Verlauf und Höchststand nach jeder Guthabenänderung. */
    private void recordBalance() {
        profile.balanceHistory.add(profile.balance);
        while (profile.balanceHistory.size() > PlayerProfile.HISTORY_LIMIT) {
            profile.balanceHistory.removeFirst();
        }
        if (profile.balance > profile.records.highestBalance) {
            profile.records.highestBalance = profile.balance;
        }
        if (profile.balance >= Achievement.RICH_BALANCE) {
            unlock(Achievement.RICH);
        }
        if (profile.balance >= Achievement.TYCOON_BALANCE) {
            unlock(Achievement.TYCOON);
        }
    }

    /** Gutschrift außerhalb von Spielrunden (Notkredit, Glücksrad, Lootbox). Verlauf trägt der Aufrufer ein. */
    private void addBonusIncome(long chips) {
        if (chips > 0) {
            profile.balance += chips;
            profile.stats.bonusIncome += chips;
        }
    }

    // ------------------------------------------------------------------
    // Slot-Maschine
    // ------------------------------------------------------------------

    /** Dreht mit Einsatz. Liefert null, wenn das Guthaben nicht reicht. */
    public SlotSpin playSlots(long bet) {
        if (bet < Odds.MIN_BET || bet > Odds.MAX_BET || !withdraw(bet)) {
            return null;
        }
        SlotResult result = SlotMachine.spin(rng);
        RoundResult round = settle(GameType.SLOTS, bet, result.payout(bet), false);
        if (result.isJackpot()) {
            unlock(Achievement.FIRST_JACKPOT);
        }
        return new SlotSpin(result, round);
    }

    /** Freispiel mit festem Einsatz {@link Odds#FREE_SPIN_BET}. Liefert null ohne Freispiele. */
    public SlotSpin playFreeSpin() {
        if (profile.freeSpins <= 0) {
            return null;
        }
        profile.freeSpins--;
        SlotResult result = SlotMachine.spin(rng);
        RoundResult round = settle(GameType.SLOTS, 0, result.payout(Odds.FREE_SPIN_BET), true);
        if (result.isJackpot()) {
            unlock(Achievement.FIRST_JACKPOT);
        }
        return new SlotSpin(result, round);
    }

    // ------------------------------------------------------------------
    // Roulette
    // ------------------------------------------------------------------

    /** Dreht mit allen gesetzten Chips. Liefert null bei leerem Tisch oder zu wenig Guthaben. */
    public RouletteSpin playRoulette(Map<RouletteBet, Long> bets) {
        long total = 0;
        for (long amount : bets.values()) {
            if (amount <= 0) {
                return null;
            }
            total += amount;
        }
        if (total <= 0 || !withdraw(total)) {
            return null;
        }
        int pocket = Roulette.spin(rng);
        RoundResult round = settle(GameType.ROULETTE, total, Roulette.payout(bets, pocket), false);
        for (RouletteBet bet : bets.keySet()) {
            if (bet.kind() == RouletteBet.Kind.STRAIGHT && bet.wins(pocket)) {
                unlock(Achievement.BULLSEYE);
            }
        }
        return new RouletteSpin(pocket, round);
    }

    // ------------------------------------------------------------------
    // Münzwurf
    // ------------------------------------------------------------------

    /** Beginnt eine Serie (bucht den Einsatz ab). Null bei zu wenig Guthaben. */
    public CoinFlipSeries startCoinFlip(long stake) {
        if (stake < Odds.MIN_BET || stake > Odds.MAX_BET || !withdraw(stake)) {
            return null;
        }
        return new CoinFlipSeries(stake);
    }

    /**
     * Wirft die Münze. Bei einem Fehlwurf wird die Serie sofort als verloren abgerechnet.
     *
     * @return Abrechnung bei Verlust, sonst null (Serie läuft weiter)
     */
    public RoundResult flipCoin(CoinFlipSeries series, CoinSide choice) {
        boolean win = series.flip(rng, choice);
        if (win) {
            if (series.streak() > profile.records.longestCoinStreak) {
                profile.records.longestCoinStreak = series.streak();
            }
            if (series.streak() >= Achievement.COIN_STREAK) {
                unlock(Achievement.DOUBLE_TROUBLE);
            }
            changed();
            return null;
        }
        return settle(GameType.COINFLIP, series.stake(), 0, false);
    }

    /** Zahlt den Topf aus. */
    public RoundResult cashOutCoin(CoinFlipSeries series) {
        long pot = series.cashOut();
        return settle(GameType.COINFLIP, series.stake(), pot, false);
    }

    // ------------------------------------------------------------------
    // Blackjack
    // ------------------------------------------------------------------

    public BlackjackRules blackjackRules() {
        return BlackjackRules.of(rng.odds());
    }

    /** Verbleibende Karten im Schlitten (für die Anzeige). */
    public int shoeRemaining() {
        return shoe == null ? blackjackRules().decks() * 52 : shoe.remaining();
    }

    /** Teilt eine neue Hand aus (bucht den Einsatz ab). Null bei zu wenig Guthaben. */
    public BlackjackGame startBlackjack(long stake) {
        if (stake < Odds.MIN_BET || stake > Odds.MAX_BET || !withdraw(stake)) {
            return null;
        }
        BlackjackRules rules = blackjackRules();
        if (shoe == null || shoe.decks() != rules.decks()) {
            shoe = new Shoe(rules.decks(), rng);
        } else if (shoe.needsShuffle()) {
            shoe.shuffle();
        }
        BlackjackGame game = new BlackjackGame(shoe, rules, stake);
        if (game.isFinished()) {
            finishBlackjack(game);
        }
        return game;
    }

    /** @return Abrechnung, falls die Hand damit endet, sonst null */
    public RoundResult blackjackHit(BlackjackGame game) {
        game.hit();
        return game.isFinished() ? finishBlackjack(game) : null;
    }

    public RoundResult blackjackStand(BlackjackGame game) {
        game.stand();
        return finishBlackjack(game);
    }

    /** Verdoppeln – bucht den zweiten Einsatz ab. Null, wenn nicht möglich. */
    public RoundResult blackjackDouble(BlackjackGame game) {
        if (!game.canDouble() || !withdraw(game.stake())) {
            return null;
        }
        game.doubleDown();
        return finishBlackjack(game);
    }

    /** Darf verdoppelt werden (Regel + Guthaben)? */
    public boolean canDouble(BlackjackGame game) {
        return game.canDouble() && canAfford(game.stake());
    }

    private RoundResult finishBlackjack(BlackjackGame game) {
        RoundResult round = settle(GameType.BLACKJACK, game.stake(), game.payout(), false);
        if (game.outcome() == BlackjackGame.Outcome.BLACKJACK) {
            unlock(Achievement.NATURAL);
        }
        return round;
    }

    // ------------------------------------------------------------------
    // Crash
    // ------------------------------------------------------------------

    /** Startet eine Runde (bucht ab). Null bei zu wenig Guthaben. */
    public CrashRound startCrash(long stake, double autoCashOut, long nowMs) {
        if (stake < Odds.MIN_BET || stake > Odds.MAX_BET || !withdraw(stake)) {
            return null;
        }
        CrashRound round = new CrashRound(stake, CrashRound.drawCrashPoint(rng), nowMs, autoCashOut);
        if (!round.isRunning()) {
            finishCrash(round);
        }
        return round;
    }

    /** Pro Frame aufrufen. @return Abrechnung, falls die Runde gerade endet */
    public RoundResult updateCrash(CrashRound round, long nowMs) {
        if (!round.isRunning()) {
            return null;
        }
        round.update(nowMs);
        return round.isRunning() ? null : finishCrash(round);
    }

    /** Cash Out. @return Abrechnung (Gewinn oder – wenn zu spät – Verlust) */
    public RoundResult cashOutCrash(CrashRound round, long nowMs) {
        if (!round.isRunning()) {
            return null;
        }
        round.cashOut(nowMs);
        return finishCrash(round);
    }

    private RoundResult finishCrash(CrashRound round) {
        RoundResult result = settle(GameType.CRASH, round.stake(), round.payout(), false);
        if (round.state() == CrashRound.State.CASHED_OUT) {
            double m = round.cashOutMultiplier();
            if (m > profile.records.bestCrashMultiplier) {
                profile.records.bestCrashMultiplier = m;
            }
            if (m >= Achievement.MOON_MULTIPLIER) {
                unlock(Achievement.TO_THE_MOON);
            }
        }
        return result;
    }

    // ------------------------------------------------------------------
    // Rubbellose
    // ------------------------------------------------------------------

    /** Kauft ein Los. Der Gewinn steht fest und wird sofort gutgeschrieben (Freirubbeln ist Optik). */
    public ScratchTicket buyScratchTicket(ScratchTier tier) {
        if (!withdraw(tier.price())) {
            return null;
        }
        ScratchTicket ticket = ScratchTicket.generate(rng, tier);
        settle(GameType.SCRATCH, tier.price(), ticket.prize(), false);
        if (ticket.winSymbol() != null && ticket.winSymbol().multiplier() >= Achievement.SCRATCH_MULTIPLIER) {
            unlock(Achievement.SCRATCH_LUCK);
        }
        return ticket;
    }

    // ------------------------------------------------------------------
    // Glücksrad (täglich, gratis)
    // ------------------------------------------------------------------

    public boolean canSpinWheel(LocalDate today) {
        return LuckyWheel.isAvailable(profile.lastWheelDay, today);
    }

    /** Dreht das Rad und schreibt den Preis gut. Null, wenn heute schon gedreht wurde. */
    public WheelSpin spinWheel(LocalDate today) {
        if (!canSpinWheel(today)) {
            return null;
        }
        profile.lastWheelDay = today.toString();
        profile.stats.wheelSpins++;
        int index = LuckyWheel.spin(rng);
        LuckyWheel.Segment seg = LuckyWheel.SEGMENTS.get(index);
        switch (seg.type()) {
            case CHIPS -> {
                addBonusIncome(seg.amount());
                recordBalance();
            }
            case BONUS -> profile.bonusMultiplier = Math.max(profile.bonusMultiplier, seg.amount());
            case FREE_SPINS -> profile.freeSpins += seg.amount();
        }
        unlock(Achievement.WHEEL_SPIN);
        changed();
        return new WheelSpin(index, seg);
    }

    // ------------------------------------------------------------------
    // Lootboxen & Kosmetik
    // ------------------------------------------------------------------

    /** Kauft und öffnet eine Kiste. Null bei zu wenig Guthaben. */
    public LootReward openLootBox(LootBoxType type) {
        if (!withdraw(type.price())) {
            return null;
        }
        profile.stats.shopSpending += type.price();
        profile.stats.lootBoxesOpened++;
        LootReward reward = LootBox.roll(rng, type, profile.cosmetics);
        if (reward.isNewCosmetic()) {
            profile.cosmetics.add(reward.cosmetic().key());
        }
        addBonusIncome(reward.chips());
        recordBalance();
        if (reward.rarity() == Rarity.LEGENDARY) {
            unlock(Achievement.LEGENDARY_LOOT);
        }
        if (ownedCosmeticCount() >= Achievement.COLLECTOR_ITEMS) {
            unlock(Achievement.COLLECTOR);
        }
        changed();
        return reward;
    }

    /** Anzahl besessener Gegenstände (ohne Standard-Rahmen). */
    public int ownedCosmeticCount() {
        int n = 0;
        for (String key : profile.cosmetics) {
            Cosmetic c = Cosmetic.byKey(key);
            if (c != null && !c.isDefault()) {
                n++;
            }
        }
        return n;
    }

    public boolean owns(Cosmetic cosmetic) {
        return profile.cosmetics.contains(cosmetic.key());
    }

    /** Legt einen Titel/Rahmen an. Ein angelegter Titel wird bei erneutem Klick abgelegt. */
    public boolean equip(Cosmetic cosmetic) {
        if (!owns(cosmetic)) {
            return false;
        }
        if (cosmetic.type() == Cosmetic.Type.TITLE) {
            profile.equippedTitle = profile.equippedTitle.equals(cosmetic.key()) ? "" : cosmetic.key();
        } else {
            profile.equippedFrame = cosmetic.key();
        }
        changed();
        return true;
    }

    public Cosmetic equippedTitle() {
        return Cosmetic.byKey(profile.equippedTitle);
    }

    public Cosmetic equippedFrame() {
        Cosmetic c = Cosmetic.byKey(profile.equippedFrame);
        return c == null ? Cosmetic.FRAME_CLASSIC : c;
    }

    // ------------------------------------------------------------------
    // Plinko
    // ------------------------------------------------------------------

    /** Lässt eine Kugel fallen (bucht ab und rechnet sofort ab). Null bei zu wenig Guthaben. */
    public Plinko.Drop playPlinko(long stake, Plinko.Risk risk) {
        if (stake < Odds.MIN_BET || stake > Odds.MAX_BET || !withdraw(stake)) {
            return null;
        }
        Plinko.Drop drop = Plinko.drop(rng, risk, stake);
        settle(GameType.PLINKO, stake, drop.payout(), false);
        if (drop.multiplier() >= Achievement.PLINKO_MULTIPLIER) {
            unlock(Achievement.PLINKO_PRO);
        }
        return drop;
    }

    // ------------------------------------------------------------------
    // Minenfeld
    // ------------------------------------------------------------------

    /** Startet eine Runde (bucht ab). Null bei zu wenig Guthaben. */
    public MinesGame startMines(long stake, int mines) {
        if (stake < Odds.MIN_BET || stake > Odds.MAX_BET || !withdraw(stake)) {
            return null;
        }
        return new MinesGame(rng, stake, mines);
    }

    /** Deckt ein Feld auf. @return Abrechnung, falls die Runde endet (Mine oder alles gefunden), sonst null */
    public RoundResult revealMine(MinesGame game, int tile) {
        boolean safe = game.reveal(tile);
        if (!safe) {
            return settle(GameType.MINES, game.stake(), 0, false);
        }
        return game.isFinished() ? finishMines(game) : null;
    }

    public RoundResult cashOutMines(MinesGame game) {
        game.cashOut();
        return finishMines(game);
    }

    private RoundResult finishMines(MinesGame game) {
        RoundResult r = settle(GameType.MINES, game.stake(), game.payout(), false);
        if (game.currentMultiplier() >= Achievement.MINES_MULTIPLIER) {
            unlock(Achievement.MINESWEEPER);
        }
        return r;
    }

    // ------------------------------------------------------------------
    // Level & XP
    // ------------------------------------------------------------------

    /** XP, die für den Aufstieg von {@code level} auf {@code level + 1} nötig sind. */
    public static long xpForNextLevel(int level) {
        return 100 + 50L * (level - 1);
    }

    /** Belohnung beim Erreichen von {@code level}. */
    public static long levelReward(int level) {
        return 50L * level;
    }

    private void gainXp(long amount) {
        profile.xp += amount;
        while (profile.xp >= xpForNextLevel(profile.level)) {
            profile.xp -= xpForNextLevel(profile.level);
            profile.level++;
            long reward = levelReward(profile.level);
            addBonusIncome(reward);
            recordBalance();
            for (Listener l : listeners) {
                l.onLevelUp(profile.level, reward);
            }
            if (profile.level >= Achievement.LEVEL_GOAL) {
                unlock(Achievement.LEVEL_10);
            }
        }
    }

    // ------------------------------------------------------------------
    // Tagesmissionen
    // ------------------------------------------------------------------

    /** Heutiges Datum laut Casino-Uhr. */
    public LocalDate today() {
        return Instant.ofEpochMilli(now()).atZone(ZoneId.systemDefault()).toLocalDate();
    }

    /** Die heutigen Missionen (werden bei Tageswechsel neu gezogen). */
    public List<Missions.Progress> missions() {
        ensureMissions();
        return profile.missions;
    }

    private void ensureMissions() {
        LocalDate today = today();
        if (!today.toString().equals(profile.missionDay) || profile.missions.size() != Missions.PER_DAY) {
            profile.missionDay = today.toString();
            profile.missions = new ArrayList<>();
            for (Missions.Def d : Missions.forDay(today)) {
                profile.missions.add(new Missions.Progress(d.id()));
            }
            changed();
        }
    }

    private void progressMissions(GameType game, long stake, long profit, boolean win) {
        ensureMissions();
        for (Missions.Progress mp : profile.missions) {
            Missions.Def def = Missions.byId(mp.id);
            if (def == null || mp.claimed || mp.progress >= def.target()) {
                continue;
            }
            long add = Missions.progressFor(def, game, stake, profit, win);
            if (add > 0) {
                mp.progress = Math.min(def.target(), mp.progress + add);
                if (mp.progress >= def.target()) {
                    for (Listener l : listeners) {
                        l.onMissionComplete(def);
                    }
                }
            }
        }
    }

    /** Holt die Belohnung einer erfüllten Mission ab. */
    public boolean claimMission(String id) {
        for (Missions.Progress mp : missions()) {
            Missions.Def def = Missions.byId(mp.id);
            if (mp.id.equals(id) && def != null && !mp.claimed && mp.progress >= def.target()) {
                mp.claimed = true;
                profile.missionsCompleted++;
                addBonusIncome(def.reward());
                recordBalance();
                if (profile.missionsCompleted >= Achievement.MISSIONS_GOAL) {
                    unlock(Achievement.DILIGENT);
                }
                changed();
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Notkredit
    // ------------------------------------------------------------------

    /** Restliche Wartezeit in ms (0 = sofort verfügbar, sofern Guthaben 0). */
    public long loanCooldownRemaining() {
        long now = now();
        if (profile.lastLoanAt <= 0 || now < profile.lastLoanAt) {
            return 0; // nie genommen oder Systemuhr zurückgestellt
        }
        return Math.max(0, profile.lastLoanAt + Odds.LOAN_COOLDOWN_MS - now);
    }

    public boolean canTakeLoan() {
        return profile.balance == 0 && loanCooldownRemaining() == 0;
    }

    public boolean takeLoan() {
        if (!canTakeLoan()) {
            return false;
        }
        profile.lastLoanAt = now();
        profile.stats.loansTaken++;
        addBonusIncome(Odds.LOAN_AMOUNT);
        recordBalance();
        unlock(Achievement.EMERGENCY_LOAN);
        changed();
        return true;
    }

    // ------------------------------------------------------------------
    // Erfolge
    // ------------------------------------------------------------------

    public boolean isUnlocked(Achievement achievement) {
        return profile.achievements.containsKey(achievement.id());
    }

    /** Zeitpunkt der Freischaltung oder 0. */
    public long unlockedAt(Achievement achievement) {
        Long t = profile.achievements.get(achievement.id());
        return t == null ? 0 : t;
    }

    public int unlockedCount() {
        return profile.achievements.size();
    }

    private void unlock(Achievement achievement) {
        if (profile.achievements.putIfAbsent(achievement.id(), now()) == null) {
            for (Listener l : listeners) {
                l.onAchievement(achievement);
            }
        }
    }

    private void changed() {
        for (Listener l : listeners) {
            l.onChanged();
        }
    }
}
