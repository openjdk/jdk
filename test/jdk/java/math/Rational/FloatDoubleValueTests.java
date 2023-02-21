
/*
 * @test
 * @summary Verify {float, double}Value methods work with condensed representation
 * @run main FloatDoubleValueTests
 * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions -XX:+EliminateAutoBox -XX:AutoBoxCacheMax=20000 FloatDoubleValueTests
 */
import java.math.*;
import static java.math.Rational.*;

public class FloatDoubleValueTests {
    private static final long two2the24 = 1L << 23;
    private static final long two2the53 = 1L << 52;

    // Largest long that fits exactly in a float
    private static final long maxFltLong = Integer.MAX_VALUE & ~(0xff);

    // Largest long that fits exactly in a double
    private static final long maxDblLong = Long.MAX_VALUE & ~(0x7ffL);

    static void testDoubleValue0(long i, Rational r) {
        if (r.doubleValue() != i || r.longValue() != i)
            throw new RuntimeException("Unexpected equality failure for " + i + "\t" + r);
    }

    static void testFloatValue0(long i, Rational r) {
        if (r.floatValue() != i || r.longValue() != i)
            throw new RuntimeException("Unexpected equality failure for " + i + "\t" + r);
    }

    static void checkFloat(Rational r, float f) {
        float fbd = r.floatValue();
        if (f != fbd) {
            String message = String.format("Bad conversion:" + "got %g (%a)\texpected %g (%a)", f, f, fbd, fbd);
            throw new RuntimeException(message);
        }
    }

    static void checkDouble(Rational r, double d) {
        double dbd = r.doubleValue();

        if (d != dbd) {
            String message = String.format("Bad conversion:" + "got %g (%a)\texpected %g (%a)", d, d, dbd, dbd);
            throw new RuntimeException(message);
        }
    }

    // Test integral values that will convert exactly to both float
    // and double.
    static void testFloatDoubleValue() {
        long longValues[] = { Long.MIN_VALUE, // -2^63
                0, 1, 2,

                two2the24 - 1, two2the24, two2the24 + 1,

                maxFltLong - 1, maxFltLong, maxFltLong + 1, };

        for (long i : longValues) {
            Rational r1 = new Rational(i);
            Rational r2 = new Rational(-i);

            testDoubleValue0(i, r1);
            testDoubleValue0(-i, r2);

            testFloatValue0(i, r1);
            testFloatValue0(-i, r2);
        }

    }

    static void testDoubleValue() {
        long longValues[] = { Integer.MAX_VALUE - 1, Integer.MAX_VALUE, (long) Integer.MAX_VALUE + 1,

                two2the53 - 1, two2the53, two2the53 + 1,

                maxDblLong, };

        // Test integral values that will convert exactly to double
        // but not float.
        for (long i : longValues) {
            Rational r1 = new Rational(i);
            Rational r2 = new Rational(-i);

            testDoubleValue0(i, r1);
            testDoubleValue0(-i, r2);

            checkFloat(r1, i);
            checkFloat(r2, -(float) i);
        }

        // Now check values that should not convert the same in double
        for (long i = maxDblLong; i < Long.MAX_VALUE; i++) {
            Rational r1 = new Rational(i);
            Rational r2 = new Rational(-i);
            checkDouble(r1, i);
            checkDouble(r2, -(double) i);

            checkFloat(r1, i);
            checkFloat(r2, -(float) i);
        }

        checkDouble(new Rational(Long.MIN_VALUE), Long.MIN_VALUE);
        checkDouble(new Rational(Long.MAX_VALUE), Long.MAX_VALUE);
    }

    static void testFloatValue() {
        // Now check values that should not convert the same in float
        for (long i = maxFltLong; i <= Integer.MAX_VALUE; i++) {
            Rational r1 = new Rational(i);
            Rational r2 = new Rational(-i);
            checkFloat(r1, i);
            checkFloat(r2, -(float) i);

            testDoubleValue0(i, r1);
            testDoubleValue0(-i, r2);
        }
    }

