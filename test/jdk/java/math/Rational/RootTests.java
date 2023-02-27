/*
 * @test
 * @summary Tests of Rational.root(n).
 */

import java.math.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import static java.math.Rational.*;

public class RootTests {
    /**
     * The value 0.1.
     */
    private static final Rational ONE_TENTH = valueOf(1, 10);

    private static final HashSet<RoundingMode> MODES = new HashSet<>(Arrays.asList(RoundingMode.values()));

    static {
        MODES.remove(RoundingMode.UNNECESSARY);
    }

    public static void main(String... args) {
        int failures = 0;

        failures += negativeTests();
        failures += zeroTests();
        failures += oneDigitSqrtTests();
        failures += oneDigitCbrtTests();
        failures += evenPowersOfTenTests();
        failures += squareRootTwoTests();
        failures += lowPrecisionPerfectSquares();
        failures += almostFourRoundingDown();
        failures += almostFourRoundingUp();
        failures += nearTen();
        failures += nearOne();
        failures += halfWay();

        if (failures > 0) {
            throw new RuntimeException("Incurred " + failures + " failures" + " testing Rational.root().");
        }
    }

    private static int negativeTests() {
        int failures = 0;

        // even degree
        for (int n = 0; n <= 100; n += 2) {
            for (int i = -10; i < 0; i++) {
                for (int j = -5; j < 5; j++) {
                    try {
                        Rational input = new Rational(i + "e" + -j);
                        Rational result = input.root(n, MathContext.DECIMAL64);
                        System.err.println("Unexpected even-degree root of negative: (" + input + ").root(" + n
                                + ")  = " + result);
                        failures++;
                    } catch (ArithmeticException e) {
                        // Expected
                    }
                }
            }
        }

        // odd degree
        for (int n = 1; n <= 100; n += 2) {
            for (int i = -10; i < 0; i++) {
                for (int j = -5; j < 5; j++) {
                    Rational input = new Rational(i + "e" + -j);
                    int sign = input.root(n, MathContext.DECIMAL64).signum();
                    if (sign != -1) {
                        failures++;
                        System.err.println("odd-degree root of negative number did return a value with sign: " + sign);
                    }
                }
            }
        }

        return failures;
    }

    private static int zeroTests() {
        int failures = 0;
        Rational expected = Rational.ZERO;

        for (int n = 1; n <= 100; n++) {
            // These results are independent of rounding mode
            failures += equal(Rational.ZERO.root(n, MathContext.UNLIMITED), expected, "zero");
            failures += equal(Rational.ZERO.root(n, MathContext.DECIMAL64), expected, "zero");
        }

        return failures;
    }

    /**
     * Probe sqrt inputs with one digit of precision, 1 ... 9 and those values
     * scaled by 10^-1, 0.1, ... 0.9.
     */
    private static int oneDigitSqrtTests() {
        int failures = 0;

        // x, sqrt(x), sqrt(x/10)
        String[][] testCases = { { "1", "1", "0.31622776601683793319" },
                { "2", "1.4142135623730950488", "0.44721359549995793928" },
                { "3", "1.73205080756887729352", "0.547722557505166113456" }, { "4", "2", "0.63245553203367586639" },
                { "5", "2.2360679774997896964", "0.7071067811865475244008" },
                { "6", "2.4494897427831780981", "0.77459666924148337703" },
                { "7", "2.645751311064590590501", "0.83666002653407554797" },
                { "8", "2.8284271247461900976", "0.89442719099991587856" }, { "9", "3", "0.94868329805051379959" }, };

        for (int i = 1; i < 20; i++) {
            for (String[] test : testCases) {
                for (RoundingMode rm : MODES) {
                    MathContext mc = new MathContext(i, rm);
                    Rational r = new Rational(test[0]);
                    Rational expected = new Rational(test[1]).round(mc);
                    Rational result = r.sqrt(mc);
                    equal(expected, result, "sqrt(" + r + ")");

                    r = r.multiply(ONE_TENTH);
                    expected = new Rational(test[2]).round(mc);
                    result = r.sqrt(mc);
                    equal(expected, result, "sqrt(" + r + ")");
                }
            }
        }

        return failures;
    }

