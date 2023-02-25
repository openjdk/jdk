/*
 * @test
 * @summary Tests the Rational string constructor.
 */

import java.math.*;
import java.util.Random;

import static java.math.Rational.*;

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
        constructWithError("1.2(3)4");
        constructWithError("1.(3)4");
        constructWithError("1.38452)486425");
        constructWithError("1.3(4");
        constructWithError("1.752(3248)4324");
        constructWithError("1.752ddj32sh84324");

        // Range checks
        constructWithOverflow("1e"+Integer.MIN_VALUE);
        constructWithOverflow("10e"+Integer.MIN_VALUE);
        constructWithOverflow("0.01e"+Integer.MIN_VALUE);

        /* These Strings have an exponent > Integer.MAX_VALUE or < Integer.MIN_VALUE */
        constructWithError("1e"+((long)Integer.MIN_VALUE-1));
        constructWithError("1.0E+2147483649");
        constructWithError("-9.223372036854775808E+2147483666");
        constructWithError("1.0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000E+2147483748");

        leadingExponentZeroTest();
        nonAsciiZeroTest();

        Rational[][] testCases = {
                {new Rational(1), new Rational("0.(9)")},
                {valueOf(1, 2), new Rational("0.5")},
                {valueOf(1, 3), new Rational("0.(3)")},
                {valueOf(1, 6), new Rational("0.1(6)")},
                {valueOf(1, 7), new Rational("0.(142857)")},
                {valueOf(1, 8), new Rational("0.125")},
                {valueOf(1, 9), new Rational("0.(1)")},
                {valueOf(1, 11), new Rational("0.(09)")},
                {valueOf(1, 12), new Rational("0.08(3)")},
                {valueOf(1, 29), new Rational("0.(0344827586206896551724137931)")},
                {valueOf(1, 81), new Rational("0.(012345679)")},
                {valueOf(1, 97), new Rational("0.(010309278350515463917525773195876288659793814432989690721649484536082474226804123711340206185567)")},
                {valueOf(1, 119), new Rational("0.(008403361344537815126050420168067226890756302521)")},
                {valueOf(2, 3), new Rational("0.(6)")},
                {valueOf(9, 11), new Rational("0.(81)")},
                {valueOf(7, 12), new Rational("0.58(3)")},
                {valueOf(22, 7), new Rational("3.(142857)")},

        };

        for (Rational[] test : testCases) {
            if (!test[0].equals(test[1]))
                throw new RuntimeException("expected: " + test[0] + ", actual: " + test[1]);
        }
    }

    /*
     * Verify precision is set properly if the significand has
     * non-ASCII leading zeros.
     */
    private static void nonAsciiZeroTest() {
        String values[] = {
            "00004e5",
            "\u0660\u0660\u0660\u06604e5",
        };

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
        int limit  = ((int)Math.log10(Long.MAX_VALUE)) + 6;
        for(int i = 0; i < limit; i++, middle += "0") {
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
        } catch(NumberFormatException e) {
            // expected
        }
    }

    private static void constructWithOverflow(String badString) {
        try {
            new Rational(badString);
            throw new RuntimeException(badString + " accepted");
        } catch(ArithmeticException e) {
            // expected
        }
    }
}
