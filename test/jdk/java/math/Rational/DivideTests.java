/*
 * @test
 * @summary Some tests for the divide methods.
 * @author Fabio Romano
 */

import java.math.*;

public class DivideTests {

    static final MathContext HALF_UP_1 = new MathContext(1);
    static final MathContext HALF_UP_3 = new MathContext(3);
    static final MathContext UP_4 = new MathContext(4, RoundingMode.UP);
    static final MathContext DOWN_4 = new MathContext(4, RoundingMode.DOWN);
    static final MathContext CEIL_4 = new MathContext(4, RoundingMode.CEILING);
    static final MathContext FLOOR_4 = new MathContext(4, RoundingMode.FLOOR);
    static final MathContext HALF_UP_4 = new MathContext(4);
    static final MathContext HALF_DOWN_4 = new MathContext(4, RoundingMode.HALF_DOWN);
    static final MathContext HALF_EVEN_4 = new MathContext(4, RoundingMode.HALF_EVEN);

    // Preliminary exact divide method; could be used for comparison
    // purposes.
    BigDecimal anotherDivide(BigDecimal dividend, BigDecimal divisor) {
        /*
         * Handle zero cases first.
         */
        if (divisor.signum() == 0) { // x/0
            if (dividend.signum() == 0) // 0/0
                throw new ArithmeticException("Division undefined"); // NaN
            throw new ArithmeticException("Division by zero");
        }
        if (dividend.signum() == 0) // 0/y
            return BigDecimal.ZERO;
        else {
            /*
             * Determine if there is a result with a terminating decimal expansion. Putting
             * aside overflow and underflow considerations, the existance of an exact result
             * only depends on the ratio of the intVal's of the dividend (i.e. this) and
             * divisor since the scales of the argument just affect where the decimal point
             * lies.
             *
             * For the ratio of (a = this.intVal) and (b = divisor.intVal) to have a finite
             * decimal expansion, once a/b is put in lowest terms, b must be equal to
             * (2^i)*(5^j) for some integer i,j >= 0. Therefore, we first compute to see if
             * b_prime =(b/gcd(a,b)) is equal to (2^i)*(5^j).
             */
            BigInteger TWO = BigInteger.valueOf(2);
            BigInteger FIVE = BigInteger.valueOf(5);
            BigInteger TEN = BigInteger.valueOf(10);

            BigInteger divisorIntvalue = divisor.scaleByPowerOfTen(divisor.scale()).toBigInteger().abs();
            BigInteger dividendIntvalue = dividend.scaleByPowerOfTen(dividend.scale()).toBigInteger().abs();

            BigInteger b_prime = divisorIntvalue.divide(dividendIntvalue.gcd(divisorIntvalue));

            boolean goodDivisor = false;
            // int i = 0, j = 0;

            badDivisor: {
                while (!b_prime.equals(BigInteger.ONE)) {
                    int b_primeModTen = b_prime.mod(TEN).intValue();

                    switch (b_primeModTen) {
                    case 0:
                        // b_prime divisible by 10=2*5, increment i and j
                        // i++;
                        // j++;
                        b_prime = b_prime.divide(TEN);
                        break;

                    case 5:
                        // b_prime divisible by 5, increment j
                        // j++;
                        b_prime = b_prime.divide(FIVE);
                        break;

                    case 2:
                    case 4:
                    case 6:
                    case 8:
                        // b_prime divisible by 2, increment i
                        // i++;
                        b_prime = b_prime.divide(TWO);
                        break;

                    default: // hit something we shouldn't have
                        b_prime = BigInteger.ONE; // terminate loop
                        break badDivisor;
                    }
                }

                goodDivisor = true;
            }

            if (!goodDivisor) {
                throw new ArithmeticException("Non terminating decimal expansion");
            } else {
                // What is a rule for determining how many digits are
                // needed? Once that is determined, cons up a new
                // MathContext object and pass it on to the divide(bd,
                // mc) method; precision == ?, roundingMode is unnecessary.

                // Are we sure this is the right scale to use? Should
                // also determine a precision-based method.
                MathContext mc = new MathContext(
                        dividend.precision() + (int) Math.ceil(10.0 * divisor.precision() / 3.0),
                        RoundingMode.UNNECESSARY);
                // Should do some more work here to rescale, etc.
                return dividend.divide(divisor, mc);
            }
        }
    }