    static void testFloatValue1() {
        checkFloat(new Rational("85070591730234615847396907784232501249"), 8.507059e+37f);
        checkFloat(new Rational("7784232501249e12"), 7.7842326e24f);
        checkFloat(new Rational("907784232501249e-12"), 907.78424f);
        checkFloat(new Rational("7784e8"), 7.7839997e11f);
        checkFloat(new Rational("9077e-8"), 9.077e-5f);

    }

    static void testDoubleValue1() {
        checkDouble(new Rational("85070591730234615847396907784232501249"), 8.507059173023462e37);
        checkDouble(new Rational("7784232501249e12"), 7.784232501249e24);
        checkDouble(new Rational("907784232501249e-12"), 907.784232501249);
        checkDouble(new Rational("7784e8"), 7.784e11);
        checkDouble(new Rational("9077e-8"), 9.077e-5);

    }

    private static void rudimentaryDoubleTest() {
        checkDouble(new Rational("" + Double.MIN_VALUE), Double.MIN_VALUE);
        checkDouble(new Rational("" + Double.MAX_VALUE), Double.MAX_VALUE);

        checkDouble(new Rational("10"), 10.0);
        checkDouble(new Rational("10.0"), 10.0);
        checkDouble(new Rational("10.01"), 10.01);

        checkDouble(new Rational("-10"), -10.0);
        checkDouble(new Rational("-10.00"), -10.0);
        checkDouble(new Rational("-10.01"), -10.01);
    }

    private static void rudimentaryFloatTest() {
        checkFloat(new Rational("" + Float.MIN_VALUE), Float.MIN_VALUE);
        checkFloat(new Rational("" + Float.MAX_VALUE), Float.MAX_VALUE);

        checkFloat(new Rational("10"), 10.0f);
        checkFloat(new Rational("10.0"), 10.0f);
        checkFloat(new Rational("10.01"), 10.01f);

        checkFloat(new Rational("-10"), -10.0f);
        checkFloat(new Rational("-10.00"), -10.0f);
        checkFloat(new Rational("-10.01"), -10.01f);

        checkFloat(new Rational("144115196665790480"), 0x1.000002p57f);
        checkFloat(new Rational("144115196665790481"), 0x1.000002p57f);
        checkFloat(new Rational("0.050000002607703203"), 0.05f);
        checkFloat(new Rational("0.050000002607703204"), 0.05f);
        checkFloat(new Rational("0.050000002607703205"), 0.05f);
        checkFloat(new Rational("0.050000002607703206"), 0.05f);
        checkFloat(new Rational("0.050000002607703207"), 0.05f);
        checkFloat(new Rational("0.050000002607703208"), 0.05f);
        checkFloat(new Rational("0.050000002607703209"), 0.050000004f);
    }

