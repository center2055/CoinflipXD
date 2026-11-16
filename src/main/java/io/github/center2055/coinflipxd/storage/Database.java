package io.github.center2055.coinflipxd.storage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The plugin's SQLite file (data.db). Uses the SQLite driver that ships with Spigot and Paper, and one writer
 * thread so writes happen in the order they were made.
 */
public final class Database {

    private static final String[] SCHEMA = {
            """
            CREATE TABLE IF NOT EXISTS player_stats (
                player_uuid TEXT PRIMARY KEY,
                wins INTEGER NOT NULL DEFAULT 0,
                losses INTEGER NOT NULL DEFAULT 0,
                total_won REAL NOT NULL DEFAULT 0,
                total_lost REAL NOT NULL DEFAULT 0,
                last_play_ts INTEGER NOT NULL DEFAULT 0
            )""",
            """
            CREATE TABLE IF NOT EXISTS escrow (
                game_id TEXT NOT NULL,
                player_uuid TEXT NOT NULL,
                amount REAL NOT NULL,
                created_ts INTEGER NOT NULL,
                PRIMARY KEY (game_id, player_uuid)
            )"""
    };

    private final Path file;
    private final Logger logger;
    private final ExecutorService writer;

    public Database(Path file, Logger logger) {
        this.file = file;
        this.logger = logger;
        this.writer = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "CoinflipXD-Database");
            t.setDaemon(true);
            return t;
        });
    }

    /** Creates the tables. Runs on the calling thread so the database is ready before anything uses it. */
    public void init() throws SQLException {
        try {
            Class.forName("org.sqlite.JDBC");
            Files.createDirectories(file.toAbsolutePath().getParent());
        } catch (ClassNotFoundException e) {
            throw new SQLException("The server has no SQLite driver (org.sqlite.JDBC).", e);
        } catch (java.io.IOException e) {
            throw new SQLException("Could not create " + file.getParent(), e);
        }
        try (Connection c = connect(); Statement s = c.createStatement()) {
            for (String sql : SCHEMA) {
                s.executeUpdate(sql);
            }
        }
    }

    public Connection connect() throws SQLException {
        Connection c = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
        try (Statement s = c.createStatement()) {
            s.execute("PRAGMA busy_timeout = 5000");
        }
        return c;
    }

    public interface SqlTask {
        void run(Connection connection) throws SQLException;
    }

    /** Queues a write. Writes run one after another in submission order. */
    public void write(String what, SqlTask task) {
        try {
            writer.execute(() -> {
                try (Connection c = connect()) {
                    task.run(c);
                } catch (SQLException e) {
                    logger.log(Level.SEVERE, "Database write failed (" + what + ")", e);
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException e) {
            // Shutting down: do it right away instead of dropping it.
            try (Connection c = connect()) {
                task.run(c);
            } catch (SQLException ex) {
                logger.log(Level.SEVERE, "Database write failed (" + what + ")", ex);
            }
        }
    }

    public ExecutorService executor() {
        return writer;
    }

    /** Finishes queued writes (stats, escrow) before the plugin unloads. */
    public void shutdown() {
        writer.shutdown();
        try {
            if (!writer.awaitTermination(10, TimeUnit.SECONDS)) {
                logger.warning("Database writes did not finish within 10 seconds.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
