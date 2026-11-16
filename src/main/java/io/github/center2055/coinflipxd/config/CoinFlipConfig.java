package io.github.center2055.coinflipxd.config;

import net.kyori.adventure.key.Key;

import java.util.UUID;

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

    /** {@code recipientId} is null when taxed money simply leaves the economy ("server"). */
    public record TaxSettings(boolean enabled, double percent, String recipient, UUID recipientId) {
    }

    public record UiSettings(BrowserSettings browser, int expireSeconds, int privateExpireSeconds, double createConfirmMin, UiSounds sounds) {
    }

    public record BrowserSettings(int rows, int itemsPerPage) {
    }

    /** Sound keys such as minecraft:ui.button.click. Null means silent. */
    public record UiSounds(Key open, Key accept, Key win, Key lose) {
    }

    public record LimitSettings(boolean oneActivePerPlayer) {
    }

    public record BroadcastSettings(boolean enabled, String message) {
    }
}
