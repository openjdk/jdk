
/**
 * @test
 * @summary Tests of Rational.intValueExact
 */
import java.math.*;
import java.util.List;
import java.util.Map;
import static java.util.Map.entry;

public class IntValueExactTests {
    public static void main(String... args) {
        int failures = 0;

        failures += intValueExactSuccessful();
        failures += intValueExactExceptional();

        if (failures > 0) {
            throw new RuntimeException("Incurred " + failures + " failures while testing intValueExact.");
        }
    }

    private static int simpleIntValueExact(Rational r) {
        return r.toBigIntegerExact().intValue();
    }

    private static int intValueExactSuccessful() {
        int failures = 0;

        // Strings used to create Rational instances on which invoking
        // intValueExact() will succeed.
        Map<String, Integer> successCases = Map.ofEntries(entry("2147483647", Integer.MAX_VALUE), // 2^31 -1
                entry("2147483647.0", Integer.MAX_VALUE), entry("2147483647.00", Integer.MAX_VALUE),

                entry("-2147483648", Integer.MIN_VALUE), // -2^31
                entry("-2147483648.0", Integer.MIN_VALUE), entry("-2147483648.00", Integer.MIN_VALUE),

                entry("1e0", 1), entry("1e9", 1_000_000_000),

                entry("0e13", 0), // Fast path zero
                entry("0e32", 0), entry("0e512", 0),

                entry("10.000000000000000000000000000000000", 10));

        for (var testCase : successCases.entrySet()) {
            Rational r = new Rational(testCase.getKey());
            int expected = testCase.getValue();
            try {
                int intValueExact = r.intValueExact();
                if (expected != intValueExact || intValueExact != simpleIntValueExact(r)) {
                    failures++;
                    System.err.println("Unexpected intValueExact result " + intValueExact + " on " + r);
                }
            } catch (Exception e) {
                failures++;
                System.err.println("Error on " + r + "\tException message:" + e.getMessage());
            }
        }
        return failures;
    }

    private static int intValueExactExceptional() {
        int failures = 0;
        List<Rational> exceptionalCases = List.of(new Rational("2147483648"), // Integer.MAX_VALUE + 1
                new Rational("2147483648.0"), new Rational("2147483648.00"), new Rational("-2147483649"), // Integer.MIN_VALUE
                                                                                                          // - 1
                new Rational("-2147483649.1"), new Rational("-2147483649.01"),

                new Rational("9999999999999999999999999999999"), new Rational("10000000000000000000000000000000"),

                new Rational("0.99"), new Rational("0.999999999999999999999"));

        for (Rational r : exceptionalCases) {
            try {
                r.intValueExact();
                failures++;
                System.err.println("Unexpected non-exceptional intValueExact on " + r);
            } catch (ArithmeticException e) {
                // Success;
            }
        }
        return failures;
    }
}
