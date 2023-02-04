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
        failures += checkIntClamp(0, 1, 2, 1);
        failures += checkIntClamp(0, 0, 2, 0);
        failures += checkIntClamp(1, 0, 2, 1);
        failures += checkIntClamp(2, 0, 2, 2);
        failures += checkIntClamp(3, 0, 2, 2);
        failures += checkIntClamp(0, Integer.MIN_VALUE, Integer.MAX_VALUE, 0);
        failures += checkIntClamp(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE);
        failures += checkIntClamp(Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
        failures += checkIntClamp(Long.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
        failures += checkIntClamp(Long.MIN_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE);
        failures += checkIntClamp(0, 1, 1, 1);
        failures += checkIntClamp(Long.MAX_VALUE, 1, 1, 1);
        failures += checkIllegalArgumentException("clamp(1, 2, 0)", () -> Math.clamp(1, 2, 0), () -> StrictMath.clamp(1, 2, 0));
        failures += checkIllegalArgumentException("clamp(1, Integer.MAX_VALUE, Integer.MIN_VALUE)",
                () -> Math.clamp(1, Integer.MAX_VALUE, Integer.MIN_VALUE),
                () -> StrictMath.clamp(1, Integer.MAX_VALUE, Integer.MIN_VALUE));
        return failures;
    }

    private static int testLongClamp() {
        int failures = 0;
        failures += checkLongClamp(0L, 1L, 2L, 1L);
        failures += checkLongClamp(0L, 0L, 2L, 0L);
        failures += checkLongClamp(1L, 0L, 2L, 1L);
        failures += checkLongClamp(2L, 0L, 2L, 2L);
        failures += checkLongClamp(3L, 0L, 2L, 2L);
        failures += checkLongClamp(0L, Long.MIN_VALUE, Long.MAX_VALUE, 0);
        failures += checkLongClamp(Long.MIN_VALUE, Long.MIN_VALUE, Long.MAX_VALUE, Long.MIN_VALUE);
        failures += checkLongClamp(Long.MAX_VALUE, Long.MIN_VALUE, Long.MAX_VALUE, Long.MAX_VALUE);
        failures += checkLongClamp(0, 1, 1, 1);
        failures += checkLongClamp(Long.MAX_VALUE, 1, 1, 1);
        failures += checkIllegalArgumentException("clamp(1, 2, 0)", () -> Math.clamp(1L, 2L, 0L), () -> StrictMath.clamp(1L, 2L, 0L));
        failures += checkIllegalArgumentException("clamp(1, Long.MAX_VALUE, Long.MIN_VALUE)",
                () -> Math.clamp(1, Long.MAX_VALUE, Long.MIN_VALUE),
                () -> StrictMath.clamp(1, Long.MAX_VALUE, Long.MIN_VALUE));
        return failures;
    }

    private static int testDoubleClamp() {
        int failures = 0;
        failures += checkDoubleClamp(-0.1, 0.0, 0.5, 0.0);
        failures += checkDoubleClamp(-0.0, 0.0, 0.5, 0.0);
        failures += checkDoubleClamp(0.0, 0.0, 0.5, 0.0);
        failures += checkDoubleClamp(Double.MIN_VALUE, 0.0, 0.5, Double.MIN_VALUE);
        failures += checkDoubleClamp(0.2, 0.0, 0.5, 0.2);
        failures += checkDoubleClamp(Math.nextDown(0.5), 0.0, 0.5, Math.nextDown(0.5));
        failures += checkDoubleClamp(0.5, 0.0, 0.5, 0.5);
        failures += checkDoubleClamp(Math.nextUp(0.5), 0.0, 0.5, 0.5);
        failures += checkDoubleClamp(0.6, 0.0, 0.5, 0.5);

        failures += checkDoubleClamp(Double.MAX_VALUE, 0.0, Double.POSITIVE_INFINITY, Double.MAX_VALUE);
        failures += checkDoubleClamp(Double.POSITIVE_INFINITY, 0.0, Double.MAX_VALUE, Double.MAX_VALUE);
        failures += checkDoubleClamp(-Double.MAX_VALUE, Double.NEGATIVE_INFINITY, 0.0, -Double.MAX_VALUE);
        failures += checkDoubleClamp(Double.NEGATIVE_INFINITY, -Double.MAX_VALUE, 0.0, -Double.MAX_VALUE);

        failures += checkDoubleClamp(-1.0, -0.0, 0.0, -0.0);
        failures += checkDoubleClamp(-0.0, -0.0, 0.0, -0.0);
        failures += checkDoubleClamp(0.0, -0.0, 0.0, 0.0);
        failures += checkDoubleClamp(1.0, -0.0, 0.0, 0.0);
        failures += checkDoubleClamp(-1.0, 0.0, 0.0, 0.0);
        failures += checkDoubleClamp(-0.0, 0.0, 0.0, 0.0);
        failures += checkDoubleClamp(0.0, 0.0, 0.0, 0.0);
        failures += checkDoubleClamp(1.0, 0.0, 0.0, 0.0);

        failures += checkDoubleClamp(Double.NaN, 0.0, 1.0, Double.NaN);
        failures += checkDoubleClamp(Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, Double.NaN);

        failures += checkIllegalArgumentException("clamp(0.0, NaN, NaN)", () -> Math.clamp(0.0, Double.NaN, Double.NaN),
                () -> StrictMath.clamp(0.0, Double.NaN, Double.NaN));
        failures += checkIllegalArgumentException("clamp(0.0, 0.0, NaN)", () -> Math.clamp(0.0, 0.0, Double.NaN),
                () -> StrictMath.clamp(0.0, 0.0, Double.NaN));
        failures += checkIllegalArgumentException("clamp(0.0, NaN, 0.0)", () -> Math.clamp(0.0, Double.NaN, 0.0),
                () -> StrictMath.clamp(0.0, Double.NaN, 0.0));
        failures += checkIllegalArgumentException("clamp(NaN, 1.0, 0.0)", () -> Math.clamp(Double.NaN, 1.0, 0.0),
                () -> StrictMath.clamp(Double.NaN, 1.0, 0.0));
        failures += checkIllegalArgumentException("clamp(0.0, 0.0, -0.0)", () -> Math.clamp(0.0, 0.0, -0.0),
                () -> StrictMath.clamp(0.0, 0.0, -0.0));
        return failures;
    }

    private static int testFloatClamp() {
        int failures = 0;
        failures += checkFloatClamp(-0.1f, 0.0f, 0.5f, 0.0f);
        failures += checkFloatClamp(-0.0f, 0.0f, 0.5f, 0.0f);
        failures += checkFloatClamp(0.0f, 0.0f, 0.5f, 0.0f);
        failures += checkFloatClamp(Float.MIN_VALUE, 0.0f, 0.5f, Float.MIN_VALUE);
        failures += checkFloatClamp(0.2f, 0.0f, 0.5f, 0.2f);
        failures += checkFloatClamp(Math.nextDown(0.5f), 0.0f, 0.5f, Math.nextDown(0.5f));
        failures += checkFloatClamp(0.5f, 0.0f, 0.5f, 0.5f);
        failures += checkFloatClamp(Math.nextUp(0.5f), 0.0f, 0.5f, 0.5f);
        failures += checkFloatClamp(0.6f, 0.0f, 0.5f, 0.5f);

        failures += checkFloatClamp(Float.MAX_VALUE, 0.0f, Float.POSITIVE_INFINITY, Float.MAX_VALUE);
        failures += checkFloatClamp(Float.POSITIVE_INFINITY, 0.0f, Float.MAX_VALUE, Float.MAX_VALUE);
        failures += checkFloatClamp(-Float.MAX_VALUE, Float.NEGATIVE_INFINITY, 0.0f, -Float.MAX_VALUE);
        failures += checkFloatClamp(Float.NEGATIVE_INFINITY, -Float.MAX_VALUE, 0.0f, -Float.MAX_VALUE);

        failures += checkFloatClamp(-1.0f, -0.0f, 0.0f, -0.0f);
        failures += checkFloatClamp(-0.0f, -0.0f, 0.0f, -0.0f);
        failures += checkFloatClamp(0.0f, -0.0f, 0.0f, 0.0f);
        failures += checkFloatClamp(1.0f, -0.0f, 0.0f, 0.0f);
        failures += checkFloatClamp(-1.0f, 0.0f, 0.0f, 0.0f);
        failures += checkFloatClamp(-0.0f, 0.0f, 0.0f, 0.0f);
        failures += checkFloatClamp(0.0f, 0.0f, 0.0f, 0.0f);
        failures += checkFloatClamp(1.0f, 0.0f, 0.0f, 0.0f);

        failures += checkFloatClamp(Float.NaN, 0.0f, 1.0f, Float.NaN);
        failures += checkFloatClamp(Float.NaN, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, Float.NaN);

        failures += checkIllegalArgumentException("clamp(0.0f, NaN, NaN)", () -> Math.clamp(0.0f, Float.NaN, Float.NaN),
                () -> StrictMath.clamp(0.0f, Float.NaN, Float.NaN));
        failures += checkIllegalArgumentException("clamp(0.0f, 0.0f, NaN)", () -> Math.clamp(0.0f, 0.0f, Float.NaN),
                () -> StrictMath.clamp(0.0f, 0.0f, Float.NaN));
        failures += checkIllegalArgumentException("clamp(0.0f, NaN, 0.0f)", () -> Math.clamp(0.0f, Float.NaN, 0.0f),
                () -> StrictMath.clamp(0.0f, Float.NaN, 0.0f));
        failures += checkIllegalArgumentException("clamp(NaN, 1.0f, 0.0f)", () -> Math.clamp(Float.NaN, 1.0f, 0.0f),
                () -> StrictMath.clamp(Float.NaN, 1.0f, 0.0f));
        failures += checkIllegalArgumentException("clamp(0.0f, 0.0f, -0.0f)", () -> Math.clamp(0.0f, 0.0f, -0.0f),
                () -> StrictMath.clamp(0.0f, 0.0f, -0.0f));
        return failures;
    }

    private static int checkIntClamp(long value, int min, int max, int expected) {
        return checkEquals("Math.clamp(" + value + ", " + min + ", " + max + ")", Math.clamp(value, min, max), expected) +
            checkEquals("StrictMath.clamp(" + value + ", " + min + ", " + max + ")", StrictMath.clamp(value, min, max), expected);
    }

    private static int checkLongClamp(long value, long min, long max, long expected) {
        return checkEquals("Math.clamp(" + value + ", " + min + ", " + max + ")", Math.clamp(value, min, max), expected) +
            checkEquals("StrictMath.clamp(" + value + ", " + min + ", " + max + ")", StrictMath.clamp(value, min, max), expected);
    }

    private static int checkFloatClamp(float value, float min, float max, float expected) {
        return checkEquals("Math.clamp(" + value + ", " + min + ", " + max + ")", Math.clamp(value, min, max), expected) +
            checkEquals("StrictMath.clamp(" + value + ", " + min + ", " + max + ")", StrictMath.clamp(value, min, max), expected);
    }

    private static int checkDoubleClamp(double value, double min, double max, double expected) {
        return checkEquals("Math.clamp(" + value + ", " + min + ", " + max + ")", Math.clamp(value, min, max), expected) +
            checkEquals("StrictMath.clamp(" + value + ", " + min + ", " + max + ")", StrictMath.clamp(value, min, max), expected);
    }

    private static int checkIllegalArgumentException(String what, Runnable... runnables) {
        int failures = 0;
        for (Runnable runnable : runnables) {
            try {
                runnable.run();
            }
            catch (IllegalArgumentException ex) {
                continue;
            }
            System.err.println(what+": missing expected exception");
            failures++;
        }
        return failures;
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