    /**
     * For each subnormal power of two, test at boundaries of region that should
     * convert to that value.
     */
    private static void testSubnormalDoublePowers() {
        boolean failed = false;
        // An ulp is the same for all subnormal values
        Rational ulp_R = new Rational(Double.MIN_VALUE);

        // Test subnormal powers of two (except Double.MIN_VALUE)
        for (int i = -1073; i <= -1022; i++) {
            double d = Math.scalb(1.0, i);

            /*
             * The region [d - ulp/2, d + ulp/2] should round to d.
             */
            Rational d_R = new Rational(d);

            Rational lowerBound = d_R.subtract(ulp_R.divide(TWO));
            Rational upperBound = d_R.add(ulp_R.divide(TWO));

            double convertedLowerBound = lowerBound.doubleValue();
            double convertedUpperBound = upperBound.doubleValue();
            if (convertedLowerBound != d) {
                failed = true;
                System.out.printf("2^%d lowerBound converts as %a %s%n", i, convertedLowerBound, lowerBound);
            }
            if (convertedUpperBound != d) {
                failed = true;
                System.out.printf("2^%d upperBound converts as %a %s%n", i, convertedUpperBound, upperBound);
            }
        }
        /*
         * Double.MIN_VALUE The region ]0.5*Double.MIN_VALUE, 1.5*Double.MIN_VALUE[
         * should round to Double.MIN_VALUE.
         */
        Rational minValue = new Rational(Double.MIN_VALUE);
        if (minValue.multiply(new Rational(0.5)).doubleValue() != 0.0) {
            failed = true;
            System.out.printf("0.5*MIN_VALUE doesn't convert 0%n");
        }
        if (minValue.multiply(new Rational(0.5000000000000001)).doubleValue() != Double.MIN_VALUE) {
            failed = true;
            System.out.printf("0.5000000000000001*MIN_VALUE doesn't convert to MIN_VALUE%n");
        }
        if (minValue.multiply(new Rational(1.499999999999999)).doubleValue() != Double.MIN_VALUE) {
            failed = true;
            System.out.printf("1.499999999999999*MIN_VALUE doesn't convert to MIN_VALUE%n");
        }
        if (minValue.multiply(new Rational(1.5)).doubleValue() != 2 * Double.MIN_VALUE) {
            failed = true;
            System.out.printf("1.5*MIN_VALUE doesn't convert to 2*MIN_VALUE%n");
        }

        if (failed)
            throw new RuntimeException("Inconsistent conversion");
    }

    /**
     * For each power of two, test at boundaries of region that should convert to
     * that value.
     */
    private static void testSubnormalFloatPowers() {
        boolean failed = false;
        // An ulp is the same for all subnormal values
        Rational ulp_R = new Rational(Float.MIN_VALUE);

        // Test subnormal powers of two (except Float.MIN_VALUE)
        for (int i = -148; i <= -126; i++) {
            float f = Math.scalb(1.0f, i);

            /*
             * The region [f - ulp/2, f + ulp/2] should round to f.
             */
            Rational f_R = new Rational(f);

            Rational lowerBound = f_R.subtract(ulp_R.divide(TWO));
            Rational upperBound = f_R.add(ulp_R.divide(TWO));

            float convertedLowerBound = lowerBound.floatValue();
            float convertedUpperBound = upperBound.floatValue();
            if (convertedLowerBound != f) {
                failed = true;
                System.out.printf("2^%d lowerBound converts as %a %s%n", i, convertedLowerBound, lowerBound);
            }
            if (convertedUpperBound != f) {
                failed = true;
                System.out.printf("2^%d upperBound converts as %a %s%n", i, convertedUpperBound, upperBound);
            }
        }

        /*
         * Float.MIN_VALUE The region ]0.5*Float.MIN_VALUE, 1.5*Float.MIN_VALUE[ should
         * round to Float.MIN_VALUE.
         */
        Rational minValue = new Rational(Float.MIN_VALUE);
        if (minValue.multiply(new Rational(0.5f)).floatValue() != 0.0f) {
            failed = true;
            System.out.printf("0.5*MIN_VALUE doesn't convert 0%n");
        }
        if (minValue.multiply(new Rational(0.5000001f)).floatValue() != Float.MIN_VALUE) {
            failed = true;
            System.out.printf("0.5000001*MIN_VALUE doesn't convert to MIN_VALUE%n");
        }
        if (minValue.multiply(new Rational(1.4999999f)).floatValue() != Float.MIN_VALUE) {
            failed = true;
            System.out.printf("1.4999999*MIN_VALUE doesn't convert to MIN_VALUE%n");
        }
        if (minValue.multiply(new Rational(1.5f)).floatValue() != 2 * Float.MIN_VALUE) {
            failed = true;
            System.out.printf("1.5*MIN_VALUE doesn't convert to 2*MIN_VALUE%n");
        }

        if (failed)
            throw new RuntimeException("Inconsistent conversion");
    }