    public static int powersOf2and5() {
        int failures = 0;

        for (int i = 0; i < 6; i++) {
            int powerOf2 = (int) StrictMath.pow(2.0, i);

            for (int j = 0; j < 6; j++) {
                int powerOf5 = (int) StrictMath.pow(5.0, j);

                try {
                    Rational.ONE.divide(new Rational(powerOf2 * powerOf5));
                } catch (ArithmeticException e) {
                    failures++;
                    System.err.println("(" + new Rational(powerOf2).toString() + ") / ("
                            + new Rational(powerOf5).toString() + ") threw an exception.");
                    e.printStackTrace();
                }

                try {
                    new Rational(powerOf2).divide(new Rational(powerOf5));
                } catch (ArithmeticException e) {
                    failures++;
                    System.err.println("(" + new Rational(powerOf2).toString() + ") / ("
                            + new Rational(powerOf5).toString() + ") threw an exception.");
                    e.printStackTrace();
                }

                try {
                    new Rational(powerOf5).divide(new Rational(powerOf2));
                } catch (ArithmeticException e) {
                    failures++;
                    System.err.println("(" + new Rational(powerOf5).toString() + ") / ("
                            + new Rational(powerOf2).toString() + ") threw an exception.");

                    e.printStackTrace();
                }

            }
        }
        return failures;
    }

    public static int nonTerminating() {
        int failures = 0;
        int[] primes = { 1, 3, 7, 13, 17 };

        // For each pair of prime products, verify the ratio of
        // non-equal products has a non-terminating expansion.

        for (int i = 0; i < primes.length; i++) {
            for (int j = i + 1; j < primes.length; j++) {

                for (int m = 0; m < primes.length; m++) {
                    for (int n = m + 1; n < primes.length; n++) {
                        int dividend = primes[i] * primes[j];
                        int divisor = primes[m] * primes[n];

                        if (((dividend / divisor) * divisor) != dividend) {
                            try {
                                BigDecimal quotient = new Rational(dividend).divide(new Rational(divisor))
                                        .toBigDecimalExact();
                                failures++;
                                System.err.println("Exact quotient " + quotient.toString()
                                        + " returned for non-terminating fraction " + dividend + " / " + divisor + ".");
                            } catch (ArithmeticException e) {
                                ; // Correct result
                            }
                        }

                    }
                }
            }
        }

        return failures;
    }

    public static int properPrecisionTests() {
        int failures = 0;

        Number[][] testCases = { { new Rational("1"), new Rational("5"), new BigDecimal("2e-1") },
                { new Rational("1"), new Rational("50e-1"), new BigDecimal("2e-1") },
                { new Rational("10e-1"), new Rational("5"), new BigDecimal("2e-1") },
                { new Rational("1"), new Rational("500e-2"), new BigDecimal("2e-1") },
                { new Rational("100e-2"), new Rational("5"), new BigDecimal("2e-1") },
                { new Rational("1"), new Rational("32"), new BigDecimal("3125e-5") },
                { new Rational("1"), new Rational("64"), new BigDecimal("15625e-6") },
                { new Rational("1.0000000"), new Rational("64"), new BigDecimal("15625e-6") }, };

        for (Number[] tc : testCases) {
            BigDecimal quotient = ((Rational) tc[0]).divide((Rational) tc[1]).toBigDecimalExact();
            if (!quotient.equals(tc[2])) {
                failures++;
                System.err.println("Unexpected quotient from " + tc[0] + " / " + tc[1] + "; expected " + tc[2] + " got "
                        + quotient);
            }
        }

        return failures;
    }

    public static int trailingZeroTests() {
        int failures = 0;

        MathContext mc = new MathContext(3, RoundingMode.FLOOR);
        Number[][] testCases = { { new Rational("19"), new Rational("100"), new BigDecimal("0.19") },
                { new Rational("21"), new Rational("110"), new BigDecimal("0.190") }, };

        for (Number[] tc : testCases) {
            BigDecimal quotient;
            if (!(quotient = ((Rational) tc[0]).divide((Rational) tc[1]).toBigDecimal(mc)).equals(tc[2])) {
                failures++;
                System.err.println("Unexpected quotient from (" + tc[0] + ") / (" + tc[1] + "); expected " + tc[2]
                        + " got " + quotient);
            }
        }

        return failures;
    }

