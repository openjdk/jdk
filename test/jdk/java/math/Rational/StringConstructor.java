/*
 * @test
 * @summary Tests the Rational string constructor.
 */

import java.math.*;

public class StringConstructor {

    public static void main(String[] args) throws Exception {
        constructWithError("");
        constructWithError("+");
        constructWithError("-");
        constructWithError("+e");
        constructWithError("-e");
        constructWithError("e+");
        constructWithError("1.-0");
        constructWithError(".-123");
        constructWithError("-");
        constructWithError("--1.1");
        constructWithError("-+1.1");
        constructWithError("+-1.1");
        constructWithError("1-.1");
        constructWithError("1+.1");
        constructWithError("1.111+1");
        constructWithError("1.111-1");
        constructWithError("11.e+");
        constructWithError("11.e-");
        constructWithError("11.e+-");
        constructWithError("11.e-+");
        constructWithError("11.e-+1");
        constructWithError("11.e+-1");

        // Range checks
        constructWithError("1e" + Integer.MIN_VALUE);
        constructWithError("10e" + Integer.MIN_VALUE);
        constructWithError("0.01e" + Integer.MIN_VALUE);
        constructWithError("1e" + ((long) Integer.MIN_VALUE - 1));

        leadingExponentZeroTest();
        nonAsciiZeroTest();
    }

    /*
     * Verify value is set properly if the significand has non-ASCII leading zeros.
     */
    private static void nonAsciiZeroTest() {
        String values[] = { "00004e5", "\u0660\u0660\u0660\u06604e5", };

        Rational expected = new Rational("4e5");

        for (String s : values) {
            Rational tmp = new Rational(s);
            // System.err.println("Testing " + s);
            if (!expected.equals(tmp)) {
                System.err.println("Bad conversion of " + s + "got " + tmp);
                throw new RuntimeException("String constructor failure.");
            }
        }

    }

    private static void leadingExponentZeroTest() {
        Rational twelve = new Rational("12");
        Rational onePointTwo = new Rational("1.2");

        String start = "1.2e0";
        String end = "1";
        String middle = "";

        // Test with more excess zeros than the largest number of
        // decimal digits needed to represent a long
        int limit = ((int) Math.log10(Long.MAX_VALUE)) + 6;
        for (int i = 0; i < limit; i++, middle += "0") {
            String t1 = start + middle;
            String t2 = t1 + end;

            // System.out.println(i + "\t" + t1 + "\t" + t2);
            testString(t1, onePointTwo);
            testString(t2, twelve);
        }
    }

    private static void testString(String s, Rational expected) {
        testString0(s, expected);
        testString0(switchZero(s), expected);
    }

    private static void testString0(String s, Rational expected) {
        if (!expected.equals(new Rational(s)))
            throw new RuntimeException(s + " is not equal to " + expected);
    }

    private static String switchZero(String s) {
        return s.replace('0', '\u0660'); // Arabic-Indic zero
    }

    private static void constructWithError(String badString) {
        try {
            new Rational(badString);
            throw new RuntimeException(badString + " accepted");
        } catch (NumberFormatException e) {
        }
    }
}
