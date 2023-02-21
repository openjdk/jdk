/*
 * @test
 * @library /test/lib
 * @summary tests root(.) and rootAndRemainder(.) methods in BigInteger
 */

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.AbstractMap.SimpleEntry;
import java.util.Map;
import java.util.Random;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

public class RootTest {

    static final int SIZE = 100; // numbers per batch

    private static Random random;
    
    static {
        long seed = new Random().nextLong();
        System.out.println("Random seed: " + seed);
        random = new Random(seed);
    }

    static boolean failure = false;

    private static void printErr(String msg) {
        System.err.println(msg);
    }

    private static int checkResult(BigInteger expected, BigInteger actual,
        String failureMessage) {
        if (expected.compareTo(actual) != 0) {
            printErr(failureMessage + " - expected: " + expected
                + ", actual: " + actual);
            return 1;
        }
        return 0;
    }

    private static void rootSmall() {
        int failCount = 0;

        // A negative value with even degree should cause an exception.
        BigInteger x = BigInteger.ONE.negate();
        for (int n = 0; n <= 100; n += 2) {
            try {
                x.root(n);
                // If root(n) does not throw an exception that is a failure.
                failCount++;
                printErr("even-degree root of negative number did not throw an exception");
            } catch (ArithmeticException expected) {
                // expected
            }
        }
        
        // A negative value with odd degree should return a negative value.
        for (int n = 1; n <= 100; n += 2) {
            for (int i = -10; i < 0; i++) {
                int sign = BigInteger.valueOf(i).root(n).signum();
                if (sign != -1) {
                    failCount++;
                    printErr("odd-degree root of negative number did return a value with sign: " + sign);
                }
            }
        }
        
        // A negative or zero degree should cause an exception.
        for (int n = -100; n <= 0; n++) {
            try {
                x.root(n);
                // If root(n) does not throw an exception that is a failure.
                failCount++;
                printErr("non-positive degree root did not throw an exception");
            } catch (ArithmeticException expected) {
                // expected
            }
        }

        // A zero value should return BigInteger.ZERO.
        for (int n = 1; n <= 100; n++)
            failCount += checkResult(BigInteger.ZERO, BigInteger.ZERO.root(n),
                    "root(0, n) != BigInteger.ZERO");

        // 1 <= value < 2^n should return BigInteger.ONE.
        for (int n = 1; n <= 20; n++) {
            int last = (1 << n) - 1;
            for (int i = 1; i <= last; i++) {
                failCount += checkResult(BigInteger.ONE,
                    BigInteger.valueOf(i).root(n), "root("+i+", "+n+") != 1");
            }
        }

        report("rootSmall", failCount);
    }

