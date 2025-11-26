package com.yourorg.coinflip.stats;

import com.yourorg.coinflip.CoinFlipPlugin;
import org.bukkit.OfflinePlayer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class StatsService {

    private static final String TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS player_stats (
                player_uuid TEXT PRIMARY KEY,
                wins INTEGER NOT NULL DEFAULT 0,
                losses INTEGER NOT NULL DEFAULT 0,
                total_won REAL NOT NULL DEFAULT 0,
                total_lost REAL NOT NULL DEFAULT 0,
                last_play_ts INTEGER NOT NULL DEFAULT 0
            )
            """;

    private final CoinFlipPlugin plugin;
    private final Path databasePath;
    private final ExecutorService executor;

    public StatsService(CoinFlipPlugin plugin) {
        this.plugin = plugin;
        this.databasePath = plugin.getDataFolder().toPath().resolve("data.db");
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "CoinFlip-Stats");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void init() {
        try {
            Files.createDirectories(databasePath.getParent());
        } catch (Exception ex) {
            plugin.getLogger().severe("Failed to create data directory: " + ex.getMessage());
        }
        runAsync(() -> {
            try (Connection connection = getConnection();
                 Statement statement = connection.createStatement()) {
                statement.executeUpdate(TABLE_SQL);
            } catch (SQLException ex) {
                plugin.getLogger().severe("Failed to prepare stats database: " + ex.getMessage());
            }
        });
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    public void recordResult(UUID winner, UUID loser, double winnings, double loss) {
        long now = Instant.now().getEpochSecond();
        runAsync(() -> {
            try (Connection connection = getConnection()) {
                updateStats(connection, winner, 1, 0, winnings, 0, now);
                updateStats(connection, loser, 0, 1, 0, loss, now);
            } catch (SQLException ex) {
                plugin.getLogger().severe("Failed to record stats: " + ex.getMessage());
            }
        });
    }

    public CompletableFuture<PlayerStats> fetchStats(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = getConnection()) {
                return selectStats(connection, playerUuid);
            } catch (SQLException ex) {
                plugin.getLogger().severe("Failed to fetch stats: " + ex.getMessage());
                return PlayerStats.empty(playerUuid);
            }
        }, executor);
    }

    public CompletableFuture<PlayerStats> fetchStats(OfflinePlayer player) {
        return fetchStats(player.getUniqueId());
    }

    private void updateStats(Connection connection, UUID playerId,
                             int winIncrement, int lossIncrement,
                             double wonIncrement, double lostIncrement,
                             long lastPlayed) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO player_stats (player_uuid, wins, losses, total_won, total_lost, last_play_ts)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT(player_uuid) DO UPDATE SET
                    wins = wins + excluded.wins,
                    losses = losses + excluded.losses,
                    total_won = total_won + excluded.total_won,
                    total_lost = total_lost + excluded.total_lost,
                    last_play_ts = excluded.last_play_ts
                """)) {
            statement.setString(1, playerId.toString());
            statement.setInt(2, winIncrement);
            statement.setInt(3, lossIncrement);
            statement.setDouble(4, wonIncrement);
            statement.setDouble(5, lostIncrement);
            statement.setLong(6, lastPlayed);
            statement.executeUpdate();
        }
    }

    private PlayerStats selectStats(Connection connection, UUID playerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT wins, losses, total_won, total_lost, last_play_ts
                FROM player_stats
                WHERE player_uuid = ?
                """)) {
            statement.setString(1, playerId.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    int wins = rs.getInt("wins");
                    int losses = rs.getInt("losses");
                    double totalWon = rs.getDouble("total_won");
                    double totalLost = rs.getDouble("total_lost");
                    long lastPlayed = rs.getLong("last_play_ts");
                    return new PlayerStats(playerId, wins, losses, totalWon, totalLost, lastPlayed);
                }
            }
        }
        return PlayerStats.empty(playerId);
    }

    private Connection getConnection() throws SQLException {
        String url = "jdbc:sqlite:" + databasePath.toAbsolutePath();
        return DriverManager.getConnection(url);
    }

    private void runAsync(Runnable runnable) {
        executor.submit(runnable);
    }
}

