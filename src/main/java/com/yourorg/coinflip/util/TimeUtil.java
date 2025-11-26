package com.yourorg.coinflip.util;

import java.time.Duration;

public final class TimeUtil {

    private TimeUtil() {
    }

    public static String formatSecondsRemaining(long millisRemaining) {
        if (millisRemaining < 0) {
            millisRemaining = 0;
        }
        Duration duration = Duration.ofMillis(millisRemaining);
        long minutes = duration.toMinutes();
        long seconds = duration.minusMinutes(minutes).toSeconds();
        return String.format("%02d:%02d", minutes, seconds);
    }
}

