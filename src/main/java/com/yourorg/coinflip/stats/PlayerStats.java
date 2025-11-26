package com.yourorg.coinflip.stats;

import java.time.Instant;
import java.util.UUID;

public record PlayerStats(
        UUID playerId,
        int wins,
        int losses,
        double totalWon,
        double totalLost,
        long lastPlayedEpochSeconds
) {

    public static PlayerStats empty(UUID playerId) {
        return new PlayerStats(playerId, 0, 0, 0.0D, 0.0D, 0L);
    }

    public int totalGames() {
        return wins + losses;
    }

    public Instant lastPlayed() {
        if (lastPlayedEpochSeconds <= 0) {
            return Instant.EPOCH;
        }
        return Instant.ofEpochSecond(lastPlayedEpochSeconds);
    }
}

