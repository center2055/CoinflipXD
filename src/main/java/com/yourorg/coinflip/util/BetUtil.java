package com.yourorg.coinflip.util;

import com.yourorg.coinflip.config.CoinFlipConfig;

public final class BetUtil {

    private BetUtil() {
    }

    public static double parseAmount(String input, CoinFlipConfig.EconomySettings settings, boolean bypass) {
        double amount;
        try {
            amount = Double.parseDouble(input);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid number", ex);
        }

        if (Double.isNaN(amount) || Double.isInfinite(amount) || amount <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        if (settings.requireWholeNumbers() && amount != Math.floor(amount)) {
            throw new IllegalArgumentException("Amount must be whole number");
        }

        if (!bypass) {
            if (amount < settings.minBet() || amount > settings.maxBet()) {
                throw new IllegalArgumentException("Amount outside limits");
            }
        }

        return amount;
    }
}