    /**
     * Probe cube root inputs with one digit of precision, 1 ... 9 and those values
     * scaled by 10^-1, 0.1, ... 0.9.
     */
    private static int oneDigitCbrtTests() {
        int failures = 0;

        // x, cbrt(x), cbrt(x/10)
        String[][] testCases = { { "1", "1", "0.46415888336127788924" },
                { "2", "1.2599210498948731647", "0.584803547642573213101" },
                { "3", "1.4422495703074083823", "0.66943295008216952188" },
                { "4", "1.5874010519681994747", "0.736806299728077321155" },
                { "5", "1.7099759466766969893", "0.79370052598409973737" },
                { "6", "1.8171205928321396588", "0.84343266530174924284" },
                { "7", "1.9129311827723891011", "0.88790400174260070842" }, { "8", "2", "0.92831776672255577848" },
                { "9", "2.08008382305190411453", "0.965489384605629757859" }, };

        for (int i = 1; i < 20; i++) {
            for (String[] test : testCases) {
                for (RoundingMode rm : MODES) {
                    MathContext mc = new MathContext(i, rm);
                    Rational r = new Rational(test[0]);
                    Rational expected = new Rational(test[1]).round(mc);
                    Rational result = r.root(3, mc);
                    equal(expected, result, "root(" + r + ", 3)");

                    r = r.multiply(ONE_TENTH);
                    expected = new Rational(test[2]).round(mc);
                    result = r.root(3, mc);
                    equal(expected, result, "root(" + r + ", 3)");
                }
            }
        }

        return failures;
    }

    /**
     * sqrt(10^2N) is 10^N
     */
    private static int evenPowersOfTenTests() {
        int failures = 0;
        MathContext oneDigitExactly = new MathContext(1, RoundingMode.UNNECESSARY);

        for (int scale = -100; scale <= 100; scale++) {
            Rational testValue = new Rational("1e" + -2 * scale);
            Rational expected = new Rational("1e" + -scale);

            failures += equal(expected, testValue.sqrt(MathContext.DECIMAL64), "Even powers of 10, DECIMAL64");

            // Can round to one digit of precision exactly
            failures += equal(expected, testValue.sqrt(oneDigitExactly), "even powers of 10, 1 digit");
        }

        return failures;
    }

    private static int squareRootTwoTests() {
        int failures = 0;

        // Square root of 2 truncated to 65 digits
        Rational highPrecisionRoot2 = new Rational(
                "1.41421356237309504880168872420969807856967187537694807317667973799");

        // For each interesting rounding mode, for precisions 1 to, say,
        // 63 compare TWO.sqrt(mc) to
        // highPrecisionRoot2.round(mc) and the alternative internal high-precision
        // implementation of square root.
        for (RoundingMode mode : MODES) {
            for (int precision = 1; precision < 63; precision++) {
                MathContext mc = new MathContext(precision, mode);
                Rational expected = highPrecisionRoot2.round(mc);
                Rational computed = TWO.sqrt(mc);

                failures += equal(expected, computed, "sqrt(2)");
            }
        }

        return failures;
    }

    private static int lowPrecisionPerfectSquares() {
        int failures = 0;

        // For 5^2 through 9^2, if the input is rounded to one digit
        // first before the root is computed, the wrong answer will
        // result. Verify results for different rounding modes and precisions.
        long[][] squaresWithOneDigitRoot = { { 4, 2 }, { 9, 3 }, { 16, 4 }, { 25, 5 }, { 36, 6 }, { 49, 7 }, { 64, 8 },
                { 81, 9 } };

        for (long[] squareAndRoot : squaresWithOneDigitRoot) {
            Rational square = new Rational(squareAndRoot[0]);
            Rational expected = new Rational(squareAndRoot[1]);

            for (int precision = 0; precision <= 5; precision++) {
                for (RoundingMode rm : RoundingMode.values()) {
                    MathContext mc = new MathContext(precision, rm);
                    Rational computedRoot = square.sqrt(mc);
                    failures += equal(expected, computedRoot, "simple squares");
                }
            }
        }

        return failures;
    }

