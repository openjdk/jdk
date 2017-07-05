/*
 * Copyright (c) 2003, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * Shared static test methods for numerical tests.  Sharing these
 * helper test methods avoids repeated functions in the various test
 * programs.  The test methods return 1 for a test failure and 0 for
 * success.  The order of arguments to the test methods is generally
 * the test name, followed by the test arguments, the computed result,
 * and finally the expected result.
 */

import sun.misc.FpUtils;

public class Tests {
    private Tests(){}; // do not instantiate

    private static String toHexString(float f) {
        if (!Float.isNaN(f))
            return Float.toHexString(f);
        else
            return "NaN(0x" + Integer.toHexString(Float.floatToRawIntBits(f)) + ")";
    }

    private static String toHexString(double d) {
        if (!Double.isNaN(d))
            return Double.toHexString(d);
        else
            return "NaN(0x" + Long.toHexString(Double.doubleToRawLongBits(d)) + ")";
    }

    public static int test(String testName, float input,
                           boolean result, boolean expected) {
        if (expected != result) {
            System.err.println("Failure for " + testName + ":\n" +
                               "\tFor input " + input    + "\t(" + toHexString(input) + ")\n" +
                               "\texpected  " + expected + "\n"  +
                               "\tgot       " + result   + ").");
            return 1;
        }
        else
            return 0;
    }

    public static int test(String testName, double input,
                           boolean result, boolean expected) {
        if (expected != result) {
            System.err.println("Failure for " + testName + ":\n" +
                               "\tFor input " + input    + "\t(" + toHexString(input) + ")\n" +
                               "\texpected  " + expected + "\n"  +
                               "\tgot       " + result   + ").");
            return 1;
        }
        else
            return 0;
    }

    public static int test(String testName, float input1, float input2,
                           boolean result, boolean expected) {
        if (expected != result) {
            System.err.println("Failure for "  + testName + ":\n" +
                               "\tFor inputs " + input1   + "\t(" + toHexString(input1) + ") and "
                                               + input2   + "\t(" + toHexString(input2) + ")\n" +
                               "\texpected  "  + expected + "\n"  +
                               "\tgot       "  + result   + ").");
            return 1;
        }
        return 0;
    }

    public static int test(String testName, double input1, double input2,
                           boolean result, boolean expected) {
        if (expected != result) {
            System.err.println("Failure for "  + testName + ":\n" +
                               "\tFor inputs " + input1   + "\t(" + toHexString(input1) + ") and "
                                               + input2   + "\t(" + toHexString(input2) + ")\n" +
                               "\texpected  "  + expected + "\n"  +
                               "\tgot       "  + result   + ").");
            return 1;
        }
        return 0;
    }

    public static int test(String testName, float input,
                           int result, int expected) {
        if (expected != result) {
            System.err.println("Failure for " + testName + ":\n" +
                               "\tFor input " + input    + "\t(" + toHexString(input) + ")\n" +
                               "\texpected  " + expected + "\n" +
                               "\tgot       " + result    + ").");
            return 1;
        }
        return 0;
    }

    public  static int test(String testName, double input,
                            int result, int expected) {
        if (expected != result) {
            System.err.println("Failure for " + testName + ":\n" +
                               "\tFor input " + input    + "\t(" + toHexString(input) + ")\n" +
                               "\texpected  " + expected + "\n"  +
                               "\tgot       " + result   + ").");
            return 1;
        }
        else
            return 0;
    }

    public static int test(String testName, float input,
                           float result, float expected) {
        if (Float.compare(expected, result) != 0 ) {
            System.err.println("Failure for " + testName + ":\n" +
                               "\tFor input " + input    + "\t(" + toHexString(input) + ")\n" +
                               "\texpected  " + expected + "\t(" + toHexString(expected) + ")\n" +
                               "\tgot       " + result   + "\t(" + toHexString(result) + ").");
            return 1;
        }
        else
            return 0;
    }


