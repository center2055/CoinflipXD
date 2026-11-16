package io.github.center2055.coinflipxd.storage;

import io.github.center2055.coinflipxd.game.BetBook;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Stakes the plugin is holding, kept in data.db so a crash can't make them disappear. */
public final class EscrowStore implements BetBook.Escrow {

    private final Database db;

    public EscrowStore(Database db) {
        this.db = db;
    }

    @Override
    public void hold(UUID gameId, UUID player, double amount) {
        long now = System.currentTimeMillis();
        db.write("escrow hold", c -> {
            try (PreparedStatement s = c.prepareStatement(
                    "INSERT OR REPLACE INTO escrow (game_id, player_uuid, amount, created_ts) VALUES (?, ?, ?, ?)")) {
                s.setString(1, gameId.toString());
                s.setString(2, player.toString());
                s.setDouble(3, amount);
                s.setLong(4, now);
                s.executeUpdate();
            }
        });
    }

    @Override
    public void release(UUID gameId, UUID player) {
        db.write("escrow release", c -> {
            try (PreparedStatement s = c.prepareStatement("DELETE FROM escrow WHERE game_id = ? AND player_uuid = ?")) {
                s.setString(1, gameId.toString());
                s.setString(2, player.toString());
                s.executeUpdate();
            }
        });
    }

    @Override
    public void releaseGame(UUID gameId) {
        db.write("escrow release game", c -> {
            try (PreparedStatement s = c.prepareStatement("DELETE FROM escrow WHERE game_id = ?")) {
                s.setString(1, gameId.toString());
                s.executeUpdate();
            }
        });
    }

    /** Read directly on the calling thread; only used at startup. */
    @Override
    public List<Entry> pending() {
        List<Entry> entries = new ArrayList<>();
        try (Connection c = db.connect();
             PreparedStatement s = c.prepareStatement("SELECT game_id, player_uuid, amount FROM escrow ORDER BY created_ts");
             ResultSet rs = s.executeQuery()) {
            while (rs.next()) {
                try {
                    entries.add(new Entry(UUID.fromString(rs.getString(1)), UUID.fromString(rs.getString(2)), rs.getDouble(3)));
                } catch (IllegalArgumentException ignored) {
                    // corrupt row, skip
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return entries;
    }
}