    public static void root() {
        ToIntFunction<Map.Entry<BigInteger, Integer>> f = (pair) -> {
            int failCount = 0;
            BigInteger x = pair.getKey();
            int n = pair.getValue();
            System.out.println(x + ", " + n);

            // nth root of x^n -> x
            BigInteger xToN = x.pow(n);
            failCount += checkResult(x, xToN.root(n), "root(n) x^" + n + " -> x");

            // nth root of x^n + 1 -> x
            BigInteger xToNup = xToN.add(BigInteger.ONE);
            failCount += checkResult(x, xToNup.root(n), "root(n) x^" + n + " + 1 -> x");

            // nth root of (x + 1)^n - 1 -> x
            BigInteger up =
                x.add(BigInteger.ONE).pow(n).subtract(BigInteger.ONE);
            failCount += checkResult(x, up.root(n), "root(n) (x + 1)^" + n + " - 1 -> x");

            // root(x, n)^n <= x
            BigInteger s = x.root(n);
            if (s.pow(n).compareTo(x) > 0) {
                failCount++;
                printErr("root(x, n)^n > x for x = " + x + ", n = " + n);
            }

            // (root(x, n) + 1)^n > x
            if (s.add(BigInteger.ONE).pow(n).compareTo(x) <= 0) {
                failCount++;
                printErr("(root(x, n) + 1)^n <= x for x = " + x + ", n = " + n);
            }

            return failCount;
        };

        Stream.Builder<Map.Entry<BigInteger, Integer>> sb = Stream.builder();
        int maxExponent = 64;
        for (int i = 1; i <= maxExponent; i++) {
            for (int n = 2; n <= 100; n++) {
                BigInteger p2 = BigInteger.ONE.shiftLeft(i);
                sb.add(new SimpleEntry<>(p2.subtract(BigInteger.ONE), n));
                sb.add(new SimpleEntry<>(p2, n));
                sb.add(new SimpleEntry<>(p2.add(BigInteger.ONE), n));
            }
        }
        
        for (int n = 2; n <= 30; n++) {
            sb.add(new SimpleEntry<>(new BigDecimal(Double.MAX_VALUE).toBigInteger(), n));
            sb.add(new SimpleEntry<>(new BigDecimal(Double.MAX_VALUE).toBigInteger().add(BigInteger.ONE), n));
        }
        
        report("roots for 2^N and 2^N - 1, 1 <= N <= Double.MAX_EXPONENT",
            sb.build().collect(Collectors.summingInt(f)));

        
        
        for (int n = 2; n <= 30; n++) {
            IntStream ints = random.ints(SIZE, 4, Integer.MAX_VALUE);
            LongStream longs = random.longs(SIZE, (long)Integer.MAX_VALUE + 1L,
                    Long.MAX_VALUE);
            DoubleStream doubles = random.doubles(SIZE,
                    (double) Long.MAX_VALUE + 1.0, Math.sqrt(Double.MAX_VALUE));
            final int deg = n;
            report("roots for int", ints.mapToObj(x ->
                new SimpleEntry<>(BigInteger.valueOf(x), deg)).collect(Collectors.summingInt(f)));
            
            report("roots for long", longs.mapToObj(x ->
                new SimpleEntry<>(BigInteger.valueOf(x), deg)).collect(Collectors.summingInt(f)));

        
            report("roots for double", doubles.mapToObj(x ->
            new SimpleEntry<>(BigDecimal.valueOf(x).toBigInteger(), deg)).collect(Collectors.summingInt(f)));
        }
    }

    public static void rootAndRemainder() {
        ToIntFunction<Map.Entry<BigInteger, Integer>> g = (pair) -> {
            int failCount = 0;
            BigInteger x = pair.getKey();
            int n = pair.getValue();
            
            BigInteger xToN = x.pow(n);

            // root of x^n -> x
            BigInteger[] actual = xToN.rootAndRemainder(n);
            failCount += checkResult(x, actual[0], "rootAndRemainder()[0], n = " + n);
            failCount += checkResult(BigInteger.ZERO, actual[1],
                "rootAndRemainder()[1], n = " + n);

            // root of x^n + 1 -> x
            BigInteger xToNup = xToN.add(BigInteger.ONE);
            actual = xToNup.rootAndRemainder(n);
            failCount += checkResult(x, actual[0], "rootAndRemainder()[0], n = " + n );
            failCount += checkResult(BigInteger.ONE, actual[1],
                "rootAndRemainder()[1], n = " + n);

            // square root of (x + 1)^n - 1 -> x
            BigInteger up =
                x.add(BigInteger.ONE).pow(n).subtract(BigInteger.ONE);
            actual = up.rootAndRemainder(n);
            failCount += checkResult(x, actual[0], "rootAndRemainder()[0], n = " + n);
            BigInteger r = up.subtract(xToN);
            failCount += checkResult(r, actual[1], "rootAndRemainder()[1], n = " + n);

            return failCount;
        };
        
        for(int n = 2; n <= 100; n++) {
            IntStream bits = random.ints(SIZE, 3, Short.MAX_VALUE);
            final int deg = n;
            report("sqrtAndRemainder", bits.mapToObj(x ->
                new SimpleEntry<>(BigInteger.valueOf(x), deg)).collect(Collectors.summingInt(g)));
        }
        
    }

    public static void main(String[] args) throws Exception {
        rootSmall();
        root();
        rootAndRemainder();

        if (failure)
            throw new RuntimeException("Failure in RootTest.");
    }

    static void report(String testName, int failCount) {
        System.err.println(testName+": " +
                           (failCount==0 ? "Passed":"Failed("+failCount+")"));
        if (failCount > 0)
            failure = true;
    }
}