    public static int roundedDivideTests() {
        int failures = 0;
        // Tests of divide under different rounding modes.

        // {dividend, dividisor, rounding, quotient}
        Rational a = new Rational("31415");
        Rational a_minus = a.negate();
        Rational b = new Rational("10000");

        Rational c = new Rational("31425");
        Rational c_minus = c.negate();

        // Ad hoc tests
        Rational d = new Rational("-3736167111923811891189.3939591735");
        Rational e = new Rational("74723342238476237.823787879183470");

        Object[][] testCases = { { a, b, UP_4, new BigDecimal("3.142") },
                { a_minus, b, UP_4, new BigDecimal("-3.142") },

                { a, b, DOWN_4, new BigDecimal("3.141") }, { a_minus, b, DOWN_4, new BigDecimal("-3.141") },

                { a, b, CEIL_4, new BigDecimal("3.142") }, { a_minus, b, CEIL_4, new BigDecimal("-3.141") },

                { a, b, FLOOR_4, new BigDecimal("3.141") }, { a_minus, b, FLOOR_4, new BigDecimal("-3.142") },

                { a, b, HALF_UP_4, new BigDecimal("3.142") }, { a_minus, b, HALF_UP_4, new BigDecimal("-3.142") },

                { a, b, HALF_DOWN_4, new BigDecimal("3.141") }, { a_minus, b, HALF_DOWN_4, new BigDecimal("-3.141") },

                { a, b, HALF_EVEN_4, new BigDecimal("3.142") }, { a_minus, b, HALF_EVEN_4, new BigDecimal("-3.142") },

                { c, b, HALF_EVEN_4, new BigDecimal("3.142") }, { c_minus, b, HALF_EVEN_4, new BigDecimal("-3.142") },

                { d, e, new MathContext(1, RoundingMode.HALF_UP), BigDecimal.valueOf(-5, -4) },
                { d, e, new MathContext(1, RoundingMode.HALF_DOWN), BigDecimal.valueOf(-5, -4) },
                { d, e, new MathContext(1, RoundingMode.HALF_EVEN), BigDecimal.valueOf(-5, -4) }, };

        for (Object[] tc : testCases) {
            MathContext mc = (MathContext) tc[2];

            BigDecimal quotient = ((Rational) tc[0]).divide((Rational) tc[1]).toBigDecimal(mc);
            if (!quotient.equals(tc[3])) {
                failures++;
                System.err.println("Unexpected quotient from " + tc[0] + " / " + tc[1] + " precision "
                        + mc.getPrecision() + " rounding mode " + mc.getRoundingMode() + "; expected " + tc[3] + " got "
                        + quotient);
            }
        }

        Object[][] testCases2 = {
                // {dividend, dividisor, rounding, quotient}
                { new Rational(3090), new Rational(7), HALF_UP_3, new BigDecimal(441) },
                { new Rational("309000000000000000000000"), new Rational("700000000000000000000"), HALF_UP_3,
                        new BigDecimal(441) },
                { new Rational("962.430000000000"), new Rational("8346463.460000000000"), new MathContext(9),
                        new BigDecimal("0.000115309916") },
                { new Rational("18446744073709551631"), new Rational("4611686018427387909"), HALF_UP_1,
                        new BigDecimal(4) },
                { new Rational("18446744073709551630"), new Rational("4611686018427387909"), HALF_UP_1,
                        new BigDecimal(4) },
                { new Rational("23058430092136939523"), new Rational("4611686018427387905"), HALF_UP_1,
                        new BigDecimal(5) },
                { new Rational("-18446744073709551661"), new Rational("-4611686018427387919"), HALF_UP_1,
                        new BigDecimal(4) },
                { new Rational("-18446744073709551660"), new Rational("-4611686018427387919"), HALF_UP_1,
                        new BigDecimal(4) }, };

        for (Object[] test : testCases2) {
            MathContext mc = (MathContext) test[2];
            BigDecimal quo = ((Rational) test[0]).divide((Rational) test[1]).toBigDecimal(mc);
            if (!quo.equals(test[3])) {
                failures++;
                System.err.println("Unexpected quotient from " + test[0] + " / " + test[1] + " rounding mode HALF_UP"
                        + "; expected " + test[3] + " got " + quo);
            }
        }
        return failures;
    }

    public static void main(String argv[]) {
        int failures = 0;

        failures += powersOf2and5();
        failures += nonTerminating();
        failures += properPrecisionTests();
        failures += trailingZeroTests();
        failures += roundedDivideTests();

        if (failures > 0) {
            throw new RuntimeException("Incurred " + failures + " failures while testing division.");
        }
    }
}
