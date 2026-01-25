package com.yourorg.coinflip.util;

import com.yourorg.coinflip.config.CoinFlipConfig;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BetUtil {

    private static final Pattern AMOUNT_PATTERN =
            Pattern.compile("^\\s*([+-]?\\d+(?:\\.\\d+)?)\\s*([kKmMbB])?\\s*$");
    private static final double WHOLE_EPSILON = 1.0E-9;

    private BetUtil() {
    }

    public static double parseAmount(String input, CoinFlipConfig.EconomySettings settings, boolean bypass) {
        double amount = parseRawAmount(input);

        if (Double.isNaN(amount) || Double.isInfinite(amount) || amount <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        if (settings.requireWholeNumbers()) {
            if (!isWholeNumber(amount)) {
                throw new IllegalArgumentException("Amount must be whole number");
            }
            amount = Math.rint(amount);
        }

        if (!bypass) {
            if (amount < settings.minBet() || amount > settings.maxBet()) {
                throw new IllegalArgumentException("Amount outside limits");
            }
        }

        return amount;
    }

    public static boolean isAmountInput(String input) {
        if (input == null || input.isBlank()) {
            return false;
        }
        String trimmed = input.trim();
        if (AMOUNT_PATTERN.matcher(trimmed).matches()) {
            return true;
        }
        try {
            Double.parseDouble(trimmed);
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private static double parseRawAmount(String input) {
        if (input == null) {
            throw new IllegalArgumentException("Amount is required");
        }
        Matcher matcher = AMOUNT_PATTERN.matcher(input);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid number");
        }
        double base;
        try {
            base = Double.parseDouble(matcher.group(1));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid number", ex);
        }
        String suffix = matcher.group(2);
        if (suffix == null || suffix.isEmpty()) {
            return base;
        }
        return base * suffixMultiplier(suffix);
    }

    private static double suffixMultiplier(String suffix) {
        return switch (suffix.toLowerCase(Locale.ROOT)) {
            case "k" -> 1_000D;
            case "m" -> 1_000_000D;
            case "b" -> 1_000_000_000D;
            default -> 1D;
        };
    }

    private static boolean isWholeNumber(double value) {
        return Math.abs(value - Math.rint(value)) < WHOLE_EPSILON;
    }

    public static double maxBalanceBet(double balance, CoinFlipConfig.EconomySettings settings) {
        if (balance <= 0 || settings == null) {
            return 0D;
        }
        double percentLimit = balance * (settings.maxBalancePercent() / 100.0D);
        double limit = Math.min(settings.maxBet(), percentLimit);
        if (settings.requireWholeNumbers()) {
            limit = Math.floor(limit);
        }
        return Math.max(0D, limit);
    }
}

