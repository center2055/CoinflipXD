package com.yourorg.coinflip.util;

import com.yourorg.coinflip.config.CoinFlipConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PayoutCalculatorTest {

    @Test
    void calculatesWinningsWithTax() {
        CoinFlipConfig.TaxSettings tax = new CoinFlipConfig.TaxSettings(true, 10.0D, "server");
        PayoutCalculator.Payout payout = PayoutCalculator.calculate(1000D, tax);
        assertEquals(2000D, payout.totalPot());
        assertEquals(1800D, payout.winnings());
        assertEquals(200D, payout.taxAmount());
    }

    @Test
    void clampsTaxToZero() {
        CoinFlipConfig.TaxSettings tax = new CoinFlipConfig.TaxSettings(true, -5.0D, "server");
        PayoutCalculator.Payout payout = PayoutCalculator.calculate(250D, tax);
        assertEquals(500D, payout.totalPot());
        assertEquals(500D, payout.winnings());
        assertEquals(0D, payout.taxAmount());
    }

    @Test
    void clampsTaxToHundred() {
        CoinFlipConfig.TaxSettings tax = new CoinFlipConfig.TaxSettings(true, 150.0D, "server");
        PayoutCalculator.Payout payout = PayoutCalculator.calculate(100D, tax);
        assertEquals(200D, payout.totalPot());
        assertEquals(0D, payout.winnings());
        assertEquals(200D, payout.taxAmount());
    }

    @Test
    void handlesTaxDisabled() {
        CoinFlipConfig.TaxSettings tax = new CoinFlipConfig.TaxSettings(false, 50.0D, "server");
        PayoutCalculator.Payout payout = PayoutCalculator.calculate(500D, tax);
        assertEquals(1000D, payout.totalPot());
        assertEquals(1000D, payout.winnings());
        assertEquals(0D, payout.taxAmount());
    }
}