    /**
     * For each normal power of two, test at boundaries of region that should
     * convert to that value.
     */
    private static void testNormalDoublePowers() {
        boolean failed = false;

        for (int i = -1021; i <= 1023; i++) {
            double d = Math.scalb(1.0, i);
            Rational d_R = new Rational(d);

            Rational lowerBound = d_R.subtract(new Rational(Math.ulp(Math.nextUp(-d))).divide(TWO));
            Rational upperBound = d_R.add(new Rational(Math.ulp(d)).divide(TWO));

            double convertedLowerBound = lowerBound.doubleValue();
            double convertedUpperBound = upperBound.doubleValue();
            if (convertedLowerBound != d) {
                failed = true;
                System.out.printf("2^%d lowerBound converts as %a %s%n", i, convertedLowerBound, lowerBound);
            }
            if (convertedUpperBound != d) {
                failed = true;
                System.out.printf("2^%d upperBound converts as %a %s%n", i, convertedUpperBound, upperBound);
            }
        }

        if (failed)
            throw new RuntimeException("Inconsistent conversion");
    }

    /**
     * For each normal power of two, test at boundaries of region that should
     * convert to that value.
     */
    private static void testNormalFloatPowers() {
        boolean failed = false;

        for (int i = -125; i <= 127; i++) {
            float f = Math.scalb(1.0f, i);
            Rational d_R = new Rational(f);

            Rational lowerBound = d_R.subtract(new Rational(Math.ulp(Math.nextUp(-f))).divide(TWO));
            Rational upperBound = d_R.add(new Rational(Math.ulp(f)).divide(TWO));

            float convertedLowerBound = lowerBound.floatValue();
            float convertedUpperBound = upperBound.floatValue();
            if (convertedLowerBound != f) {
                failed = true;
                System.out.printf("2^%d lowerBound converts as %a %s%n", i, convertedLowerBound, lowerBound);
            }
            if (convertedUpperBound != f) {
                failed = true;
                System.out.printf("2^%d upperBound converts as %a %s%n", i, convertedUpperBound, upperBound);
            }
        }

        if (failed)
            throw new RuntimeException("Inconsistent conversion");
    }

    private static void testStrictness() {
        final double expected = 0x0.0000008000000p-1022;
//        final double expected = 0x0.0000008000001p-1022;
        boolean failed = false;
        double sum = 0.0; // Prevent conversion from being optimized away

        // 2^-1047 + 2^-1075 rounds to 2^-1047
        String decimal = "6.631236871469758276785396630275967243399099947355303144249971758736286630139265439618068200788048744105960420552601852889715006376325666595539603330361800519107591783233358492337208057849499360899425128640718856616503093444922854759159988160304439909868291973931426625698663157749836252274523485312442358651207051292453083278116143932569727918709786004497872322193856150225415211997283078496319412124640111777216148110752815101775295719811974338451936095907419622417538473679495148632480391435931767981122396703443803335529756003353209830071832230689201383015598792184172909927924176339315507402234836120730914783168400715462440053817592702766213559042115986763819482654128770595766806872783349146967171293949598850675682115696218943412532098591327667236328125E-316";

        for (int i = 0; i <= 12_000; i++) {
            double conversion = new Rational(decimal).doubleValue();
            sum += conversion;
            if (conversion != expected) {
                failed = true;
                System.out.printf("Iteration %d converts as %a%n", i, conversion);
            }
        }

        System.out.println("Sum = " + sum);
        if (failed)
            throw new RuntimeException("Inconsistent conversion");
    }

    public static void main(String[] args) throws Exception {
        rudimentaryDoubleTest();
        rudimentaryFloatTest();

        testSubnormalDoublePowers();
        testSubnormalFloatPowers();
        testNormalDoublePowers();
        testNormalFloatPowers();
        testStrictness();

        testFloatDoubleValue();
        testDoubleValue();
        testFloatValue();
        testFloatValue1();
        testDoubleValue1();
    }
}
