package io.github.center2055.coinflipxd.game;

import io.github.center2055.coinflipxd.util.PayoutCalculator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.random.RandomGenerator;

/**
 * All money movements of the plugin: stakes, payouts, tax and refunds. Bukkit independent so it can be tested
 * directly, and synchronized so it is safe on Folia, where commands and GUIs run on several threads.
 *
 * <p>Every stake is written to the escrow store the moment it is withdrawn and removed only after it has been
 * paid out or refunded. Whatever is still in escrow at startup (a crash, or a shutdown while the economy plugin
 * was already gone) is refunded by {@link #recover}.
 */
public final class BetBook {

    /** Moves money. Implemented with Vault. */
    public interface Bank {
        boolean withdraw(UUID player, double amount);

        boolean deposit(UUID player, double amount);
    }

    /** Durable record of stakes the plugin is holding. */
    public interface Escrow {
        void hold(UUID gameId, UUID player, double amount);

        void release(UUID gameId, UUID player);

        void releaseGame(UUID gameId);

        List<Entry> pending();

        record Entry(UUID gameId, UUID player, double amount) {
        }
    }

    /** Tax and rounding rules, re-read on every payout so /cf reload applies immediately. */
    public record PayoutRules(boolean taxEnabled, double taxPercent, UUID taxRecipient, boolean wholeNumbers) {
    }

    public enum Status {
        OK, ALREADY_ACTIVE, TARGET_BUSY, INSUFFICIENT_FUNDS, NOT_FOUND, SELF, NOT_TARGET, EXPIRED, PAYOUT_FAILED
    }

    public record CreateResult(Status status, CoinFlipGame game) {
    }

    /** Result of accepting a game. On OK, winner/loser/payout are set. */
    public record Outcome(Status status, CoinFlipGame game, UUID winner, UUID loser, PayoutCalculator.Payout payout,
                          boolean taxDelivered, Refund refund) {
        static Outcome of(Status status, CoinFlipGame game) {
            return new Outcome(status, game, null, null, null, true, null);
        }
    }

    /** A game that ended without a winner. {@code refunded} is false if the stake stays in escrow for later. */
    public record Refund(CoinFlipGame game, boolean refunded) {
    }

    private final Map<UUID, CoinFlipGame> games = new HashMap<>();
    private final Map<UUID, UUID> byCreator = new HashMap<>();
    private final Map<UUID, UUID> byTarget = new HashMap<>();
    private final Bank bank;
    private final Escrow escrow;
    private final RandomGenerator random;
    private final Supplier<PayoutRules> rules;
    private final Logger logger;

    public BetBook(Bank bank, Escrow escrow, RandomGenerator random, Supplier<PayoutRules> rules, Logger logger) {
        this.bank = bank;
        this.escrow = escrow;
        this.random = random;
        this.rules = rules;
        this.logger = logger;
    }

    // ------------------------------------------------------------------ queries

    public synchronized Optional<CoinFlipGame> get(UUID gameId) {
        return Optional.ofNullable(games.get(gameId));
    }

    public synchronized Optional<CoinFlipGame> byCreator(UUID creator) {
        UUID id = byCreator.get(creator);
        return id == null ? Optional.empty() : Optional.ofNullable(games.get(id));
    }

    public synchronized Optional<CoinFlipGame> byTarget(UUID target) {
        UUID id = byTarget.get(target);
        return id == null ? Optional.empty() : Optional.ofNullable(games.get(id));
    }

    public synchronized List<CoinFlipGame> publicGames() {
        List<CoinFlipGame> list = new ArrayList<>();
        for (CoinFlipGame game : games.values()) {
            if (game.type() == GameType.PUBLIC && game.state() == GameState.PENDING) {
                list.add(game);
            }
        }
        list.sort(Comparator.comparingLong(CoinFlipGame::createdAt));
        return list;
    }

    public synchronized int size() {
        return games.size();
    }

    // ------------------------------------------------------------------ create

    /** Takes the stake from the creator and opens a game. */
    public synchronized CreateResult create(UUID creator, GameType type, UUID target, double amount,
                                            long now, long ttlMillis, boolean onePerPlayer) {
        if (onePerPlayer && byCreator.containsKey(creator)) {
            return new CreateResult(Status.ALREADY_ACTIVE, null);
        }
        if (type == GameType.PRIVATE) {
            if (target == null || target.equals(creator)) {
                return new CreateResult(Status.SELF, null);
            }
            if (byTarget.containsKey(target)) {
                return new CreateResult(Status.TARGET_BUSY, null);
            }
        }
        if (!(amount > 0) || Double.isInfinite(amount)) {
            return new CreateResult(Status.INSUFFICIENT_FUNDS, null);
        }
        if (!bank.withdraw(creator, amount)) {
            return new CreateResult(Status.INSUFFICIENT_FUNDS, null);
        }
        CoinFlipGame game = new CoinFlipGame(UUID.randomUUID(), creator, type,
                type == GameType.PRIVATE ? target : null, amount, now, now + ttlMillis);
        escrow.hold(game.id(), creator, amount);
        games.put(game.id(), game);
        // With onePerPlayer off a creator can have several games; this map then points at the newest one.
        byCreator.put(creator, game.id());
        if (type == GameType.PRIVATE) {
            byTarget.put(target, game.id());
        }
        return new CreateResult(Status.OK, game);
    }

    // ------------------------------------------------------------------ accept and resolve

