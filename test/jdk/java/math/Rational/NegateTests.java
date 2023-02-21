/*
 * @test
 * @summary Test for the rounding behavior of negate(MathContext)
 */

import java.math.*;

public class NegateTests {

    private static Rational negateThenRound(Rational r, MathContext mc) {
        return r.negate().round(mc);
    }

    private static int negateTest(Rational[][] testCases, MathContext mc) {
        int failures = 0;

        for (Rational[] testCase : testCases) {

            Rational r = testCase[0];
            Rational neg = negateThenRound(r, mc);
            Rational expected = testCase[1];

            if (!neg.equals(expected)) {
                failures++;
                System.err.println("(" + r + ").negate(" + mc + ") => " + neg + " != expected " + expected);
            }
        }

        return failures;
    }

    private static int negateTests() {
        int failures = 0;
        Rational[][] testCasesCeiling = { { new Rational("1.3"), new Rational("-1") },
                { new Rational("-1.3"), new Rational("2") }, };

        failures += negateTest(testCasesCeiling, new MathContext(1, RoundingMode.CEILING));

        Rational[][] testCasesFloor = { { new Rational("1.3"), new Rational("-2") },
                { new Rational("-1.3"), new Rational("1") }, };

        failures += negateTest(testCasesFloor, new MathContext(1, RoundingMode.FLOOR));

        return failures;
    }

    public static void main(String argv[]) {
        int failures = 0;

        failures += negateTests();

        if (failures > 0)
            throw new RuntimeException("Incurred " + failures + " failures" + " testing the negate and/or abs.");
    }
}