    public static int test(String testName, double input,
                           double result, double expected) {
        if (Double.compare(expected, result ) != 0) {
            System.err.println("Failure for " + testName + ":\n" +
                               "\tFor input " + input    + "\t(" + toHexString(input) + ")\n" +
                               "\texpected  " + expected + "\t(" + toHexString(expected) + ")\n" +
                               "\tgot       " + result   + "\t(" + toHexString(result) + ").");
            return 1;
        }
        else
            return 0;
    }

    public static int test(String testName,
                           float input1, double input2,
                           float result, float expected) {
        if (Float.compare(expected, result ) != 0) {
            System.err.println("Failure for "  + testName + ":\n" +
                               "\tFor inputs " + input1   + "\t(" + toHexString(input1) + ") and "
                                               + input2   + "\t(" + toHexString(input2) + ")\n" +
                               "\texpected  "  + expected + "\t(" + toHexString(expected) + ")\n" +
                               "\tgot       "  + result   + "\t(" + toHexString(result) + ").");
            return 1;
        }
        else
            return 0;
    }

    public static int test(String testName,
                           double input1, double input2,
                           double result, double expected) {
        if (Double.compare(expected, result ) != 0) {
            System.err.println("Failure for "  + testName + ":\n" +
                               "\tFor inputs " + input1   + "\t(" + toHexString(input1) + ") and "
                                               + input2   + "\t(" + toHexString(input2) + ")\n" +
                               "\texpected  "  + expected + "\t(" + toHexString(expected) + ")\n" +
                               "\tgot       "  + result   + "\t(" + toHexString(result) + ").");
            return 1;
        }
        else
            return 0;
    }

    public static int test(String testName,
                           float input1, int input2,
                           float result, float expected) {
        if (Float.compare(expected, result ) != 0) {
            System.err.println("Failure for "  + testName + ":\n" +
                               "\tFor inputs " + input1   + "\t(" + toHexString(input1) + ") and "
                                               + input2   + "\n"  +
                               "\texpected  "  + expected + "\t(" + toHexString(expected) + ")\n" +
                               "\tgot       "  + result   + "\t(" + toHexString(result) + ").");
            return 1;
        }
        else
            return 0;
    }

    public static int test(String testName,
                           double input1, int input2,
                           double result, double expected) {
        if (Double.compare(expected, result ) != 0) {
            System.err.println("Failure for "  + testName + ":\n" +
                               "\tFor inputs " + input1   + "\t(" + toHexString(input1) + ") and "
                                               + input2   + "\n"  +
                               "\texpected  "  + expected + "\t(" + toHexString(expected) + ")\n" +
                               "\tgot       "  + result   + "\t(" + toHexString(result) + ").");
            return 1;
        }
        else
            return 0;
    }

    static int testUlpCore(double result, double expected, double ulps) {
        // We assume we won't be unlucky and have an inexact expected
        // be nextDown(2^i) when 2^i would be the correctly rounded
        // answer.  This would cause the ulp size to be half as large
        // as it should be, doubling the measured error).

        if (Double.compare(expected, result) == 0) {
            return 0;   // result and expected are equivalent
        } else {
            if( ulps == 0.0) {
                // Equivalent results required but not found
                return 1;
            } else {
                double difference = expected - result;
                if (FpUtils.isUnordered(expected, result) ||
                    Double.isNaN(difference) ||
                    // fail if greater than or unordered
                    !(Math.abs( difference/Math.ulp(expected) ) <= Math.abs(ulps)) ) {
                    return 1;
                }
                else
                    return 0;
            }
        }
    }

