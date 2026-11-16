package io.github.center2055.coinflipxd.stats;

import io.github.center2055.coinflipxd.storage.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class StatsService {

    private final Database db;
    private final Logger logger;

    public StatsService(Database db, Logger logger) {
        this.db = db;
        this.logger = logger;
    }

    /** Records a finished game. {@code winnerProfit} is the net gain (payout minus own stake). */
    public void recordResult(UUID winner, UUID loser, double winnerProfit, double loserLoss) {
        long now = Instant.now().getEpochSecond();
        db.write("stats", c -> {
            update(c, winner, 1, 0, winnerProfit, 0, now);
            update(c, loser, 0, 1, 0, loserLoss, now);
        });
    }

    public CompletableFuture<PlayerStats> fetchStats(UUID playerId) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection c = db.connect()) {
                return select(c, playerId);
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Failed to read stats", e);
                return PlayerStats.empty(playerId);
            }
        }, db.executor());
    }

    private static void update(Connection c, UUID playerId, int wins, int losses, double won, double lost, long ts)
            throws SQLException {
        try (PreparedStatement s = c.prepareStatement("""
                INSERT INTO player_stats (player_uuid, wins, losses, total_won, total_lost, last_play_ts)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT(player_uuid) DO UPDATE SET
                    wins = wins + excluded.wins,
                    losses = losses + excluded.losses,
                    total_won = total_won + excluded.total_won,
                    total_lost = total_lost + excluded.total_lost,
                    last_play_ts = excluded.last_play_ts
                """)) {
            s.setString(1, playerId.toString());
            s.setInt(2, wins);
            s.setInt(3, losses);
            s.setDouble(4, won);
            s.setDouble(5, lost);
            s.setLong(6, ts);
            s.executeUpdate();
        }
    }

    private static PlayerStats select(Connection c, UUID playerId) throws SQLException {
        try (PreparedStatement s = c.prepareStatement(
                "SELECT wins, losses, total_won, total_lost, last_play_ts FROM player_stats WHERE player_uuid = ?")) {
            s.setString(1, playerId.toString());
            try (ResultSet rs = s.executeQuery()) {
                if (rs.next()) {
                    return new PlayerStats(playerId, rs.getInt(1), rs.getInt(2), rs.getDouble(3), rs.getDouble(4), rs.getLong(5));
                }
            }
        }
        return PlayerStats.empty(playerId);
    }
}
