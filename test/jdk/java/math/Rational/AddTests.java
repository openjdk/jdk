/*
 * @test
 * @summary Some tests of add(Rational)
 * @author Fabio Romano
 */

import java.math.*;
import static java.math.BigDecimal.*;
import java.util.Set;
import java.util.EnumSet;

public class AddTests {

    /**
     * Test for some simple additions, particularly, it will test the overflow case.
     */
    private static int simpleTests() {
        int failures = 0;

        Rational[] bd1 = { new Rational("78124046.66936930160"), new Rational("7812404.666936930160"),
                new Rational("78124.04666936930160"), };
        Rational bd2 = new Rational("279000.0");
        Rational[] expectedResult = { new Rational("78403046.66936930160"), new Rational("8091404.666936930160"),
                new Rational("1060240.4666936930160"), };
        for (int i = 0; i < bd1.length; i++) {
            if (!bd1[i].add(bd2).equals(expectedResult[i]))
                failures++;
        }
        return failures;
    }

    /**
     * Test for extreme value of scale that could cause
     * integer overflow in right-shift-into-sticky-bit computations.
     */
    private static int extremaTests() {
        int failures = 0;

        failures += addWithoutException(new Rational(valueOf(1, -Integer.MAX_VALUE)),
                new Rational(valueOf(2, Integer.MAX_VALUE)));
        failures += addWithoutException(new Rational(valueOf(1, -Integer.MAX_VALUE)),
                new Rational(valueOf(-2, Integer.MAX_VALUE)));
        return failures;
    }

    /**
     * Print sum of b1 and b2; correct result will not throw an exception.
     */
    private static int addWithoutException(Rational b1, Rational b2) {
        try {
            Rational sum = b1.add(b2);
            printAddition(b1, b2, sum.toString());
            return 0;
        } catch (ArithmeticException ae) {
            printAddition(b1, b2, "Exception!");
            return 1;
        }
    }

    private static void printAddition(BigDecimal b1, BigDecimal b2, String s) {
        System.out.println("" + b1 + "\t+\t" + b2 + "\t=\t" + s);
    }

    private static int arithmeticExceptionTest() {
        int failures = 0;
        Rational x;
        try {
            //
            // The string representation "1e2147483647", which is equivalent
            // to 10^Integer.MAX_VALUE, is used to create an augend too big
            // to be represented into a Rational, so the program must throw
            // an ArithmeticException
            //
            x = new Rational("1e2147483647").add(new Rational(1));
            failures++;
        } catch (ArithmeticException e) {
        }
        return failures;
    }

    public static void main(String argv[]) {
        int failures = 0;

        failures += simpleTests();
        failures += extremaTests();
        failures += arithmeticExceptionTest();

        if (failures > 0) {
            throw new RuntimeException("Incurred " + failures + " failures while testing rounding add.");
        }
    }
}