    // One input argument.
    public static int testUlpDiff(String testName, double input,
                                  double result, double expected, double ulps) {
        int code = testUlpCore(result, expected, ulps);
        if (code == 1) {
            System.err.println("Failure for " + testName + ":\n" +
                               "\tFor input " + input    + "\t(" + toHexString(input) + ")\n" +
                               "\texpected  " + expected + "\t(" + toHexString(expected) + ")\n" +
                               "\tgot       " + result   + "\t(" + toHexString(result) + ");\n" +
                               "\tdifference greater than ulp tolerance " + ulps);
        }
        return code;
    }

    // Two input arguments.
    public static int testUlpDiff(String testName, double input1, double input2,
                                  double result, double expected, double ulps) {
        int code = testUlpCore(result, expected, ulps);
        if (code == 1) {
            System.err.println("Failure for "  + testName + ":\n" +
                               "\tFor inputs " + input1   + "\t(" + toHexString(input1) + ") and "
                                               + input2   + "\t(" + toHexString(input2) + ")\n" +
                               "\texpected  "  + expected + "\t(" + toHexString(expected) + ")\n" +
                               "\tgot       "  + result   + "\t(" + toHexString(result) + ");\n" +
                               "\tdifference greater than ulp tolerance " + ulps);
        }
        return code;
    }

    // For a successful test, the result must be within the ulp bound of
    // expected AND the result must have absolute value less than or
    // equal to absBound.
    public static int testUlpDiffWithAbsBound(String testName, double input,
                                              double result, double expected,
                                              double ulps, double absBound) {
        int code = 0;   // return code value

        if (!(StrictMath.abs(result) <= StrictMath.abs(absBound)) &&
            !Double.isNaN(expected)) {
            code = 1;
        } else
            code = testUlpCore(result, expected, ulps);

        if (code == 1) {
            System.err.println("Failure for " + testName + ":\n" +
                               "\tFor input " + input    + "\t(" + toHexString(input) + ")\n" +
                               "\texpected  " + expected + "\t(" + toHexString(expected) + ")\n" +
                               "\tgot       " + result   + "\t(" + toHexString(result) + ");\n" +
                               "\tdifference greater than ulp tolerance " + ulps +
                               " or the result has larger magnitude than " + absBound);
        }
        return code;
    }

    // For a successful test, the result must be within the ulp bound of
    // expected AND the result must have absolute value greater than
    // or equal to the lowerBound.
    public static int testUlpDiffWithLowerBound(String testName, double input,
                                                double result, double expected,
                                                double ulps, double lowerBound) {
        int code = 0;   // return code value

        if (!(result >= lowerBound) && !Double.isNaN(expected)) {
            code = 1;
        } else
            code = testUlpCore(result, expected, ulps);

        if (code == 1) {
            System.err.println("Failure for " + testName +
                               ":\n" +
                               "\tFor input "   + input    + "\t(" + toHexString(input) + ")" +
                               "\n\texpected  " + expected + "\t(" + toHexString(expected) + ")" +
                               "\n\tgot       " + result   + "\t(" + toHexString(result) + ");" +
                               "\ndifference greater than ulp tolerance " + ulps +
                               " or result not greater than or equal to the bound " + lowerBound);
        }
        return code;
    }

    public static int testTolerance(String testName, double input,
                                    double result, double expected, double tolerance) {
        if (Double.compare(expected, result ) != 0) {
            double difference = expected - result;
            if (FpUtils.isUnordered(expected, result) ||
                Double.isNaN(difference) ||
                // fail if greater than or unordered
                !(Math.abs((difference)/expected) <= StrictMath.pow(10, -tolerance)) ) {
                System.err.println("Failure for " + testName + ":\n" +
                                   "\tFor input " + input    + "\t(" + toHexString(input) + ")\n" +
                                   "\texpected  " + expected + "\t(" + toHexString(expected) + ")\n" +
                                   "\tgot       " + result   + "\t(" + toHexString(result) + ");\n" +
                                   "\tdifference greater than tolerance 10^-" + tolerance);
                return 1;
            }
            return 0;
        }
        else
            return 0;
    }
}
