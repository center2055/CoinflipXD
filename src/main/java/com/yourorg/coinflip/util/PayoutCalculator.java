package com.yourorg.coinflip.util;

import com.yourorg.coinflip.config.CoinFlipConfig;

public final class PayoutCalculator {

    private PayoutCalculator() {
    }

    public static Payout calculate(double stakePerPlayer, CoinFlipConfig.TaxSettings taxSettings) {
        double totalPot = stakePerPlayer * 2.0D;
        double winnings = totalPot;
        double taxAmount = 0.0D;
        if (taxSettings.enabled()) {
            double percent = clampPercent(taxSettings.percent());
            double rate = percent / 100.0D;
            winnings = Math.floor(totalPot * (1.0D - rate));
            taxAmount = totalPot - winnings;
        }
        return new Payout(totalPot, winnings, taxAmount);
    }

    private static double clampPercent(double percent) {
        if (percent < 0.0D) {
            return 0.0D;
        }
        if (percent > 100.0D) {
            return 100.0D;
        }
        return percent;
    }

    public record Payout(double totalPot, double winnings, double taxAmount) {
    }
}

