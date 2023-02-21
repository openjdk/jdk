/*
 * @test
 * @summary Some exponent over/undeflow tests for the pow method
 */

import java.math.*;

public class PowTests {
    
    private static int simpleTests() {
        int failures = 0;

        Number[][] testCases = {
            {Rational.ZERO,  0,                 Rational.ONE},
            {Rational.ZERO,  1,                 Rational.ZERO},
            {Rational.ZERO,  2,                 Rational.ZERO},
            {Rational.ZERO,  Integer.MAX_VALUE, Rational.ZERO},

            {Rational.ONE,  0,                   Rational.ONE},
            {Rational.ONE,  1,                   Rational.ONE},
            {Rational.ONE,  2,                   Rational.ONE},
            {Rational.ONE,  Integer.MAX_VALUE,   Rational.ONE},
            
            {Rational.TWO,  0,   Rational.ONE},
            {Rational.TWO,  1,   Rational.TWO},
            {Rational.TWO,  2,   new Rational(4)},
            {Rational.TWO,  3,   new Rational(8)},
            {Rational.TWO,  4,   new Rational(16)},
            {Rational.TWO,  5,   new Rational(32)},
            
            {new Rational(3),  0,   Rational.ONE},
            {new Rational(3),  1,   new Rational(3)},
            {new Rational(3),  2,   new Rational(9)},
            {new Rational(3),  3,   new Rational(27)},
            {new Rational(3),  4,   new Rational(81)},
            {new Rational(3),  5,   new Rational(243)},
            
            {Rational.TWO,  -1,                 Rational.valueOf(1, 2)},
            {Rational.TWO,  -2,                 Rational.valueOf(1, 4)},
            {Rational.TWO,  -3,                 Rational.valueOf(1, 8)},
            {Rational.TWO,  -4,                 Rational.valueOf(1, 16)},
            {Rational.TWO,  -5,                 Rational.valueOf(1, 32)},
            
            {new Rational(3),  -1,   Rational.valueOf(1, 3)},
            {new Rational(3),  -2,   Rational.valueOf(1, 9)},
            {new Rational(3),  -3,   Rational.valueOf(1, 27)},
            {new Rational(3),  -4,   Rational.valueOf(1, 81)},
            {new Rational(3),  -5,   Rational.valueOf(1, 243)},
            
            {new Rational(-2),  0,   Rational.ONE},
            {new Rational(-2),  1,   new Rational(-2)},
            {new Rational(-2),  2,   new Rational(4)},
            {new Rational(-2),  3,   new Rational(-8)},
            {new Rational(-2),  4,   new Rational(16)},
            {new Rational(-2),  5,   new Rational(-32)},
            
            {Rational.valueOf(1, 2),  1,                 Rational.valueOf(1, 2)},
            {Rational.valueOf(1, 2),  2,                 Rational.valueOf(1, 4)},
            {Rational.valueOf(1, 2),  3,                 Rational.valueOf(1, 8)},
            {Rational.valueOf(1, 2),  4,                 Rational.valueOf(1, 16)},
            {Rational.valueOf(1, 2),  5,                 Rational.valueOf(1, 32)},
            
            {Rational.valueOf(2, 3), 2, Rational.valueOf(4, 9)},
            {Rational.valueOf(2, 3), 3, Rational.valueOf(8, 27)},
            {Rational.valueOf(2, 3), 4, Rational.valueOf(16, 81)},
            {Rational.valueOf(2, 3), 5, Rational.valueOf(32, 243)},
        };

        for(Number[] testCase: testCases) {
            int exponent = (int) testCase[1];
            Rational result;

            try{
                result = ((Rational) testCase[0]).pow(exponent);
                if (!result.equals(testCase[2])) {
                    failures++;
                    System.err.println("Unexpected result while raising " +
                                       testCase[0] +
                                       " to the " + exponent + " power; expected " +
                                       testCase[2] + ", got " + result + ".");
                }
            } catch (ArithmeticException e) {
                if (testCase[2] != null) {
                    failures++;
                    System.err.println("Unexpected exception while raising " + testCase[0] +
                                       " to the " + exponent + " power.");

                }
            }
        }

        return failures;
    }

    public static void main(String argv[]) {
        int failures = 0;

        failures += simpleTests();

        if (failures > 0) {
            throw new RuntimeException("Incurred " + failures +
                                       " failures while testing pow methods.");
        }
    }

}
