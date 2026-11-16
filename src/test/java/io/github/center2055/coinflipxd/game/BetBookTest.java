package io.github.center2055.coinflipxd.game;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BetBookTest {

    static final class FakeBank implements BetBook.Bank {
        final Map<UUID, Double> balances = new HashMap<>();
        final Set<UUID> brokenDeposits = new HashSet<>();
        boolean allDepositsFail;

        @Override
        public synchronized boolean withdraw(UUID player, double amount) {
            double balance = balances.getOrDefault(player, 0D);
            if (balance < amount) {
                return false;
            }
            balances.put(player, balance - amount);
            return true;
        }

        @Override
        public synchronized boolean deposit(UUID player, double amount) {
            if (allDepositsFail || brokenDeposits.contains(player)) {
                return false;
            }
            balances.merge(player, amount, Double::sum);
            return true;
        }

        synchronized double total() {
            return balances.values().stream().mapToDouble(Double::doubleValue).sum();
        }
    }

    static final class FakeEscrow implements BetBook.Escrow {
        final List<Entry> rows = new ArrayList<>();

        @Override
        public synchronized void hold(UUID gameId, UUID player, double amount) {
            rows.add(new Entry(gameId, player, amount));
        }

        @Override
        public synchronized void release(UUID gameId, UUID player) {
            rows.removeIf(e -> e.gameId().equals(gameId) && e.player().equals(player));
        }

        @Override
        public synchronized void releaseGame(UUID gameId) {
            rows.removeIf(e -> e.gameId().equals(gameId));
        }

        @Override
        public synchronized List<Entry> pending() {
            return new ArrayList<>(rows);
        }

        synchronized double held() {
            return rows.stream().mapToDouble(Entry::amount).sum();
        }
    }

    /** Always returns the given coin side: true = creator wins. */
    static RandomGenerator coin(boolean creatorWins) {
        return new RandomGenerator() {
            @Override
            public long nextLong() {
                return 0;
            }

            @Override
            public boolean nextBoolean() {
                return creatorWins;
            }
        };
    }

    private static final Logger LOG = Logger.getAnonymousLogger();
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final UUID carol = UUID.randomUUID();
    private final UUID taxman = UUID.randomUUID();
    private FakeBank bank;
    private FakeEscrow escrow;

    @BeforeEach
    void setUp() {
        bank = new FakeBank();
        escrow = new FakeEscrow();
        bank.balances.put(alice, 1000D);
        bank.balances.put(bob, 1000D);
        bank.balances.put(carol, 50D);
    }

    private BetBook book(boolean creatorWins, double tax, UUID taxRecipient) {
        return new BetBook(bank, escrow, coin(creatorWins),
                () -> new BetBook.PayoutRules(tax > 0, tax, taxRecipient, true), LOG);
    }

    @Test
    void publicGameIsPaidOutWithTaxAndEscrowCleared() {
        BetBook book = book(true, 10, taxman);
        BetBook.CreateResult created = book.create(alice, GameType.PUBLIC, null, 100, 0, 60_000, true);
        assertEquals(BetBook.Status.OK, created.status());
        assertEquals(900D, bank.balances.get(alice));
        assertEquals(100D, escrow.held());

        BetBook.Outcome outcome = book.accept(bob, created.game().id(), 1000);
        assertEquals(BetBook.Status.OK, outcome.status());
        assertEquals(alice, outcome.winner());
        assertEquals(1080D, bank.balances.get(alice)); // 900 + 180
        assertEquals(900D, bank.balances.get(bob));
        assertEquals(20D, bank.balances.get(taxman));
        assertEquals(0D, escrow.held(), "escrow empty after payout");
        assertEquals(2050D, bank.total(), "no money created or destroyed");
        assertEquals(0, book.size());
        assertEquals(GameState.COMPLETED, outcome.game().state());
    }

    @Test
    void serverTaxLeavesTheEconomy() {
        BetBook book = book(false, 10, null);
        UUID id = book.create(alice, GameType.PUBLIC, null, 100, 0, 60_000, true).game().id();
        book.accept(bob, id, 0);
        assertEquals(1080D, bank.balances.get(bob));
        assertEquals(2050D - 20D, bank.total(), "server tax is removed from the economy");
    }

    @Test
    void cannotAcceptTwiceOrOwnGameOrWithoutMoney() {
        BetBook book = book(true, 0, null);
        UUID id = book.create(alice, GameType.PUBLIC, null, 100, 0, 60_000, true).game().id();
        assertEquals(BetBook.Status.SELF, book.accept(alice, id, 0).status());
        assertEquals(BetBook.Status.INSUFFICIENT_FUNDS, book.accept(carol, id, 0).status());
        assertEquals(50D, bank.balances.get(carol), "failed accept takes nothing");
        assertEquals(BetBook.Status.OK, book.accept(bob, id, 0).status());
        assertEquals(BetBook.Status.NOT_FOUND, book.accept(carol, id, 0).status());
        assertEquals(2050D, bank.total());
    }

    @Test
    void privateGameOnlyForTarget() {
        BetBook book = book(false, 0, null);
        UUID id = book.create(alice, GameType.PRIVATE, bob, 100, 0, 60_000, true).game().id();
        assertEquals(BetBook.Status.TARGET_BUSY, book.create(carol, GameType.PRIVATE, bob, 10, 0, 60_000, true).status());
        assertEquals(50D, bank.balances.get(carol), "rejected create takes nothing");
        assertEquals(BetBook.Status.NOT_TARGET, book.accept(carol, id, 0).status());
        assertTrue(book.byTarget(bob).isPresent());
        assertEquals(BetBook.Status.OK, book.accept(bob, id, 0).status());
        assertTrue(book.byTarget(bob).isEmpty());
    }

    @Test
    void onePerPlayerAndInsufficientFunds() {
        BetBook book = book(true, 0, null);
        assertEquals(BetBook.Status.OK, book.create(alice, GameType.PUBLIC, null, 100, 0, 60_000, true).status());
        assertEquals(BetBook.Status.ALREADY_ACTIVE, book.create(alice, GameType.PUBLIC, null, 100, 0, 60_000, true).status());
        assertEquals(BetBook.Status.INSUFFICIENT_FUNDS, book.create(carol, GameType.PUBLIC, null, 100, 0, 60_000, true).status());
        assertEquals(BetBook.Status.INSUFFICIENT_FUNDS, book.create(bob, GameType.PUBLIC, null, -5, 0, 60_000, true).status());
        assertEquals(900D, bank.balances.get(alice));
    }

    @Test
    void cancelAndExpireRefund() {
        BetBook book = book(true, 0, null);
        UUID a = book.create(alice, GameType.PUBLIC, null, 100, 0, 1_000, true).game().id();
        UUID b = book.create(bob, GameType.PUBLIC, null, 200, 0, 60_000, true).game().id();
        assertTrue(book.cancel(b).orElseThrow().refunded());
        assertTrue(book.cancel(b).isEmpty(), "second cancel does nothing");
        assertEquals(1000D, bank.balances.get(bob));

        assertTrue(book.expire(500).isEmpty());
        List<BetBook.Refund> expired = book.expire(1_000);
        assertEquals(1, expired.size());
        assertEquals(a, expired.get(0).game().id());
        assertEquals(1000D, bank.balances.get(alice));
        assertEquals(0D, escrow.held());
    }

    @Test
    void acceptingAnExpiredGameRefundsInsteadOfPlaying() {
        BetBook book = book(true, 0, null);
        UUID id = book.create(alice, GameType.PUBLIC, null, 100, 0, 1_000, true).game().id();
        BetBook.Outcome outcome = book.accept(bob, id, 5_000);
        assertEquals(BetBook.Status.EXPIRED, outcome.status());
        assertNotNull(outcome.refund());
        assertEquals(1000D, bank.balances.get(alice));
        assertEquals(1000D, bank.balances.get(bob));
    }

    @Test
    void failedPayoutRefundsBothStakes() {
        BetBook book = book(true, 10, null);
        UUID id = book.create(alice, GameType.PUBLIC, null, 100, 0, 60_000, true).game().id();
        bank.brokenDeposits.add(alice); // the winner can't receive... and can't be refunded either
        BetBook.Outcome outcome = book.accept(bob, id, 0);
        assertEquals(BetBook.Status.PAYOUT_FAILED, outcome.status());
        assertEquals(1000D, bank.balances.get(bob), "acceptor refunded");
        assertEquals(100D, escrow.held(), "alice's stake stays in escrow");

        bank.brokenDeposits.clear();
        assertEquals(1, BetBook.recover(escrow, bank, LOG));
        assertEquals(1000D, bank.balances.get(alice), "recovered on next start");
        assertEquals(0D, escrow.held());
    }

    @Test
    void refundThatFailsIsKeptForRecovery() {
        BetBook book = book(true, 0, null);
        UUID id = book.create(alice, GameType.PUBLIC, null, 100, 0, 60_000, true).game().id();
        bank.allDepositsFail = true;
        assertFalse(book.cancel(id).orElseThrow().refunded());
        assertEquals(100D, escrow.held());
        assertEquals(0, BetBook.recover(escrow, bank, LOG), "still broken");
        bank.allDepositsFail = false;
        assertEquals(1, BetBook.recover(escrow, bank, LOG));
        assertEquals(1000D, bank.balances.get(alice));
    }

    @Test
    void crashOrLateShutdownIsRecoveredOnNextStart() {
        BetBook book = book(true, 0, null);
        book.create(alice, GameType.PUBLIC, null, 100, 0, 60_000, true);
        book.create(bob, GameType.PRIVATE, carol, 250, 0, 60_000, true);
        assertEquals(2, book.abandonAll(), "economy gone: games dropped, stakes kept in escrow");
        assertEquals(350D, escrow.held());

        // next start
        assertEquals(2, BetBook.recover(escrow, bank, LOG));
        assertEquals(1000D, bank.balances.get(alice));
        assertEquals(1000D, bank.balances.get(bob));
        assertEquals(0D, escrow.held());
    }

    @Test
    void shutdownWithWorkingEconomyRefundsEveryone() {
        BetBook book = book(true, 0, null);
        book.create(alice, GameType.PUBLIC, null, 100, 0, 60_000, true);
        book.create(bob, GameType.PRIVATE, carol, 250, 0, 60_000, true);
        assertEquals(2, book.refundAll().size());
        assertEquals(2050D, bank.total());
        assertEquals(0D, escrow.held());
        assertEquals(0, book.size());
    }

    @Test
    void concurrentAcceptsPayOutExactlyOnce() throws Exception {
        bank.balances.clear();
        BetBook book = new BetBook(bank, escrow, RandomGenerator.of("L64X128MixRandom"),
                () -> new BetBook.PayoutRules(false, 0, null, true), LOG);
        bank.balances.put(alice, 100D);
        UUID id = book.create(alice, GameType.PUBLIC, null, 100, 0, 60_000, true).game().id();
        int players = 16;
        List<UUID> acceptors = new ArrayList<>();
        for (int i = 0; i < players; i++) {
            UUID p = UUID.randomUUID();
            bank.balances.put(p, 100D);
            acceptors.add(p);
        }
        ExecutorService pool = Executors.newFixedThreadPool(players);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger wins = new AtomicInteger();
        for (UUID p : acceptors) {
            pool.submit(() -> {
                start.await();
                if (book.accept(p, id, 0).status() == BetBook.Status.OK) {
                    wins.incrementAndGet();
                }
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        assertEquals(1, wins.get(), "only one player can accept");
        assertEquals(100D * (players + 1), bank.total(), 1e-9);
        assertEquals(0D, escrow.held());
    }

    @Test
    void coinIsRoughlyFair() {
        BetBook book = new BetBook(bank, escrow, new java.security.SecureRandom(),
                () -> new BetBook.PayoutRules(false, 0, null, true), LOG);
        bank.balances.put(alice, 1e9);
        bank.balances.put(bob, 1e9);
        int creatorWins = 0;
        int rounds = 4000;
        for (int i = 0; i < rounds; i++) {
            UUID id = book.create(alice, GameType.PUBLIC, null, 1, 0, 60_000, true).game().id();
            if (alice.equals(book.accept(bob, id, 0).winner())) {
                creatorWins++;
            }
        }
        assertTrue(creatorWins > rounds * 0.45 && creatorWins < rounds * 0.55, "creator won " + creatorWins);
    }
}
