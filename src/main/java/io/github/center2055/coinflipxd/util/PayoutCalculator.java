package io.github.center2055.coinflipxd.util;

public final class PayoutCalculator {

    private PayoutCalculator() {
    }

    /**
     * The winner gets both stakes minus tax. Winnings are rounded down (to whole units, or to cents when decimal
     * bets are allowed); the rounding remainder counts as tax, so no money is created or lost.
     */
    public static Payout calculate(double stakePerPlayer, boolean taxEnabled, double taxPercent, boolean wholeNumbers) {
        double totalPot = stakePerPlayer * 2.0D;
        if (!taxEnabled) {
            return new Payout(totalPot, totalPot, 0.0D);
        }
        double rate = clampPercent(taxPercent) / 100.0D;
        double raw = totalPot * (1.0D - rate);
        double winnings = wholeNumbers ? Math.floor(raw + 1e-9) : Math.floor(raw * 100.0D + 1e-6) / 100.0D;
        winnings = Math.max(0.0D, Math.min(totalPot, winnings));
        return new Payout(totalPot, winnings, totalPot - winnings);
    }

    private static double clampPercent(double percent) {
        if (Double.isNaN(percent) || percent < 0.0D) {
            return 0.0D;
        }
        return Math.min(percent, 100.0D);
    }

    public record Payout(double totalPot, double winnings, double taxAmount) {
    }
}
