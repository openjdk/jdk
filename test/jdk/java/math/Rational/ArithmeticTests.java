/*
 * @test
 * @summary Some tests of add(Rational)
 */

import static java.math.Rational.*;

import java.math.*;
import java.util.Random;

public class ArithmeticTests {

    static final int ORDER_SMALL = 60;
    static final int ORDER_MEDIUM = 100;
    // #bits for testing Karatsuba
    static final int ORDER_KARATSUBA = 2760;
    // #bits for testing Toom-Cook and Burnikel-Ziegler
    static final int ORDER_TOOM_COOK = 8000;

    static final int SIZE = 1000; // numbers per batch

    private static Random random = new Random();

    /**
     * Test for some simple additions, particularly, it will test the overflow case.
     */
    private static int simpleTests() {
        int failures = 0;

        Rational[] bd1 = { new Rational("78124046.66936930160"), new Rational("7812404.666936930160"),
                new Rational("781240.4666936930160"), };
        Rational bd2 = new Rational("279000.0");
        Rational[] expectedResult = { new Rational("78403046.66936930160"), new Rational("8091404.666936930160"),
                new Rational("1060240.4666936930160"), };
        for (int i = 0; i < bd1.length; i++) {
            if (!bd1[i].add(bd2).equals(expectedResult[i]))
                failures++;
        }
        return failures;
    }

    private static int arithmeticExceptionTest() {
        int failures = 0;
        try {
            //
            // The string representation "1e2147483647", which is equivalent
            // to 10^Integer.MAX_VALUE, is used to create an augend too big
            // to be represented into a Rational, so the program must throw
            // an ArithmeticException
            //
            new Rational("1e2147483647").add(new Rational(1));
            failures++;
        } catch (ArithmeticException e) {
        }
        return failures;
    }

    /*
     * Get a random or boundary-case number. This is designed to provide a lot of
     * numbers that will find failure points, such as max sized numbers, empty
     * BigIntegers, etc.
     *
     * If order is less than 2, order is changed to 2.
     */
    private static BigInteger fetchInteger(int order) {
        boolean negative = random.nextBoolean();
        int numType = random.nextInt(7);
        BigInteger result = null;
        if (order < 2)
            order = 2;

        switch (numType) {
        case 0: // Empty
            result = BigInteger.ZERO;
            break;

        case 1: // One
            result = BigInteger.ONE;
            break;

        case 2: // All bits set in number
            int numBytes = (order + 7) / 8;
            byte[] fullBits = new byte[numBytes];
            for (int i = 0; i < numBytes; i++)
                fullBits[i] = (byte) 0xff;
            int excessBits = 8 * numBytes - order;
            fullBits[0] &= (1 << (8 - excessBits)) - 1;
            result = new BigInteger(1, fullBits);
            break;

        case 3: // One bit in number
            result = BigInteger.ONE.shiftLeft(random.nextInt(order));
            break;

        case 4: // Random bit density
            byte[] val = new byte[(order + 7) / 8];
            int iterations = random.nextInt(order);
            for (int i = 0; i < iterations; i++) {
                int bitIdx = random.nextInt(order);
                val[bitIdx / 8] |= 1 << (bitIdx % 8);
            }
            result = new BigInteger(1, val);
            break;
        case 5: // Runs of consecutive ones and zeros
            result = BigInteger.ZERO;
            int remaining = order;
            int bit = random.nextInt(2);
            while (remaining > 0) {
                int runLength = Math.min(remaining, random.nextInt(order));
                result = result.shiftLeft(runLength);
                if (bit > 0)
                    result = result.add(BigInteger.ONE.shiftLeft(runLength).subtract(BigInteger.ONE));
                remaining -= runLength;
                bit = 1 - bit;
            }
            break;

        default: // random bits
            result = new BigInteger(order, random);
        }

        if (negative)
            result = result.negate();

        return result;
    }

    private static Rational fetchNumber(int order) {
        BigInteger x = fetchInteger(order);
        BigInteger y = fetchInteger(order / 2);

        if (y.signum() == 0)
            y = y.add(BigInteger.ONE);

        return valueOf(x, y);
    }

    public static int arithmetic(int order) {
        int failCount = 0;

        for (int i = 0; i < SIZE; i++) {
            Rational x = fetchNumber(order);
            while (x.signum() != 1)
                x = fetchNumber(order);
            Rational y = fetchNumber(order / 2);
            while (x.compareTo(y) < 0)
                y = fetchNumber(order / 2);
            if (y.signum() == 0)
                y = y.add(ONE);

            // Test identity ((x/y))*y + x%y - x == 0
            // using separate divideToIntegralValue() and remainder()
            Rational baz = x.divideToIntegralValue(y);
            baz = baz.multiply(y);
            baz = baz.add(x.remainder(y));
            baz = baz.subtract(x);
            if (!baz.equals(ZERO))
                failCount++;
        }
        report("Arithmetic I for " + order + " bits", failCount);

        failCount = 0;
        for (int i = 0; i < 100; i++) {
            Rational x = fetchNumber(order);
            while (x.signum() != 1)
                x = fetchNumber(order);
            Rational y = fetchNumber(order / 2);
            while (x.compareTo(y) < 0)
                y = fetchNumber(order / 2);
            if (y.signum() == 0)
                y = y.add(ONE);

            // Test identity ((x/y))*y + x%y - x == 0
            // using divideAndRemainder()
            Rational[] baz = x.divideAndRemainder(y);
            baz[0] = baz[0].multiply(y);
            baz[0] = baz[0].add(baz[1]);
            baz[0] = baz[0].subtract(x);
            if (!baz[0].equals(ZERO))
                failCount++;
        }
        report("Arithmetic II for " + order + " bits", failCount);

        return failCount;
    }

    static void report(String testName, int failCount) {
        System.err.println(testName + ": " + (failCount == 0 ? "Passed" : "Failed(" + failCount + ")"));
    }

    public static void main(String argv[]) {
        int failures = 0;

        // Some variables for sizing test numbers in bits
        int order1 = ORDER_MEDIUM;
        int order2 = ORDER_SMALL;
        int order3 = ORDER_KARATSUBA;
        int order4 = ORDER_TOOM_COOK;

        failures += simpleTests();
        failures += arithmeticExceptionTest();

        failures += arithmetic(order1); // medium numbers
        failures += arithmetic(order2); // small numbers
        failures += arithmetic(order3); // Karatsuba range
        failures += arithmetic(order4); // Toom-Cook / Burnikel-Ziegler range

        if (failures > 0)
            throw new RuntimeException("Incurred " + failures + " failures while testing arithmetic operations.");
    }
}
