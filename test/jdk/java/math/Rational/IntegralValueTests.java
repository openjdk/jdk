
/*
 * @test
 * @summary Tests of Rational.intValue() and Rational.longValue()
 */
import java.math.Rational;
import java.util.Map;

public class IntegralValueTests {
    public static void main(String... args) {
        int failures = integralValuesTest(INT_VALUES, true) + integralValuesTest(LONG_VALUES, false);
        if (failures != 0) {
            throw new RuntimeException("Incurred " + failures + " failures for {int,long}Value().");
        }
    }

    private static final Map<String, Number> INT_VALUES = Map.ofEntries(

            // 2**31 - 1
            Map.entry("2147483647", Integer.MAX_VALUE), Map.entry("2147483647.0", Integer.MAX_VALUE),
            Map.entry("2147483647.00", Integer.MAX_VALUE),

            Map.entry("-2147483647", -Integer.MAX_VALUE), Map.entry("-2147483647.0", -Integer.MAX_VALUE),

            // -2**31
            Map.entry("-2147483648", Integer.MIN_VALUE), Map.entry("-2147483648.1", Integer.MIN_VALUE),
            Map.entry("-2147483648.01", Integer.MIN_VALUE),

            // -2**31 + 1 truncation to 2**31 - 1
            Map.entry("-2147483649", Integer.MAX_VALUE),

            // 2**64 - 1 truncation to 1
            Map.entry("4294967295", -1),

            // 2**64 truncation to 0
            Map.entry("4294967296", 0),

            // truncation to 0
            Map.entry("1e32", 0),

            // truncation to -2**31
            Map.entry("1e31", Integer.MIN_VALUE),

            Map.entry("1e0", 1),

            // round to 0
            Map.entry("9e-1", 0),

            // Some random values
            Map.entry("900e-1", 90), // Increasing negative exponents
            Map.entry("900e-2", 9), Map.entry("900e-3", 0),

            // round to 0
            Map.entry("123456789e-9", 0),

            // round to 1
            Map.entry("123456789e-8", 1),

            // Increasing positive exponents
            Map.entry("10000001e1", 100000010), Map.entry("10000001e10", -1315576832), Map.entry("10000001e100", 0),
            Map.entry("10000001e1000", 0), Map.entry("10000001e10000", 0), Map.entry("10000001e100000", 0),
            Map.entry("10000001e1000000", 0), Map.entry("10000001e10000000", 0),

            // Increasing negative exponents
            Map.entry("10000001e-1", 1000000), Map.entry("10000001e-10", 0), Map.entry("10000001e-100", 0),
            Map.entry("10000001e-1000", 0), Map.entry("10000001e-10000", 0), Map.entry("10000001e-100000", 0),
            Map.entry("10000001e-1000000", 0), Map.entry("10000001e-10000000", 0),

            // Currency calculation to 4 places
            Map.entry("12345.0001", 12345), Map.entry("12345.9999", 12345), Map.entry("-12345.0001", -12345),
            Map.entry("-12345.9999", -12345));

    private static final Map<String, Number> LONG_VALUES = Map.ofEntries(
            // 2**63 - 1
            Map.entry("9223372036854775807", Long.MAX_VALUE), Map.entry("9223372036854775807.0", Long.MAX_VALUE),
            Map.entry("9223372036854775807.00", Long.MAX_VALUE),

            // 2**63 truncation to -2**63
            Map.entry("-9223372036854775808", Long.MIN_VALUE), Map.entry("-9223372036854775808.1", Long.MIN_VALUE),
            Map.entry("-9223372036854775808.01", Long.MIN_VALUE),

            // -2**63 + 1 truncation to 2**63 - 1
            Map.entry("-9223372036854775809", 9223372036854775807L),

            // 2**64 - 1 truncation to -1
            Map.entry("18446744073709551615", -1L),

            // 2**64 truncation to 0
            Map.entry("18446744073709551616", 0L),

            // truncation to -2**63
            Map.entry("1e63", -9223372036854775808L), Map.entry("-1e63", -9223372036854775808L),
            // larger magnitude scale
            Map.entry("1e64", 0L), Map.entry("-1e64", 0L), Map.entry("1e65", 0L), Map.entry("-1e65", 0L),

            Map.entry("1e0", 1L),

            // round to 0
            Map.entry("9e-1", 0L),

            // Some random values
            Map.entry("900e-1", 90L), // Increasing negative exponents
            Map.entry("900e-2", 9L), Map.entry("900e-3", 0L),

            // round to 0
            Map.entry("123456789e-9", 0L),

            // round to 1
            Map.entry("123456789e-8", 1L),

            // Increasing positive exponents
            Map.entry("10000001e1", 100000010L), Map.entry("10000001e10", 100000010000000000L),
            Map.entry("10000001e100", 0L), Map.entry("10000001e1000", 0L), Map.entry("10000001e10000", 0L),
            Map.entry("10000001e100000", 0L), Map.entry("10000001e1000000", 0L), Map.entry("10000001e10000000", 0L),

            // Increasing negative exponents
            Map.entry("10000001e-1", 1000000L), Map.entry("10000001e-10", 0L), Map.entry("10000001e-100", 0L),
            Map.entry("10000001e-1000", 0L), Map.entry("10000001e-10000", 0L), Map.entry("10000001e-100000", 0L),
            Map.entry("10000001e-1000000", 0L), Map.entry("10000001e-10000000", 0L),

            // Currency calculation to 4 places
            Map.entry("12345.0001", 12345L), Map.entry("12345.9999", 12345L), Map.entry("-12345.0001", -12345L),
            Map.entry("-12345.9999", -12345L));

    private static int integralValuesTest(Map<String, Number> v, boolean isInt) {
        System.err.format("Testing %s%n", isInt ? "Integer" : "Long");
        int failures = 0;
        for (var testCase : v.entrySet()) {
            Rational r = new Rational(testCase.getKey());
            Number expected = testCase.getValue();
            try {
                if (isInt) {
                    int intValue = r.intValue();
                    if (intValue != (int) expected) {
                        failures += reportError(r, expected, intValue, isInt);
                    }
                } else {
                    long longValue = r.longValue();
                    if (longValue != (long) expected) {
                        failures += reportError(r, expected, longValue, isInt);
                    }
                }
            } catch (Exception e) {
                failures++;
                System.err.format("Unexpected exception %s for %s%n", e, r.toString());
            }
        }
        return failures;
    }

    private static int reportError(Rational r, Number expected, long longValue, boolean isInt) {
        System.err.format("For %s expected %d, actual %d, simple %d%n", r.toString(),
                (isInt ? (Integer) expected : (Long) expected), longValue,
                (isInt ? simpleIntValue(r) : simpleLongValue(r)));
        return 1;
    }

    private static long simpleLongValue(Rational r) {
        return r.toBigInteger().longValue();
    }

    private static int simpleIntValue(Rational r) {
        return r.toBigInteger().intValue();
    }
}
