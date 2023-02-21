/*
 * @test
 * @summary Test constructors of Rational
 * @author Fabio Romano
 */

import java.math.*;

public class ConstructorTests {

    public static int stringConstructor() {
        int failures = 0;
        try {
            new Rational("1.2e");
            failures++;
        } catch (NumberFormatException e) {
        }
        return failures;
    }

    public static int charArrayConstructorNegativeOffset() {
        int failures = 0;
        try {
            new Rational(new char[5], -1, 4);
            failures++;
        } catch (NumberFormatException e) {
        }
        return failures;
    }

    public static int charArrayConstructorNegativeLength() {
        int failures = 0;
        try {
            new Rational(new char[5], 0, -1);
            failures++;
        } catch (NumberFormatException e) {
        }
        return failures;
    }

    public static int charArrayConstructorIntegerOverflow() {
        int failures = 0;
        try {
            new Rational(new char[5], Integer.MAX_VALUE - 5, 6);
            failures++;
        } catch (NumberFormatException nfe) {
            if (nfe.getCause() instanceof IndexOutOfBoundsException)
                failures++;
        }
        return failures;
    }

    public static int charArrayConstructorIndexOutOfBounds() {
        int failures = 0;
        try {
            new Rational(new char[5], 1, 5);
            failures++;
        } catch (NumberFormatException e) {
        }
        return failures;
    }

    public static void main(String[] args) {
        int failures = 0;

        failures += charArrayConstructorIndexOutOfBounds();
        failures += charArrayConstructorIntegerOverflow();
        failures += charArrayConstructorNegativeLength();
        failures += charArrayConstructorNegativeOffset();
        failures += stringConstructor();

        if (failures > 0)
            throw new RuntimeException("Incurred " + failures + " failures while testing constructors.");
    }
}
