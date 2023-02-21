/*
 * @test
 * @summary Tests arithmetic operations with zero as an argument.
 */

import java.math.*;
import java.util.*;

public class ZeroTests {

    static MathContext longEnough = new MathContext(50, RoundingMode.UNNECESSARY);

    static Rational element = new Rational(10_000);

    static MathContext contexts[] = { new MathContext(0, RoundingMode.UNNECESSARY),
            new MathContext(100, RoundingMode.UNNECESSARY), new MathContext(5, RoundingMode.UNNECESSARY),
            new MathContext(4, RoundingMode.UNNECESSARY), new MathContext(3, RoundingMode.UNNECESSARY),
            new MathContext(2, RoundingMode.UNNECESSARY), new MathContext(1, RoundingMode.UNNECESSARY), };

    static int addTests() {
        int failures = 0;

        Rational expected = Rational.ZERO;
        Rational result;

        if (!(result = Rational.ZERO.add(Rational.ZERO)).equals(expected)) {
            failures++;
            System.err.println("For classic exact add, expected " + expected + "; got " + result + ".");
        }

        // Test effect of adding zero to a nonzero value.
        result = element.add(Rational.ZERO);
        if (!result.equals(element)) {
            failures++;
            System.err.println("Expected " + element + " result value was " + result);
        }

        result = Rational.ZERO.add(element);
        if (!result.equals(element)) {
            failures++;
            System.err.println("Expected " + element + " result value was " + result);
        }

        result = element.negate().add(Rational.ZERO);
        if (!result.equals(element.negate())) {
            failures++;
            System.err.println("Expected " + element + " result value was " + result);
        }

        result = Rational.ZERO.add(element.negate());
        if (!result.equals(element.negate())) {
            failures++;
            System.err.println("Expected " + element + " result value was " + result);
        }

        return failures;
    }

    static int subtractTests() {
        int failures = 0;

        Rational expected = Rational.ZERO;
        Rational result;

        if (!(result = Rational.ZERO.subtract(Rational.ZERO)).equals(expected)) {
            failures++;
            System.err.println("For classic exact subtract, expected " + expected + "; got " + result + ".");
        }

        // Test effect of subtracting zero to a nonzero value.
        result = element.subtract(Rational.ZERO);
        if (!result.equals(element)) {
            failures++;
            System.err.println("Expected  " + element + " result value was " + result);
        }

        result = Rational.ZERO.subtract(element);
        if (!result.equals(element.negate())) {
            failures++;
            System.err.println("Expected  " + element + " result value was " + result);
        }

        result = element.negate().subtract(Rational.ZERO);
        if (!result.equals(element.negate())) {
            failures++;
            System.err.println("Expected  " + element + " result value was " + result);
        }

        result = Rational.ZERO.subtract(element.negate());
        if (!result.equals(element)) {
            failures++;
            System.err.println("Expected  " + element + " result value was " + result);
        }

        return failures;
    }

    static int multiplyTests() {
        int failures = 0;

        List<Rational> values = Arrays.asList(Rational.ZERO, Rational.ONE);

        for (Rational value : values) {
            Rational expected = Rational.ZERO;
            Rational result;

            if (!(result = Rational.ZERO.multiply(value)).equals(expected)) {
                failures++;
                System.err.println("For classic exact multiply, expected " + expected + "; got " + result + ".");
            }
        }

        return failures;
    }

    static int divideTests() {
        int failures = 0;

        Rational expected = Rational.ZERO;
        Rational result;

        if (!(result = Rational.ZERO.divide(Rational.ONE)).equals(expected)) {
            failures++;
            System.err.println("For classic exact divide, expected " + expected + "; got " + result + ".");
        }

        return failures;
    }

    public static void main(String argv[]) {
        int failures = 0;

        failures += addTests();
        failures += subtractTests();
        failures += multiplyTests();
        failures += divideTests();

        if (failures > 0) {
            throw new RuntimeException(
                    "Incurred " + failures + " failures" + " testing the preservation of zero scales.");
        }
    }
}
