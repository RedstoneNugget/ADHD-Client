package com.adhdclient.casino.core.blackjack;

/**
 * Eine Blackjack-Runde gegen den Dealer: Hit, Stand, Double Down.
 *
 * <p>Regeln: Dealer schaut bei Ass/Zehn sofort nach Blackjack ("Peek"),
 * Blackjack zahlt laut {@link BlackjackRules#blackjackPayout()}, Verdoppeln
 * auf jede Zwei-Karten-Hand, kein Split, keine Versicherung.
 * Der Dealer spielt fest nach Regel (zieht bis 17, ggf. auch bei Soft 17).</p>
 */
public final class BlackjackGame {

    public enum Phase { PLAYER_TURN, FINISHED }

    public enum Outcome {
        BLACKJACK, WIN, DEALER_BUST, PUSH, LOSE, BUST, DEALER_BLACKJACK;

        public boolean isWin() {
            return this == BLACKJACK || this == WIN || this == DEALER_BUST;
        }

        public String translationKey() {
            return "luckycasino.blackjack.outcome." + name().toLowerCase();
        }
    }

    private final Shoe shoe;
    private final BlackjackRules rules;
    private final BlackjackHand player = new BlackjackHand();
    private final BlackjackHand dealer = new BlackjackHand();
    private long stake;
    private boolean doubled;
    private Phase phase = Phase.PLAYER_TURN;
    private Outcome outcome;

    public BlackjackGame(Shoe shoe, BlackjackRules rules, long stake) {
        if (stake <= 0) {
            throw new IllegalArgumentException("Einsatz muss positiv sein");
        }
        this.shoe = shoe;
        this.rules = rules;
        this.stake = stake;
        deal();
    }

    private void deal() {
        player.add(shoe.draw());
        dealer.add(shoe.draw());
        player.add(shoe.draw());
        dealer.add(shoe.draw());
        if (player.isBlackjack() || dealer.isBlackjack()) {
            if (player.isBlackjack() && dealer.isBlackjack()) {
                finish(Outcome.PUSH);
            } else if (player.isBlackjack()) {
                finish(Outcome.BLACKJACK);
            } else {
                finish(Outcome.DEALER_BLACKJACK);
            }
        }
    }

    public void hit() {
        requirePlayerTurn();
        player.add(shoe.draw());
        if (player.isBust()) {
            finish(Outcome.BUST);
        } else if (player.total() == 21) {
            stand();
        }
    }

    public void stand() {
        requirePlayerTurn();
        playDealer();
        int p = player.total();
        int d = dealer.total();
        if (dealer.isBust()) {
            finish(Outcome.DEALER_BUST);
        } else if (p > d) {
            finish(Outcome.WIN);
        } else if (p == d) {
            finish(Outcome.PUSH);
        } else {
            finish(Outcome.LOSE);
        }
    }

    public boolean canDouble() {
        return phase == Phase.PLAYER_TURN && player.size() == 2 && !doubled;
    }

    /**
     * Verdoppeln: Einsatz x2, genau eine weitere Karte, dann automatisch stehen.
     * Den zusätzlichen Einsatz muss der Aufrufer vorher vom Guthaben abbuchen.
     */
    public void doubleDown() {
        if (!canDouble()) {
            throw new IllegalStateException("Verdoppeln nicht möglich");
        }
        stake *= 2;
        doubled = true;
        player.add(shoe.draw());
        if (player.isBust()) {
            finish(Outcome.BUST);
        } else {
            stand();
        }
    }

    private void playDealer() {
        while (dealer.total() < 17 || (rules.dealerHitsSoft17() && dealer.total() == 17 && dealer.isSoft())) {
            dealer.add(shoe.draw());
        }
    }

    private void finish(Outcome result) {
        outcome = result;
        phase = Phase.FINISHED;
    }

    private void requirePlayerTurn() {
        if (phase != Phase.PLAYER_TURN) {
            throw new IllegalStateException("Runde ist vorbei");
        }
    }

    /** Gesamtauszahlung (inkl. Einsatz); 0 solange die Runde läuft oder verloren ist. */
    public long payout() {
        if (outcome == null) {
            return 0;
        }
        return switch (outcome) {
            case BLACKJACK -> stake + (long) Math.floor(stake * rules.blackjackPayout());
            case WIN, DEALER_BUST -> stake * 2;
            case PUSH -> stake;
            case LOSE, BUST, DEALER_BLACKJACK -> 0;
        };
    }

    public BlackjackHand player() {
        return player;
    }

    public BlackjackHand dealer() {
        return dealer;
    }

    public long stake() {
        return stake;
    }

    public boolean isDoubled() {
        return doubled;
    }

    public Phase phase() {
        return phase;
    }

    public boolean isFinished() {
        return phase == Phase.FINISHED;
    }

    public Outcome outcome() {
        return outcome;
    }

    public BlackjackRules rules() {
        return rules;
    }
}
