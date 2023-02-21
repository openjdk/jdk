/*
 * @test
 * @summary Test Rational.equals() method.
 * @author xlu
 */

import java.math.*;
import static java.math.Rational.*;

public class EqualsTests {

    public static void main(String argv[]) {
        int failures = 0;

        Rational[][] testValues = {
                // The even index is supposed to return true for equals call and
                // the odd index is supposed to return false, i.e. not equal.
                { ZERO, ZERO }, { ONE, TEN },

                { new Rational(Integer.MAX_VALUE), new Rational(Integer.MAX_VALUE) },
                { new Rational(Long.MAX_VALUE), new Rational(-Long.MAX_VALUE) },

                { new Rational(12345678), new Rational(12345678) },
                { new Rational(123456789), new Rational(123456788) },

                { new Rational("123456789123456789123"), new Rational(new BigInteger("123456789123456789123")) },
                { new Rational("123456789123456789123"), new Rational(new BigInteger("123456789123456789124")) },

                { new Rational(Long.MIN_VALUE), new Rational("-9223372036854775808") },
                { new Rational("9223372036854775808"), new Rational(Long.MAX_VALUE) },

                { new Rational(Math.round(Math.pow(2, 10))), new Rational("1024") },
                { new Rational("1020"), valueOf(Math.pow(2, 11)) },

                { new Rational(BigInteger.valueOf(2).pow(65)), new Rational("36893488147419103232") },
                { new Rational("36893488147419103231.81"), new Rational("36893488147419103231.811"), } };

        boolean expected = true;
        for (Rational[] testValuePair : testValues) {
            failures += equalsTest(testValuePair[0], testValuePair[1], expected);
            expected = !expected;
        }

        if (failures > 0) {
            throw new RuntimeException("Incurred " + failures + " failures while testing equals.");
        }
    }

    private static int equalsTest(Rational l, Rational r, boolean expected) {
        boolean result = l.equals(r);
        int failed = (result == expected) ? 0 : 1;

        if (failed == 1) {
            System.err.println(l + " .equals(" + r + ") => " + result + "\n\tExpected " + expected);
        }
        return failed;
    }
}
