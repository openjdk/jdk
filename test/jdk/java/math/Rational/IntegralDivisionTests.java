
/*
 * @test
 * @summary Tests that integral division and related methods return the proper result.
 * @author Joseph D. Darcy
 */
import java.math.*;

public class IntegralDivisionTests {

    static int dividetoIntegralValueTests() {
        int failures = 0;

        // Exact integer quotient should have the same results from
        // the exact divide and dividetoIntegralValue

        // Rounded results
        Rational[][] moreTestCases = { { new Rational("11003"), new Rational("10"), new Rational("1100") },
                { new Rational("11003"), new Rational("1e1"), new Rational("1100") },
                { new Rational("1e9"), new Rational("1"), new Rational("1e9") },
                { new Rational("1e9"), new Rational("1"), new Rational("1e9") },
                { new Rational("1e9"), new Rational("0.1"), new Rational("1e10") },
                { new Rational("10e8"), new Rational("0.1"), new Rational("10e9") },
                { new Rational("400e1"), new Rational("5"), new Rational("80e1") },
                { new Rational("400e1"), new Rational("4.999999999"), new Rational("8e2") },
                { new Rational("40e2"), new Rational("5"), new Rational("8e2") }, };

        for (Rational[] testCase : moreTestCases) {
            Rational quotient;
            if (!(quotient = testCase[0].divideToIntegralValue(testCase[1])).equals(testCase[2])) {
                failures++;
                // BigDecimal exact = testCase[0].divide(testCase[1]);
                System.err.println();
                System.err.println("dividend  = " + testCase[0]);
                System.err.println("divisor   = " + testCase[1]);
                System.err.println("quotient  = " + quotient);
                System.err.println("expected  = " + testCase[2]);
                // System.err.println("exact = " + exact);
            }
        }

        return failures;
    }

    public static void main(String argv[]) {
        int failures = 0;

        failures += dividetoIntegralValueTests();

        if (failures > 0) {
            System.err.println("Encountered " + failures + " failures while testing integral division.");
            throw new RuntimeException();
        }
    }
}
