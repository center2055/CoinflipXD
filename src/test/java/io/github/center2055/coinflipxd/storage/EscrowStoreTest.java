package io.github.center2055.coinflipxd.storage;

import io.github.center2055.coinflipxd.game.BetBook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EscrowStoreTest {

    @TempDir
    Path dir;

    @Test
    void survivesRestartAndReleases() throws Exception {
        UUID game = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();

        Database db = new Database(dir.resolve("data.db"), Logger.getAnonymousLogger());
        db.init();
        EscrowStore escrow = new EscrowStore(db);
        escrow.hold(game, alice, 100);
        escrow.hold(game, bob, 100);
        escrow.hold(other, alice, 5.5);
        escrow.release(game, bob);
        db.shutdown(); // flushes queued writes, like a server stop

        Database reopened = new Database(dir.resolve("data.db"), Logger.getAnonymousLogger());
        reopened.init();
        EscrowStore after = new EscrowStore(reopened);
        List<BetBook.Escrow.Entry> pending = after.pending();
        assertEquals(2, pending.size());
        assertEquals(105.5, pending.stream().mapToDouble(BetBook.Escrow.Entry::amount).sum(), 1e-9);

        after.releaseGame(game);
        after.releaseGame(other);
        reopened.shutdown();
        Database third = new Database(dir.resolve("data.db"), Logger.getAnonymousLogger());
        third.init();
        assertEquals(0, new EscrowStore(third).pending().size());
        third.shutdown();
    }
}
