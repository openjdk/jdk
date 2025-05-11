import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @test
 * @run junit ValueOfDouble
 */

public class ValueOfDouble {
    private static final String DIGITS = "1234567899123456789"; // Enough digits to fill a long

    @Test
    public void testValueOfDouble() {
        checkValue(0.0);
        checkValue(-0.0);
        checkValue(Math.PI);
        checkValue(-Math.PI);
        checkValue(Double.MAX_VALUE);
        checkValue(Double.MIN_VALUE);
        checkValue(1e-44); // Lots of digits with lots of 9s

        for (int prec = 1; prec < DIGITS.length(); prec++) {
            String prefix = DIGITS.substring(0, prec);
            for (int exp = -30; exp < 30; exp++) {
                double value = Double.parseDouble(prefix + "e" + exp);
                checkValue(value);
                checkValue(-value);
            }
        }
    }

    private static void checkValue(double value) {
        BigDecimal expected = new BigDecimal(Double.toString(value));
        assertEquals(expected, BigDecimal.valueOf(value));
    }

    @Test
    public void testExceptions() {
        assertThrows(NumberFormatException.class, () -> BigDecimal.valueOf(Double.NaN));
        assertThrows(NumberFormatException.class, () -> BigDecimal.valueOf(Double.POSITIVE_INFINITY));
        assertThrows(NumberFormatException.class, () -> BigDecimal.valueOf(Double.NEGATIVE_INFINITY));
    }
}