    /**
     * Test around 3.9999 that the sqrt doesn't improperly round-up to a numerical
     * value of 2.
     */
    private static int almostFourRoundingDown() {
        int failures = 0;
        Rational nearFour = new Rational("3.999999999999999999999999999999");
        // Square root truncated to 65 digits
        Rational highPrecisionRoot = new Rational("1.9999999999999999999999999999997499999999999999999999999999999843");

        for (int i = 1; i < 64; i++) {
            MathContext mc = new MathContext(i, RoundingMode.DOWN);
            Rational result = nearFour.sqrt(mc);
            Rational expected = highPrecisionRoot.round(mc);
            failures += equal(expected, result, "near four rounding down");
            failures += (result.compareTo(TWO) < 0) ? 0 : 1;
        }

        return failures;
    }

    /**
     * Test around 4.000...1 that the sqrt doesn't improperly round-down to a
     * numerical value of 2.
     */
    private static int almostFourRoundingUp() {
        int failures = 0;
        Rational nearFour = new Rational("4.000000000000000000000000000001");
        // Square root truncated to 65 digits
        Rational highPrecisionRoot = new Rational("2.0000000000000000000000000000002499999999999999999999999999999843");

        for (int i = 1; i < 64; i++) {
            MathContext mc = new MathContext(i, RoundingMode.UP);
            Rational result = nearFour.sqrt(mc);
            Rational expected = highPrecisionRoot.round(mc);
            failures += equal(expected, result, "near four rounding up");
            failures += (result.compareTo(TWO) > 0) ? 0 : 1;
        }

        return failures;
    }

    private static int nearTen() {
        int failures = 0;

        BigDecimal near10 = new BigDecimal("9.99999999999999999999");

        BigDecimal near10sq = near10.multiply(near10);

        Rational near10sq_ulp = valueOf(near10sq.add(near10sq.ulp()));
        // Square root truncated to 23 digits
        Rational highPrecisionRoot = new Rational("9.9999999999999999999900");

        for (int i = 10; i < 23; i++) {
            MathContext mc = new MathContext(i, RoundingMode.HALF_EVEN);

            failures += equal(highPrecisionRoot.round(mc), near10sq_ulp.sqrt(mc), "near 10 rounding half even");
        }

        return failures;
    }

    /*
     * Probe for rounding failures near a power of ten, 1 = 10^0, where an ulp has a
     * different size above and below the value.
     */
    private static int nearOne() {
        int failures = 0;

        BigDecimal near1 = new BigDecimal(".999999999999999999999");
        BigDecimal near1sq = near1.multiply(near1);
        Rational near1sq_ulp = valueOf(near1sq.add(near1sq.ulp()));
        // Square root truncated to 43 digits
        Rational highPrecisionRoot = new Rational("0.9999999999999999999990000000000000000000005");

        for (int i = 10; i < 23; i++) {
            for (RoundingMode rm : List.of(RoundingMode.HALF_EVEN, RoundingMode.UP, RoundingMode.DOWN)) {
                MathContext mc = new MathContext(i, rm);
                failures += equal(highPrecisionRoot.round(mc), near1sq_ulp.sqrt(mc), mc.toString());
            }
        }

        return failures;
    }

    private static int halfWay() {
        int failures = 0;

        /*
         * Use enough digits that the exact result cannot be computed from the sqrt of a
         * double.
         */
        BigDecimal[] halfWayCases = {
                // Odd next digit, truncate on HALF_EVEN
                new BigDecimal("123456789123456789.5"),

                // Even next digit, round up on HALF_EVEN
                new BigDecimal("123456789123456788.5"), };

        for (BigDecimal halfWayCase : halfWayCases) {
            // Round result to next-to-last place
            int precision = halfWayCase.precision() - 1;
            Rational halfWayCaseR = valueOf(halfWayCase);
            Rational square = halfWayCaseR.multiply(halfWayCaseR);

            for (RoundingMode rm : List.of(RoundingMode.HALF_EVEN, RoundingMode.HALF_UP, RoundingMode.HALF_DOWN)) {
                MathContext mc = new MathContext(precision, rm);

                System.out.println("\nRounding mode " + rm);
                System.out.println("\t" + halfWayCase.round(mc) + "\t" + halfWayCase);

                failures += equal(square.sqrt(mc), halfWayCaseR.round(mc), "Rounding halfway " + rm);
            }
        }

        return failures;
    }

    private static int equal(Rational a, Rational b, String prefix) {
        if (!a.equals(b)) {
            System.err.println("Testing " + prefix + " (" + a + ").equals(" + b + ") => false");
            return 1;
        }

        return 0;
    }
}
