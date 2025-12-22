package com.yourorg.coinflip.config;

import org.bukkit.Sound;

public record CoinFlipConfig(
        EconomySettings economy,
        TaxSettings tax,
        UiSettings ui,
        LimitSettings limits,
        BroadcastSettings broadcast,
        boolean miniMessage
) {

    public record EconomySettings(double minBet, double maxBet, double maxBalancePercent, boolean requireWholeNumbers) {
    }

    public record TaxSettings(boolean enabled, double percent, String recipient) {
    }

    public record UiSettings(BrowserSettings browser, int expireSeconds, int privateExpireSeconds, UiSounds sounds) {
    }

    public record BrowserSettings(int rows, int itemsPerPage) {
    }

    public record UiSounds(Sound open, Sound accept, Sound win, Sound lose) {
    }

    public record LimitSettings(boolean oneActivePerPlayer) {
    }

    public record BroadcastSettings(boolean enabled, String message) {
    }
}

