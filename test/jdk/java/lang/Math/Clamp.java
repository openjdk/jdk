/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

/* @test
   @bug 8301226
   @summary Add clamp() methods to java.lang.Math
 */


public class Clamp {
    public static void main(String[] args) {
        int failures = 0;

        failures += testIntClamp();
        failures += testLongClamp();
        failures += testDoubleClamp();
        failures += testFloatClamp();

        if (failures > 0) {
            System.err.println("Testing clamp incurred " + failures + " failures.");
            throw new RuntimeException();
        }
    }

    private static int testIntClamp() {
        int failures = 0;
        long[][] tests = {
                {0, 1, 2, 1},
                {0, 0, 2, 0},
                {1, 0, 2, 1},
                {2, 0, 2, 2},
                {3, 0, 2, 2},
                {0, Integer.MIN_VALUE, Integer.MAX_VALUE, 0},
                {Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE},
                {Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE},
                {Long.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE},
                {Long.MIN_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE},
                {0, 1, 1, 1},
                {Long.MAX_VALUE, 1, 1, 1}
        };
        long[][] exceptionTests = {
                {1, 2, 0},
                {1, Integer.MAX_VALUE, Integer.MIN_VALUE}
        };
        for (long[] test : tests) {
            long value = test[0];
            int min = Math.toIntExact(test[1]);
            int max = Math.toIntExact(test[2]);
            int expected = Math.toIntExact(test[3]);
            failures += checkEquals("(int) Math.clamp(" + value + ", " + min + ", " + max + ")", Math.clamp(value, min, max), expected);
            failures += checkEquals("(int) StrictMath.clamp(" + value + ", " + min + ", " + max + ")", StrictMath.clamp(value, min, max), expected);
        }
        for (long[] test : exceptionTests) {
            long value = test[0];
            int min = Math.toIntExact(test[1]);
            int max = Math.toIntExact(test[2]);
            failures += checkIllegalArgumentException("(int) Math.clamp(" + value + ", " + min + ", " + max + ")",
                    () -> Math.clamp(value, min, max));
            failures += checkIllegalArgumentException("(int) StrictMath.clamp(" + value + ", " + min + ", " + max + ")",
                    () -> StrictMath.clamp(value, min, max));
        }
        return failures;
    }

    private static int testLongClamp() {
        int failures = 0;
        long[][] tests = {
                {0L, 1L, 2L, 1L},
                {0L, 0L, 2L, 0L},
                {1L, 0L, 2L, 1L},
                {2L, 0L, 2L, 2L},
                {3L, 0L, 2L, 2L},
                {0L, Long.MIN_VALUE, Long.MAX_VALUE, 0},
                {Long.MIN_VALUE, Long.MIN_VALUE, Long.MAX_VALUE, Long.MIN_VALUE},
                {Long.MAX_VALUE, Long.MIN_VALUE, Long.MAX_VALUE, Long.MAX_VALUE},
                {0, 1, 1, 1},
                {Long.MAX_VALUE, 1, 1, 1}
        };
        long[][] exceptionTests = {
                {1L, 2L, 0L},
                {1, Long.MAX_VALUE, Long.MIN_VALUE}
        };
        for (long[] test : tests) {
            long value = test[0];
            long min = test[1];
            long max = test[2];
            long expected = test[3];
            failures += checkEquals("(long) Math.clamp(" + value + ", " + min + ", " + max + ")", Math.clamp(value, min, max), expected);
            failures += checkEquals("(long) StrictMath.clamp(" + value + ", " + min + ", " + max + ")", StrictMath.clamp(value, min, max), expected);
        }
        for (long[] test : exceptionTests) {
            long value = test[0];
            long min = test[1];
            long max = test[2];
            failures += checkIllegalArgumentException("(long) Math.clamp(" + value + ", " + min + ", " + max + ")",
                    () -> Math.clamp(value, min, max));
            failures += checkIllegalArgumentException("(long) StrictMath.clamp(" + value + ", " + min + ", " + max + ")",
                    () -> StrictMath.clamp(value, min, max));
        }
        return failures;
    }

