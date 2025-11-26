package com.yourorg.coinflip.util;

import com.yourorg.coinflip.config.CoinFlipConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BetUtilTest {

    private static final CoinFlipConfig.EconomySettings ECONOMY_SETTINGS =
            new CoinFlipConfig.EconomySettings(100.0D, 100000.0D, true);

    @Test
    void parsesValidWholeNumberWithinLimits() {
        double result = BetUtil.parseAmount("500", ECONOMY_SETTINGS, false);
        assertEquals(500D, result);
    }

    @Test
    void rejectsDecimalWhenWholeNumbersRequired() {
        assertThrows(IllegalArgumentException.class,
                () -> BetUtil.parseAmount("123.45", ECONOMY_SETTINGS, false));
    }

    @Test
    void allowsBypassToIgnoreLimits() {
        double result = BetUtil.parseAmount("1000000", ECONOMY_SETTINGS, true);
        assertEquals(1_000_000D, result);
    }

    @Test
    void rejectsNegativeAmount() {
        assertThrows(IllegalArgumentException.class,
                () -> BetUtil.parseAmount("-10", ECONOMY_SETTINGS, false));
    }
}