    /** Takes the acceptor's stake, flips the coin and pays the winner, all in one step. */
    public synchronized Outcome accept(UUID acceptor, UUID gameId, long now) {
        CoinFlipGame game = games.get(gameId);
        if (game == null || game.state() != GameState.PENDING) {
            return Outcome.of(Status.NOT_FOUND, game);
        }
        if (game.creator().equals(acceptor)) {
            return Outcome.of(Status.SELF, game);
        }
        if (game.type() == GameType.PRIVATE && !acceptor.equals(game.target().orElse(null))) {
            return Outcome.of(Status.NOT_TARGET, game);
        }
        if (game.isExpired(now)) {
            Refund refund = close(game, GameState.EXPIRED);
            return new Outcome(Status.EXPIRED, game, null, null, null, true, refund);
        }
        double amount = game.amount();
        if (!bank.withdraw(acceptor, amount)) {
            return Outcome.of(Status.INSUFFICIENT_FUNDS, game);
        }
        escrow.hold(game.id(), acceptor, amount);
        game.acceptor(acceptor);
        game.state(GameState.RESOLVING);
        unindex(game);

        PayoutRules r = rules.get();
        PayoutCalculator.Payout payout = PayoutCalculator.calculate(amount, r.taxEnabled(), r.taxPercent(), r.wholeNumbers());
        boolean creatorWins = random.nextBoolean();
        UUID winner = creatorWins ? game.creator() : acceptor;
        UUID loser = creatorWins ? acceptor : game.creator();

        if (!bank.deposit(winner, payout.winnings())) {
            // Paying the winner failed: give both stakes back. Any refund that also fails stays in escrow.
            refundStake(game, game.creator());
            refundStake(game, acceptor);
            game.state(GameState.CANCELED);
            logger.severe("Could not pay " + payout.winnings() + " to the winner of coinflip " + game.id()
                    + "; both stakes were refunded.");
            return new Outcome(Status.PAYOUT_FAILED, game, winner, loser, payout, true, null);
        }
        boolean taxDelivered = true;
        if (payout.taxAmount() > 0 && r.taxRecipient() != null) {
            taxDelivered = bank.deposit(r.taxRecipient(), payout.taxAmount());
            if (!taxDelivered) {
                logger.warning("Could not deposit " + payout.taxAmount() + " tax to " + r.taxRecipient()
                        + " for coinflip " + game.id());
            }
        }
        escrow.releaseGame(game.id());
        game.state(GameState.COMPLETED);
        return new Outcome(Status.OK, game, winner, loser, payout, taxDelivered, null);
    }

    // ------------------------------------------------------------------ cancel, expire, shutdown

    /** Ends a pending game and refunds the creator. Empty if the game is no longer pending. */
    public synchronized Optional<Refund> cancel(UUID gameId) {
        CoinFlipGame game = games.get(gameId);
        if (game == null || game.state() != GameState.PENDING) {
            return Optional.empty();
        }
        return Optional.of(close(game, GameState.CANCELED));
    }

    public synchronized List<Refund> expire(long now) {
        List<Refund> refunds = new ArrayList<>();
        for (CoinFlipGame game : new ArrayList<>(games.values())) {
            if (game.isExpired(now)) {
                refunds.add(close(game, GameState.EXPIRED));
            }
        }
        return refunds;
    }

    /** Shutdown while the economy still works: refund everyone now. */
    public synchronized List<Refund> refundAll() {
        List<Refund> refunds = new ArrayList<>();
        for (CoinFlipGame game : new ArrayList<>(games.values())) {
            if (game.state() == GameState.PENDING) {
                refunds.add(close(game, GameState.CANCELED));
            }
        }
        games.clear();
        byCreator.clear();
        byTarget.clear();
        return refunds;
    }

    /** Shutdown without a working economy: forget the games, keep the stakes in escrow for the next start. */
    public synchronized int abandonAll() {
        int count = games.size();
        games.clear();
        byCreator.clear();
        byTarget.clear();
        return count;
    }

    private Refund close(CoinFlipGame game, GameState endState) {
        unindex(game);
        game.state(endState);
        return new Refund(game, refundStake(game, game.creator()));
    }

    private boolean refundStake(CoinFlipGame game, UUID player) {
        if (bank.deposit(player, game.amount())) {
            escrow.release(game.id(), player);
            return true;
        }
        logger.warning("Could not refund " + game.amount() + " to " + player + " for coinflip " + game.id()
                + ". It stays in escrow and is refunded on the next start.");
        return false;
    }

    private void unindex(CoinFlipGame game) {
        games.remove(game.id());
        byCreator.remove(game.creator(), game.id());
        game.target().ifPresent(t -> byTarget.remove(t, game.id()));
    }

    // ------------------------------------------------------------------ crash recovery

    /** Refunds stakes left in escrow by a crash or an unclean shutdown. Returns how many were refunded. */
    public static int recover(Escrow escrow, Bank bank, Logger logger) {
        int refunded = 0;
        List<Escrow.Entry> pending;
        try {
            pending = escrow.pending();
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "Could not read the coinflip escrow; nothing was refunded", e);
            return 0;
        }
        for (Escrow.Entry entry : pending) {
            if (bank.deposit(entry.player(), entry.amount())) {
                escrow.release(entry.gameId(), entry.player());
                refunded++;
                logger.info("Refunded " + entry.amount() + " to " + entry.player()
                        + " from coinflip " + entry.gameId() + " that was still open when the server stopped.");
            } else {
                logger.warning("Could not refund " + entry.amount() + " to " + entry.player()
                        + " from coinflip " + entry.gameId() + "; will try again on the next start.");
            }
        }
        return refunded;
    }
}
