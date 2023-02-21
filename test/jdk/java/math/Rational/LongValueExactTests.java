/**
 * @test
 * @summary Tests of Rational.longValueExact
 */
import java.math.*;
import java.util.List;
import java.util.Map;
import static java.util.Map.entry;

public class LongValueExactTests {
    public static void main(String... args) {
        int failures = 0;

        failures += longValueExactSuccessful();
        failures += longValueExactExceptional();

        if (failures > 0) {
            throw new RuntimeException("Incurred " + failures +
                                       " failures while testing longValueExact.");
        }
    }

    private static long simpleLongValueExact(Rational r) {
        return r.toBigIntegerExact().longValue();
    }

    private static int longValueExactSuccessful() {
        int failures = 0;

        // Strings used to create Rational instances on which invoking
        // longValueExact() will succeed.
        Map<String, Long> successCases =
            Map.ofEntries(entry("9223372036854775807",    Long.MAX_VALUE), // 2^63 -1
                          entry("9223372036854775807.0",  Long.MAX_VALUE),
                          entry("9223372036854775807.00", Long.MAX_VALUE),

                          entry("-9223372036854775808",   Long.MIN_VALUE), // -2^63
                          entry("-9223372036854775808.0", Long.MIN_VALUE),
                          entry("-9223372036854775808.00",Long.MIN_VALUE),

                          entry("1e0",    1L),
                          entry("1e18",   1_000_000_000_000_000_000L),

                          entry("0e13",   0L),
                          entry("0e64",   0L),
                          entry("0e1024", 0L),

                          entry("10.000000000000000000000000000000000", 10L));

        for (var testCase : successCases.entrySet()) {
            Rational r = new Rational(testCase.getKey());
            long expected = testCase.getValue();
            try {
                long longValueExact = r.longValueExact();
                if (expected != longValueExact ||
                    longValueExact != simpleLongValueExact(r)) {
                    failures++;
                    System.err.println("Unexpected longValueExact result " + longValueExact +
                                       " on " + r);
                }
            } catch (Exception e) {
                failures++;
                System.err.println("Error on " + r + "\tException message:" + e.getMessage());
            }
        }
        return failures;
    }

    private static int longValueExactExceptional() {
        int failures = 0;
        List<String> exceptionalCases =
            List.of("9223372036854775808", // Long.MAX_VALUE + 1
                    "9223372036854775808.0",
                    "9223372036854775808.00",
                    "-9223372036854775809", // Long.MIN_VALUE - 1
                    "-9223372036854775808.1",
                    "-9223372036854775808.01",

                    "9999999999999999999",
                    "10000000000000000000",

                    "0.99",
                    "0.999999999999999999999");

        for (String s : exceptionalCases) {
            Rational r = new Rational(s);
            try {
                r.longValueExact();
                failures++;
                System.err.println("Unexpected non-exceptional longValueExact on " + r);
            } catch (ArithmeticException e) {
                // Success;
            }
        }
        return failures;
    }
}
