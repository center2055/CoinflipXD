package io.github.center2055.coinflipxd.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PayoutCalculatorTest {

    @Test
    void calculatesWinningsWithTax() {
        PayoutCalculator.Payout payout = PayoutCalculator.calculate(1000D, true, 10.0D, true);
        assertEquals(2000D, payout.totalPot());
        assertEquals(1800D, payout.winnings());
        assertEquals(200D, payout.taxAmount());
    }

    @Test
    void clampsTax() {
        assertEquals(500D, PayoutCalculator.calculate(250D, true, -5.0D, true).winnings());
        assertEquals(0D, PayoutCalculator.calculate(100D, true, 150.0D, true).winnings());
        assertEquals(200D, PayoutCalculator.calculate(100D, true, 150.0D, true).taxAmount());
        assertEquals(200D, PayoutCalculator.calculate(100D, true, Double.NaN, true).winnings());
    }

    @Test
    void handlesTaxDisabled() {
        PayoutCalculator.Payout payout = PayoutCalculator.calculate(500D, false, 50.0D, true);
        assertEquals(1000D, payout.winnings());
        assertEquals(0D, payout.taxAmount());
    }

    @Test
    void roundsToWholeUnitsOrCents() {
        // 2 x 333 = 666, minus 7% = 619.38
        assertEquals(619D, PayoutCalculator.calculate(333D, true, 7.0D, true).winnings());
        assertEquals(619.38D, PayoutCalculator.calculate(333D, true, 7.0D, false).winnings(), 1e-9);
    }

    @Test
    void neverCreatesOrLosesMoney() {
        for (double stake : new double[] {1, 7, 99, 100, 333, 1234.56, 999_999}) {
            for (double tax : new double[] {0, 1, 2.5, 7, 10, 33.3, 100}) {
                for (boolean whole : new boolean[] {true, false}) {
                    PayoutCalculator.Payout p = PayoutCalculator.calculate(stake, true, tax, whole);
                    assertEquals(p.totalPot(), p.winnings() + p.taxAmount(), 1e-6, stake + " " + tax);
                }
            }
        }
    }
}