    private static int testDoubleClamp() {
        int failures = 0;
        double[][] tests = {
                // value, min, max, expected
                {-0.1, 0.0, 0.5, 0.0},
                {-0.0, 0.0, 0.5, 0.0},
                {0.0, 0.0, 0.5, 0.0},
                {Double.MIN_VALUE, 0.0, 0.5, Double.MIN_VALUE},
                {0.2, 0.0, 0.5, 0.2},
                {Math.nextDown(0.5), 0.0, 0.5, Math.nextDown(0.5)},
                {0.5, 0.0, 0.5, 0.5},
                {Math.nextUp(0.5), 0.0, 0.5, 0.5},
                {0.6, 0.0, 0.5, 0.5},

                {Double.MAX_VALUE, 0.0, Double.POSITIVE_INFINITY, Double.MAX_VALUE},
                {Double.POSITIVE_INFINITY, 0.0, Double.MAX_VALUE, Double.MAX_VALUE},
                {-Double.MAX_VALUE, Double.NEGATIVE_INFINITY, 0.0, -Double.MAX_VALUE},
                {Double.NEGATIVE_INFINITY, -Double.MAX_VALUE, 0.0, -Double.MAX_VALUE},

                {-1.0, -0.0, 0.0, -0.0},
                {-0.0, -0.0, 0.0, -0.0},
                {0.0, -0.0, 0.0, 0.0},
                {1.0, -0.0, 0.0, 0.0},
                {-1.0, 0.0, 0.0, 0.0},
                {-0.0, 0.0, 0.0, 0.0},
                {0.0, 0.0, 0.0, 0.0},
                {1.0, 0.0, 0.0, 0.0},

                {Double.NaN, 0.0, 1.0, Double.NaN},
                {Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, Double.NaN}
        };
        double[][] exceptionTests = {
                // value, min, max
                {0.0, Double.NaN, Double.NaN},
                {0.0, 0.0, Double.NaN},
                {0.0, Double.NaN, 0.0},
                {Double.NaN, 1.0, 0.0},
                {0.0, 0.0, -0.0}
        };
        for (double[] test : tests) {
            double value = test[0];
            double min = test[1];
            double max = test[2];
            double expected = test[3];
            failures += checkEquals("(double) Math.clamp(" + value + ", " + min + ", " + max + ")", Math.clamp(value, min, max), expected);
            failures += checkEquals("(double) StrictMath.clamp(" + value + ", " + min + ", " + max + ")", StrictMath.clamp(value, min, max), expected);
        }
        for (double[] test : exceptionTests) {
            double value = test[0];
            double min = test[1];
            double max = test[2];
            failures += checkIllegalArgumentException("(double) Math.clamp(" + value + ", " + min + ", " + max + ")",
                    () -> Math.clamp(value, min, max));
            failures += checkIllegalArgumentException("(double) StrictMath.clamp(" + value + ", " + min + ", " + max + ")",
                    () -> StrictMath.clamp(value, min, max));
        }
        return failures;
    }

    private static int testFloatClamp() {
        int failures = 0;
        float[][] tests = {
                // value, min, max, expected
                {-0.1f, 0.0f, 0.5f, 0.0f},
                {-0.0f, 0.0f, 0.5f, 0.0f},
                {0.0f, 0.0f, 0.5f, 0.0f},
                {Float.MIN_VALUE, 0.0f, 0.5f, Float.MIN_VALUE},
                {0.2f, 0.0f, 0.5f, 0.2f},
                {Math.nextDown(0.5f), 0.0f, 0.5f, Math.nextDown(0.5f)},
                {0.5f, 0.0f, 0.5f, 0.5f},
                {Math.nextUp(0.5f), 0.0f, 0.5f, 0.5f},
                {0.6f, 0.0f, 0.5f, 0.5f},

                {Float.MAX_VALUE, 0.0f, Float.POSITIVE_INFINITY, Float.MAX_VALUE},
                {Float.POSITIVE_INFINITY, 0.0f, Float.MAX_VALUE, Float.MAX_VALUE},
                {-Float.MAX_VALUE, Float.NEGATIVE_INFINITY, 0.0f, -Float.MAX_VALUE},
                {Float.NEGATIVE_INFINITY, -Float.MAX_VALUE, 0.0f, -Float.MAX_VALUE},

                {-1.0f, -0.0f, 0.0f, -0.0f},
                {-0.0f, -0.0f, 0.0f, -0.0f},
                {0.0f, -0.0f, 0.0f, 0.0f},
                {1.0f, -0.0f, 0.0f, 0.0f},
                {-1.0f, 0.0f, 0.0f, 0.0f},
                {-0.0f, 0.0f, 0.0f, 0.0f},
                {0.0f, 0.0f, 0.0f, 0.0f},
                {1.0f, 0.0f, 0.0f, 0.0f},

                {Float.NaN, 0.0f, 1.0f, Float.NaN},
                {Float.NaN, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, Float.NaN}
        };
        float[][] exceptionTests = {
                // value, min, max
                {0.0f, Float.NaN, Float.NaN},
                {0.0f, 0.0f, Float.NaN},
                {0.0f, Float.NaN, 0.0f},
                {Float.NaN, 1.0f, 0.0f},
                {0.0f, 0.0f, -0.0f}
        };
        for (float[] test : tests) {
            float value = test[0];
            float min = test[1];
            float max = test[2];
            float expected = test[3];
            failures += checkEquals("(float) Math.clamp(" + value + ", " + min + ", " + max + ")", Math.clamp(value, min, max), expected);
            failures += checkEquals("(float) StrictMath.clamp(" + value + ", " + min + ", " + max + ")", StrictMath.clamp(value, min, max), expected);
        }
        for (float[] test : exceptionTests) {
            float value = test[0];
            float min = test[1];
            float max = test[2];
            failures += checkIllegalArgumentException("(float) Math.clamp(" + value + ", " + min + ", " + max + ")", 
                    () -> Math.clamp(value, min, max));
            failures += checkIllegalArgumentException("(float) StrictMath.clamp(" + value + ", " + min + ", " + max + ")", 
                    () -> StrictMath.clamp(value, min, max));
        }
        return failures;
    }

    private static int checkIllegalArgumentException(String what, Runnable r) {
        try {
            r.run();
        }
        catch (IllegalArgumentException ex) {
            return 0;
        }
        System.err.println(what+": missing expected exception");
        return 1;
    }

    private static int checkEquals(String what, double actual, double expected) {
        if (Double.doubleToLongBits(actual) != Double.doubleToLongBits(expected)) {
            System.err.println(what + ": expected = " + expected + "; actual = " + actual);
            return 1;
        }
        return 0;
    }

    private static int checkEquals(String what, long actual, long expected) {
        if (actual != expected) {
            System.err.println(what + ": expected = " + expected + "; actual = " + actual);
            return 1;
        }
        return 0;
    }
}
