/*
 * Copyright (c) 2012, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * @test Test Math and StrictMath Floor and Ceil Div / Modulo operations.
 * @bug 6282196 8271602
 * @summary Basic tests for Floor and Ceil division and modulo methods for both Math
 * and StrictMath for int and long datatypes.
 */
public class DivModTests {

    /**
     * The count of test errors.
     */
    private static int errors = 0;

    /**
     * @param args the command line arguments are unused
     */
    public static void main(String[] args) {
        errors = 0;
        testIntFloorDivMod();
        testLongIntFloorDivMod();
        testLongFloorDivMod();
        testIntCeilDivMod();
        testLongIntCeilDivMod();
        testLongCeilDivMod();

        if (errors > 0) {
            throw new RuntimeException(errors + " errors found in DivMod methods.");
        }
    }

    /**
     * Report a test failure and increment the error count.
     * @param message the formatting string
     * @param args the variable number of arguments for the message.
     */
    static void fail(String message, Object... args) {
        errors++;
        System.out.printf(message, args);
    }

    /**
     * Test the integer floorDiv and floorMod methods.
     * Math and StrictMath tested and the same results are expected for both.
     */
    static void testIntFloorDivMod() {
        testIntFloorDivMod(4, 0, new ArithmeticException(), new ArithmeticException()); // Should throw ArithmeticException
        testIntFloorDivMod(4, 3, 1, 1);
        testIntFloorDivMod(3, 3, 1, 0);
        testIntFloorDivMod(2, 3, 0, 2);
        testIntFloorDivMod(1, 3, 0, 1);
        testIntFloorDivMod(0, 3, 0, 0);
        testIntFloorDivMod(4, -3, -2, -2);
        testIntFloorDivMod(3, -3, -1, 0);
        testIntFloorDivMod(2, -3, -1, -1);
        testIntFloorDivMod(1, -3, -1, -2);
        testIntFloorDivMod(0, -3, 0, 0);
        testIntFloorDivMod(-1, 3, -1, 2);
        testIntFloorDivMod(-2, 3, -1, 1);
        testIntFloorDivMod(-3, 3, -1, 0);
        testIntFloorDivMod(-4, 3, -2, 2);
        testIntFloorDivMod(-1, -3, 0, -1);
        testIntFloorDivMod(-2, -3, 0, -2);
        testIntFloorDivMod(-3, -3, 1, 0);
        testIntFloorDivMod(-4, -3, 1, -1);
        testIntFloorDivMod(Integer.MAX_VALUE, 1, Integer.MAX_VALUE, 0);
        testIntFloorDivMod(Integer.MAX_VALUE, -1, -Integer.MAX_VALUE, 0);
        testIntFloorDivMod(Integer.MAX_VALUE, 3, 715827882, 1);
        testIntFloorDivMod(Integer.MAX_VALUE - 1, 3, 715827882, 0);
        testIntFloorDivMod(Integer.MIN_VALUE, 3, -715827883, 1);
        testIntFloorDivMod(Integer.MIN_VALUE + 1, 3, -715827883, 2);
        testIntFloorDivMod(Integer.MIN_VALUE + 1, -1, Integer.MAX_VALUE, 0);
        testIntFloorDivMod(Integer.MAX_VALUE, Integer.MAX_VALUE, 1, 0);
        testIntFloorDivMod(Integer.MAX_VALUE, Integer.MIN_VALUE, -1, -1);
        testIntFloorDivMod(Integer.MIN_VALUE, Integer.MIN_VALUE, 1, 0);
        testIntFloorDivMod(Integer.MIN_VALUE, Integer.MAX_VALUE, -2, 2147483646);
        // Special case of integer overflow
        testIntFloorDivMod(Integer.MIN_VALUE, -1, Integer.MIN_VALUE, 0);
    }

    /**
     * Test FloorDiv and then FloorMod with int data.
     */
    static void testIntFloorDivMod(int x, int y, Object divExpected, Object modExpected) {
        testIntFloorDiv(x, y, divExpected);
        testIntFloorMod(x, y, modExpected);
    }

    /**
     * Test FloorDiv with int data.
     */
    static void testIntFloorDiv(int x, int y, Object expected) {
        Object result = doFloorDiv(x, y);
        if (!resultEquals(result, expected)) {
            fail("FAIL: Math.floorDiv(%d, %d) = %s; expected %s%n", x, y, result, expected);
        }

        Object strict_result = doStrictFloorDiv(x, y);
        if (!resultEquals(strict_result, expected)) {
            fail("FAIL: StrictMath.floorDiv(%d, %d) = %s; expected %s%n", x, y, strict_result, expected);
        }
    }

    /**
     * Test FloorMod with int data.
     */
    static void testIntFloorMod(int x, int y, Object expected) {
        Object result = doFloorMod(x, y);
        if (!resultEquals(result, expected)) {
            fail("FAIL: Math.floorMod(%d, %d) = %s; expected %s%n", x, y, result, expected);
        }

        Object strict_result = doStrictFloorMod(x, y);
        if (!resultEquals(strict_result, expected)) {
            fail("FAIL: StrictMath.floorMod(%d, %d) = %s; expected %s%n", x, y, strict_result, expected);
        }

        try {
            // Verify result against double precision floor function
            int tmp = x / y;     // Force ArithmeticException for divide by zero
            double ff = x - Math.floor((double)x / (double)y) * y;
            int fr = (int)ff;
            boolean t = (fr == ((Integer)result));
            if (!result.equals(fr)) {
                fail("FAIL: Math.floorMod(%d, %d) = %s differs from Math.floor(x, y): %d%n", x, y, result, fr);
            }
        } catch (ArithmeticException ae) {
            if (y != 0) {
                fail("FAIL: Math.floorMod(%d, %d); unexpected %s%n", x, y, ae);
            }
        }
    }

    /**
     * Test the floorDiv and floorMod methods for primitive long.
     */
    static void testLongFloorDivMod() {
        testLongFloorDivMod(4L, 0L, new ArithmeticException(), new ArithmeticException()); // Should throw ArithmeticException
        testLongFloorDivMod(4L, 3L, 1L, 1L);
        testLongFloorDivMod(3L, 3L, 1L, 0L);
        testLongFloorDivMod(2L, 3L, 0L, 2L);
        testLongFloorDivMod(1L, 3L, 0L, 1L);
        testLongFloorDivMod(0L, 3L, 0L, 0L);
        testLongFloorDivMod(4L, -3L, -2L, -2L);
        testLongFloorDivMod(3L, -3L, -1L, 0l);
        testLongFloorDivMod(2L, -3L, -1L, -1L);
        testLongFloorDivMod(1L, -3L, -1L, -2L);
        testLongFloorDivMod(0L, -3L, 0L, 0L);
        testLongFloorDivMod(-1L, 3L, -1L, 2L);
        testLongFloorDivMod(-2L, 3L, -1L, 1L);
        testLongFloorDivMod(-3L, 3L, -1L, 0L);
        testLongFloorDivMod(-4L, 3L, -2L, 2L);
        testLongFloorDivMod(-1L, -3L, 0L, -1L);
        testLongFloorDivMod(-2L, -3L, 0L, -2L);
        testLongFloorDivMod(-3L, -3L, 1L, 0L);
        testLongFloorDivMod(-4L, -3L, 1L, -1L);

        testLongFloorDivMod(Long.MAX_VALUE, 1, Long.MAX_VALUE, 0L);
        testLongFloorDivMod(Long.MAX_VALUE, -1, -Long.MAX_VALUE, 0L);
        testLongFloorDivMod(Long.MAX_VALUE, 3L, Long.MAX_VALUE / 3L, 1L);
        testLongFloorDivMod(Long.MAX_VALUE - 1L, 3L, (Long.MAX_VALUE - 1L) / 3L, 0L);
        testLongFloorDivMod(Long.MIN_VALUE, 3L, Long.MIN_VALUE / 3L - 1L, 1L);
        testLongFloorDivMod(Long.MIN_VALUE + 1L, 3L, Long.MIN_VALUE / 3L - 1L, 2L);
        testLongFloorDivMod(Long.MIN_VALUE + 1, -1, Long.MAX_VALUE, 0L);
        testLongFloorDivMod(Long.MAX_VALUE, Long.MAX_VALUE, 1L, 0L);
        testLongFloorDivMod(Long.MAX_VALUE, Long.MIN_VALUE, -1L, -1L);
        testLongFloorDivMod(Long.MIN_VALUE, Long.MIN_VALUE, 1L, 0L);
        testLongFloorDivMod(Long.MIN_VALUE, Long.MAX_VALUE, -2L, 9223372036854775806L);
        // Special case of integer overflow
        testLongFloorDivMod(Long.MIN_VALUE, -1, Long.MIN_VALUE, 0L);
    }

    /**
     * Test the long floorDiv and floorMod methods.
     * Math and StrictMath are tested and the same results are expected for both.
     */
    static void testLongFloorDivMod(long x, long y, Object divExpected, Object modExpected) {
        testLongFloorDiv(x, y, divExpected);
        testLongFloorMod(x, y, modExpected);
    }

    /**
     * Test FloorDiv with long arguments against expected value.
     * The expected value is usually a Long but in some cases  is
     * an ArithmeticException.
     *
     * @param x dividend
     * @param y modulus
     * @param expected expected value,
     */
    static void testLongFloorDiv(long x, long y, Object expected) {
        Object result = doFloorDiv(x, y);
        if (!resultEquals(result, expected)) {
            fail("FAIL: long Math.floorDiv(%d, %d) = %s; expected %s%n", x, y, result, expected);
        }

        Object strict_result = doStrictFloorDiv(x, y);
        if (!resultEquals(strict_result, expected)) {
            fail("FAIL: long StrictMath.floorDiv(%d, %d) = %s; expected %s%n", x, y, strict_result, expected);
        }
    }

    /**
     * Test FloorMod of long arguments against expected value.
     * The expected value is usually a Long but in some cases  is
     * an ArithmeticException.
     *
     * @param x dividend
     * @param y modulus
     * @param expected expected value
     */
    static void testLongFloorMod(long x, long y, Object expected) {
        Object result = doFloorMod(x, y);
        if (!resultEquals(result, expected)) {
            fail("FAIL: long Math.floorMod(%d, %d) = %s; expected %s%n", x, y, result, expected);
        }

        Object strict_result = doStrictFloorMod(x, y);
        if (!resultEquals(strict_result, expected)) {
            fail("FAIL: long StrictMath.floorMod(%d, %d) = %s; expected %s%n", x, y, strict_result, expected);
        }

        try {
            // Verify the result against BigDecimal rounding mode.
            BigDecimal xD = new BigDecimal(x);
            BigDecimal yD = new BigDecimal(y);
            BigDecimal resultD = xD.divide(yD, RoundingMode.FLOOR);
            resultD = resultD.multiply(yD);
            resultD = xD.subtract(resultD);
            long fr = resultD.longValue();
            if (!result.equals(fr)) {
                fail("FAIL: Long.floorMod(%d, %d) = %d is different than BigDecimal result: %d%n", x, y, result, fr);

            }
        } catch (ArithmeticException ae) {
            if (y != 0) {
                fail("FAIL: long Math.floorMod(%d, %d); unexpected ArithmeticException from bigdecimal");
            }
        }
    }

    /**
     * Test the floorDiv and floorMod methods for mixed long and int.
     */
    static void testLongIntFloorDivMod() {
        testLongIntFloorDivMod(4L, 0, new ArithmeticException(), new ArithmeticException()); // Should throw ArithmeticException
        testLongIntFloorDivMod(4L, 3, 1L, 1);
        testLongIntFloorDivMod(3L, 3, 1L, 0);
        testLongIntFloorDivMod(2L, 3, 0L, 2);
        testLongIntFloorDivMod(1L, 3, 0L, 1);
        testLongIntFloorDivMod(0L, 3, 0L, 0);
        testLongIntFloorDivMod(4L, -3, -2L, -2);
        testLongIntFloorDivMod(3L, -3, -1L, 0);
        testLongIntFloorDivMod(2L, -3, -1L, -1);
        testLongIntFloorDivMod(1L, -3, -1L, -2);
        testLongIntFloorDivMod(0L, -3, 0L, 0);
        testLongIntFloorDivMod(-1L, 3, -1L, 2);
        testLongIntFloorDivMod(-2L, 3, -1L, 1);
        testLongIntFloorDivMod(-3L, 3, -1L, 0);
        testLongIntFloorDivMod(-4L, 3, -2L, 2);
        testLongIntFloorDivMod(-1L, -3, 0L, -1);
        testLongIntFloorDivMod(-2L, -3, 0L, -2);
        testLongIntFloorDivMod(-3L, -3, 1L, 0);
        testLongIntFloorDivMod(-4L, -3, 1L, -1);

        testLongIntFloorDivMod(Long.MAX_VALUE, 1, Long.MAX_VALUE, 0);
        testLongIntFloorDivMod(Long.MAX_VALUE, -1, -Long.MAX_VALUE, 0);
        testLongIntFloorDivMod(Long.MAX_VALUE, 3, Long.MAX_VALUE / 3L, 1);
        testLongIntFloorDivMod(Long.MAX_VALUE - 1L, 3, (Long.MAX_VALUE - 1L) / 3L, 0);
        testLongIntFloorDivMod(Long.MIN_VALUE, 3, Long.MIN_VALUE / 3L - 1L, 1);
        testLongIntFloorDivMod(Long.MIN_VALUE + 1L, 3, Long.MIN_VALUE / 3L - 1L, 2);
        testLongIntFloorDivMod(Long.MIN_VALUE + 1, -1, Long.MAX_VALUE, 0);
        testLongIntFloorDivMod(Long.MAX_VALUE, Integer.MAX_VALUE, 4294967298L, 1);
        testLongIntFloorDivMod(Long.MAX_VALUE, Integer.MIN_VALUE, -4294967296L, -1);
        testLongIntFloorDivMod(Long.MIN_VALUE, Integer.MIN_VALUE, 4294967296L, 0);
        testLongIntFloorDivMod(Long.MIN_VALUE, Integer.MAX_VALUE, -4294967299L, 2147483645);
        // Special case of integer overflow
        testLongIntFloorDivMod(Long.MIN_VALUE, -1, Long.MIN_VALUE, 0);
    }

    /**
     * Test the integer floorDiv and floorMod methods.
     * Math and StrictMath are tested and the same results are expected for both.
     */
    static void testLongIntFloorDivMod(long x, int y, Object divExpected, Object modExpected) {
        testLongIntFloorDiv(x, y, divExpected);
        testLongIntFloorMod(x, y, modExpected);
    }

    /**
     * Test FloorDiv with long arguments against expected value.
     * The expected value is usually a Long but in some cases  is
     * an ArithmeticException.
     *
     * @param x dividend
     * @param y modulus
     * @param expected expected value,
     */
    static void testLongIntFloorDiv(long x, int y, Object expected) {
        Object result = doFloorDiv(x, y);
        if (!resultEquals(result, expected)) {
            fail("FAIL: long Math.floorDiv(%d, %d) = %s; expected %s%n", x, y, result, expected);
        }

        Object strict_result = doStrictFloorDiv(x, y);
        if (!resultEquals(strict_result, expected)) {
            fail("FAIL: long StrictMath.floorDiv(%d, %d) = %s; expected %s%n", x, y, strict_result, expected);
        }
    }

    /**
     * Test FloorMod of long arguments against expected value.
     * The expected value is usually a Long but in some cases  is
     * an ArithmeticException.
     *
     * @param x dividend
     * @param y modulus
     * @param expected expected value
     */
    static void testLongIntFloorMod(long x, int y, Object expected) {
        Object result = doFloorMod(x, y);
        if (!resultEquals(result, expected)) {
            fail("FAIL: int Math.floorMod(%d, %d) = %s; expected %s%n", x, y, result, expected);
        }

        Object strict_result = doStrictFloorMod(x, y);
        if (!resultEquals(strict_result, expected)) {
            fail("FAIL: int StrictMath.floorMod(%d, %d) = %s; expected %s%n", x, y, strict_result, expected);
        }

        try {
            // Verify the result against BigDecimal rounding mode.
            BigDecimal xD = new BigDecimal(x);
            BigDecimal yD = new BigDecimal(y);
            BigDecimal resultD = xD.divide(yD, RoundingMode.FLOOR);
            resultD = resultD.multiply(yD);
            resultD = xD.subtract(resultD);
            int fr = resultD.intValue();
            if (!result.equals(fr)) {
                fail("FAIL: Long.floorMod(%d, %d) = %d is different than BigDecimal result: %d%n", x, y, result, fr);

            }
        } catch (ArithmeticException ae) {
            if (y != 0) {
                fail("FAIL: long Math.floorMod(%d, %d); unexpected ArithmeticException from bigdecimal");
            }
        }
    }

    /**
     * Invoke floorDiv and return the result or any exception.
     * @param x the x value
     * @param y the y value
     * @return the result Integer or an exception.
     */
    static Object doFloorDiv(int x, int y) {
        try {
            return Math.floorDiv(x, y);
        } catch (ArithmeticException ae) {
            return ae;
        }
    }

    /**
     * Invoke floorDiv and return the result or any exception.
     * @param x the x value
     * @param y the y value
     * @return the result Integer or an exception.
     */
    static Object doFloorDiv(long x, int y) {
        try {
            return Math.floorDiv(x, y);
        } catch (ArithmeticException ae) {
            return ae;
        }
    }

    /**
     * Invoke floorDiv and return the result or any exception.
     * @param x the x value
     * @param y the y value
     * @return the result Integer or an exception.
     */
    static Object doFloorDiv(long x, long y) {
        try {
            return Math.floorDiv(x, y);
        } catch (ArithmeticException ae) {
            return ae;
        }
    }

    /**
     * Invoke floorMod and return the result or any exception.
     * @param x the x value
     * @param y the y value
     * @return the result Integer or an exception.
     */
    static Object doFloorMod(int x, int y) {
        try {
            return Math.floorMod(x, y);
        } catch (ArithmeticException ae) {
            return ae;
        }
    }

    /**
     * Invoke floorMod and return the result or any exception.
     * @param x the x value
     * @param y the y value
     * @return the result Integer or an exception.
     */
    static Object doFloorMod(long x, int y) {
        try {
            return Math.floorMod(x, y);
        } catch (ArithmeticException ae) {
            return ae;
        }
    }

    /**
     * Invoke floorMod and return the result or any exception.
     * @param x the x value
     * @param y the y value
     * @return the result Integer or an exception.
     */
    static Object doFloorMod(long x, long y) {
        try {
            return Math.floorMod(x, y);
        } catch (ArithmeticException ae) {
            return ae;
        }
    }

    /**
     * Invoke floorDiv and return the result or any exception.
     * @param x the x value
     * @param y the y value
     * @return the result Integer or an exception.
     */
    static Object doStrictFloorDiv(int x, int y) {
        try {
            return StrictMath.floorDiv(x, y);
        } catch (ArithmeticException ae) {
            return ae;
        }
    }

    /**
     * Invoke floorDiv and return the result or any exception.
     * @param x the x value
     * @param y the y value
     * @return the result Integer or an exception.
     */
    static Object doStrictFloorDiv(long x, int y) {
        try {
            return StrictMath.floorDiv(x, y);
        } catch (ArithmeticException ae) {
            return ae;
        }
    }

    /**
     * Invoke floorDiv and return the result or any exception.
     * @param x the x value
     * @param y the y value
     * @return the result Integer or an exception.
     */
    static Object doStrictFloorDiv(long x, long y) {
        try {
            return StrictMath.floorDiv(x, y);
        } catch (ArithmeticException ae) {
            return ae;
        }
    }

    /**
     * Invoke floorMod and return the result or any exception.
     * @param x the x value
     * @param y the y value
     * @return the result Integer or an exception.
     */
    static Object doStrictFloorMod(int x, int y) {
        try {
            return StrictMath.floorMod(x, y);
        } catch (ArithmeticException ae) {
            return ae;
        }
    }

    /**
     * Invoke floorMod and return the result or any exception.
     * @param x the x value
     * @param y the y value
     * @return the result Integer or an exception.
     */
    static Object doStrictFloorMod(long x, int y) {
        try {
            return StrictMath.floorMod(x, y);
        } catch (ArithmeticException ae) {
            return ae;
        }
    }

    /**
     * Invoke floorMod and return the result or any exception.
     * @param x the x value
     * @param y the y value
     * @return the result Integer or an exception.
     */
    static Object doStrictFloorMod(long x, long y) {
        try {
            return StrictMath.floorMod(x, y);
        } catch (ArithmeticException ae) {
            return ae;
        }
    }

    /**
     * Test the integer ceilDiv and ceilMod methods.
     * Math and StrictMath tested and the same results are expected for both.
     */
    static void testIntCeilDivMod() {
        testIntCeilDivMod(4, 0, new ArithmeticException(), new ArithmeticException()); // Should throw ArithmeticException
        testIntCeilDivMod(4, 3, 2, -2);
        testIntCeilDivMod(3, 3, 1, 0);
        testIntCeilDivMod(2, 3, 1, -1);
        testIntCeilDivMod(1, 3, 1, -2);
        testIntCeilDivMod(0, 3, 0, 0);
        testIntCeilDivMod(4, -3, -1, 1);
        testIntCeilDivMod(3, -3, -1, 0);
        testIntCeilDivMod(2, -3, 0, 2);
        testIntCeilDivMod(1, -3, 0, 1);
        testIntCeilDivMod(0, -3, 0, 0);
        testIntCeilDivMod(-1, 3, 0, -1);
        testIntCeilDivMod(-2, 3, 0, -2);
        testIntCeilDivMod(-3, 3, -1, 0);
        testIntCeilDivMod(-4, 3, -1, -1);
        testIntCeilDivMod(-1, -3, 1, 2);
        testIntCeilDivMod(-2, -3, 1, 1);
        testIntCeilDivMod(-3, -3, 1, 0);
        testIntCeilDivMod(-4, -3, 2, 2);
        testIntCeilDivMod(Integer.MAX_VALUE, 1, Integer.MAX_VALUE, 0);
        testIntCeilDivMod(Integer.MAX_VALUE, -1, -Integer.MAX_VALUE, 0);
        testIntCeilDivMod(Integer.MAX_VALUE, 3, 715_827_883, -2);
        testIntCeilDivMod(Integer.MAX_VALUE - 1, 3, 715_827_882, 0);
        testIntCeilDivMod(Integer.MIN_VALUE, 3, -715_827_882, -2);
        testIntCeilDivMod(Integer.MIN_VALUE + 1, 3, -715_827_882, -1);
        testIntCeilDivMod(Integer.MIN_VALUE + 1, -1, Integer.MAX_VALUE, 0);
        testIntCeilDivMod(Integer.MAX_VALUE, Integer.MAX_VALUE, 1, 0);
        testIntCeilDivMod(Integer.MAX_VALUE, Integer.MIN_VALUE, 0, Integer.MAX_VALUE);
        testIntCeilDivMod(Integer.MIN_VALUE, Integer.MIN_VALUE, 1, 0);
        testIntCeilDivMod(Integer.MIN_VALUE, Integer.MAX_VALUE, -1, -1);
        // Special case of integer overflow
        testIntCeilDivMod(Integer.MIN_VALUE, -1, Integer.MIN_VALUE, 0);
    }

    /**
     * Test CeilDiv and then CeilMod with int data.
     */
    static void testIntCeilDivMod(int x, int y, Object divExpected, Object modExpected) {
        testIntCeilDiv(x, y, divExpected);
        testIntCeilMod(x, y, modExpected);
    }

    /**
     * Test CeilDiv with int data.
     */
    static void testIntCeilDiv(int x, int y, Object expected) {
        Object result = doCeilDiv(x, y);
        if (!resultEquals(result, expected)) {
            fail("FAIL: Math.ceilDiv(%d, %d) = %s; expected %s%n", x, y, result, expected);
        }

        Object strict_result = doStrictCeilDiv(x, y);
        if (!resultEquals(strict_result, expected)) {
            fail("FAIL: StrictMath.ceilDiv(%d, %d) = %s; expected %s%n", x, y, strict_result, expected);
        }
    }

    /**
     * Test CeilMod with int data.
     */
    static void testIntCeilMod(int x, int y, Object expected) {
        Object result = doCeilMod(x, y);
        if (!resultEquals(result, expected)) {
            fail("FAIL: Math.ceilMod(%d, %d) = %s; expected %s%n", x, y, result, expected);
        }

        Object strict_result = doStrictCeilMod(x, y);
        if (!resultEquals(strict_result, expected)) {
            fail("FAIL: StrictMath.ceilMod(%d, %d) = %s; expected %s%n", x, y, strict_result, expected);
        }

        try {
            // Verify result against double precision ceil function
            int tmp = x / y;     // Force ArithmeticException for divide by zero
            double ff = x - Math.ceil((double)x / (double)y) * y;
            int fr = (int)ff;
            boolean t = (fr == ((Integer)result));
            if (!result.equals(fr)) {
                fail("FAIL: Math.ceilMod(%d, %d) = %s differs from Math.ceil(x, y): %d%n", x, y, result, fr);
            }
        } catch (ArithmeticException ae) {
            if (y != 0) {
                fail("FAIL: Math.ceilMod(%d, %d); unexpected %s%n", x, y, ae);
            }
        }
    }

    /**
     * Test the ceilDiv and ceilMod methods for primitive long.
     */
    static void testLongCeilDivMod() {
        testLongCeilDivMod(4L, 0L, new ArithmeticException(), new ArithmeticException()); // Should throw ArithmeticException
        testLongCeilDivMod(4L, 3L, 2L, -2L);
        testLongCeilDivMod(3L, 3L, 1L, 0L);
        testLongCeilDivMod(2L, 3L, 1L, -1L);
        testLongCeilDivMod(1L, 3L, 1L, -2L);
        testLongCeilDivMod(0L, 3L, 0L, 0L);
        testLongCeilDivMod(4L, -3L, -1L, 1L);
        testLongCeilDivMod(3L, -3L, -1L, 0L);
        testLongCeilDivMod(2L, -3L, 0L, 2L);
        testLongCeilDivMod(1L, -3L, 0L, 1L);
        testLongCeilDivMod(0L, -3L, 0L, 0L);
        testLongCeilDivMod(-1L, 3L, 0L, -1L);
        testLongCeilDivMod(-2L, 3L, 0L, -2L);
        testLongCeilDivMod(-3L, 3L, -1L, 0L);
        testLongCeilDivMod(-4L, 3L, -1L, -1L);
        testLongCeilDivMod(-1L, -3L, 1L, 2L);
        testLongCeilDivMod(-2L, -3L, 1L, 1L);
        testLongCeilDivMod(-3L, -3L, 1L, 0L);
        testLongCeilDivMod(-4L, -3L, 2L, 2L);

        testLongCeilDivMod(Long.MAX_VALUE, 1, Long.MAX_VALUE, 0L);
        testLongCeilDivMod(Long.MAX_VALUE, -1, -Long.MAX_VALUE, 0L);
        testLongCeilDivMod(Long.MAX_VALUE, 3L, Long.MAX_VALUE / 3L + 1, -2L);
        testLongCeilDivMod(Long.MAX_VALUE - 1L, 3L, (Long.MAX_VALUE - 1L) / 3L, 0L);
        testLongCeilDivMod(Long.MIN_VALUE, 3L, Long.MIN_VALUE / 3L, -2L);
        testLongCeilDivMod(Long.MIN_VALUE + 1L, 3L, Long.MIN_VALUE / 3L, -1L);
        testLongCeilDivMod(Long.MIN_VALUE + 1, -1, Long.MAX_VALUE, 0L);
        testLongCeilDivMod(Long.MAX_VALUE, Long.MAX_VALUE, 1L, 0L);
        testLongCeilDivMod(Long.MAX_VALUE, Long.MIN_VALUE, 0L, Long.MAX_VALUE);
        testLongCeilDivMod(Long.MIN_VALUE, Long.MIN_VALUE, 1L, 0L);
        testLongCeilDivMod(Long.MIN_VALUE, Long.MAX_VALUE, -1L, -1L);
        // Special case of integer overflow
        testLongCeilDivMod(Long.MIN_VALUE, -1, Long.MIN_VALUE, 0L);
    }

    /**
     * Test the long ceilDiv and ceilMod methods.
     * Math and StrictMath are tested and the same results are expected for both.
     */
    static void testLongCeilDivMod(long x, long y, Object divExpected, Object modExpected) {
        testLongCeilDiv(x, y, divExpected);
        testLongCeilMod(x, y, modExpected);
    }

    /**
     * Test CeilDiv with long arguments against expected value.
     * The expected value is usually a Long but in some cases  is
     * an ArithmeticException.
     *
     * @param x dividend
     * @param y modulus
     * @param expected expected value,
     */
    static void testLongCeilDiv(long x, long y, Object expected) {
        Object result = doCeilDiv(x, y);
        if (!resultEquals(result, expected)) {
            fail("FAIL: long Math.ceilDiv(%d, %d) = %s; expected %s%n", x, y, result, expected);
        }

        Object strict_result = doStrictCeilDiv(x, y);
        if (!resultEquals(strict_result, expected)) {
            fail("FAIL: long StrictMath.ceilDiv(%d, %d) = %s; expected %s%n", x, y, strict_result, expected);
        }
    }

    /**
     * Test CeilMod of long arguments against expected value.
     * The expected value is usually a Long but in some cases  is
     * an ArithmeticException.
     *
     * @param x dividend
     * @param y modulus
     * @param expected expected value
     */
    static void testLongCeilMod(long x, long y, Object expected) {
        Object result = doCeilMod(x, y);
        if (!resultEquals(result, expected)) {
            fail("FAIL: long Math.ceilMod(%d, %d) = %s; expected %s%n", x, y, result, expected);
        }

        Object strict_result = doStrictCeilMod(x, y);
        if (!resultEquals(strict_result, expected)) {
            fail("FAIL: long StrictMath.ceilMod(%d, %d) = %s; expected %s%n", x, y, strict_result, expected);
        }

        try {
            // Verify the result against BigDecimal rounding mode.
            BigDecimal xD = new BigDecimal(x);
            BigDecimal yD = new BigDecimal(y);
            BigDecimal resultD = xD.divide(yD, RoundingMode.CEILING);
            resultD = resultD.multiply(yD);
            resultD = xD.subtract(resultD);
            long fr = resultD.longValue();
            if (!result.equals(fr)) {
                fail("FAIL: Long.ceilMod(%d, %d) = %d is different than BigDecimal result: %d%n", x, y, result, fr);

            }
        } catch (ArithmeticException ae) {
            if (y != 0) {
                fail("FAIL: long Math.ceilMod(%d, %d); unexpected ArithmeticException from bigdecimal");
            }
        }
    }

    /**
     * Test the ceilDiv and ceilMod methods for mixed long and int.
     */
    static void testLongIntCeilDivMod() {
        testLongIntCeilDivMod(4L, 0, new ArithmeticException(), new ArithmeticException()); // Should throw ArithmeticException
        testLongIntCeilDivMod(4L, 3, 2L, -2);
        testLongIntCeilDivMod(3L, 3, 1L, 0);
        testLongIntCeilDivMod(2L, 3, 1L, -1);
        testLongIntCeilDivMod(1L, 3, 1L, -2);
        testLongIntCeilDivMod(0L, 3, 0L, 0);
        testLongIntCeilDivMod(4L, -3, -1L, 1);
        testLongIntCeilDivMod(3L, -3, -1L, 0);
        testLongIntCeilDivMod(2L, -3, 0L, 2);
        testLongIntCeilDivMod(1L, -3, 0L, 1);
        testLongIntCeilDivMod(0L, -3, 0L, 0);
        testLongIntCeilDivMod(-1L, 3, 0L, -1);
        testLongIntCeilDivMod(-2L, 3, 0L, -2);
        testLongIntCeilDivMod(-3L, 3, -1L, 0);
        testLongIntCeilDivMod(-4L, 3, -1L, -1);
        testLongIntCeilDivMod(-1L, -3, 1L, 2);
        testLongIntCeilDivMod(-2L, -3, 1L, 1);
        testLongIntCeilDivMod(-3L, -3, 1L, 0);
        testLongIntCeilDivMod(-4L, -3, 2L, 2);

        testLongIntCeilDivMod(Long.MAX_VALUE, 1, Long.MAX_VALUE, 0);
        testLongIntCeilDivMod(Long.MAX_VALUE, -1, -Long.MAX_VALUE, 0);
        testLongIntCeilDivMod(Long.MAX_VALUE, 3, Long.MAX_VALUE / 3L + 1, -2);
        testLongIntCeilDivMod(Long.MAX_VALUE - 1L, 3, (Long.MAX_VALUE - 1L) / 3L, 0);
        testLongIntCeilDivMod(Long.MIN_VALUE, 3, Long.MIN_VALUE / 3L, -2);
        testLongIntCeilDivMod(Long.MIN_VALUE + 1L, 3, Long.MIN_VALUE / 3L, -1);
        testLongIntCeilDivMod(Long.MIN_VALUE + 1, -1, Long.MAX_VALUE, 0);
        testLongIntCeilDivMod(Long.MAX_VALUE, Integer.MAX_VALUE, 4_294_967_299L, -2_147_483_646);
        testLongIntCeilDivMod(Long.MAX_VALUE, Integer.MIN_VALUE, -4_294_967_295L, 2_147_483_647);
        testLongIntCeilDivMod(Long.MIN_VALUE, Integer.MIN_VALUE, 4_294_967_296L, 0);
        testLongIntCeilDivMod(Long.MIN_VALUE, Integer.MAX_VALUE, -4_294_967_298L, -2);
        // Special case of integer overflow
        testLongIntCeilDivMod(Long.MIN_VALUE, -1, Long.MIN_VALUE, 0);
    }

    /**
     * Test the integer ceilDiv and ceilMod methods.
     * Math and StrictMath are tested and the same results are expected for both.
     */
    static void testLongIntCeilDivMod(long x, int y, Object divExpected, Object modExpected) {
        testLongIntCeilDiv(x, y, divExpected);
        testLongIntCeilMod(x, y, modExpected);
    }

    /**
     * Test CeilDiv with long arguments against expected value.
     * The expected value is usually a Long but in some cases  is
     * an ArithmeticException.
     *
     * @param x dividend
     * @param y modulus
     * @param expected expected value,
     */
    static void testLongIntCeilDiv(long x, int y, Object expected) {
        Object result = doCeilDiv(x, y);
        if (!resultEquals(result, expected)) {
            fail("FAIL: long Math.ceilDiv(%d, %d) = %s; expected %s%n", x, y, result, expected);
        }

        Object strict_result = doStrictCeilDiv(x, y);
        if (!resultEquals(strict_result, expected)) {
            fail("FAIL: long StrictMath.ceilDiv(%d, %d) = %s; expected %s%n", x, y, strict_result, expected);
        }
    }

    /**
     * Test CeilMod of long arguments against expected value.
     * The expected value is usually a Long but in some cases  is
     * an ArithmeticException.
     *
     * @param x dividend
     * @param y modulus
     * @param expected expected value
     */
    static void testLongIntCeilMod(long x, int y, Object expected) {
        Object result = doCeilMod(x, y);
        if (!resultEquals(result, expected)) {
            fail("FAIL: int Math.ceilMod(%d, %d) = %s; expected %s%n", x, y, result, expected);
        }

        Object strict_result = doStrictCeilMod(x, y);
        if (!resultEquals(strict_result, expected)) {
            fail("FAIL: int StrictMath.ceilMod(%d, %d) = %s; expected %s%n", x, y, strict_result, expected);
        }

        try {
            // Verify the result against BigDecimal rounding mode.
            BigDecimal xD = new BigDecimal(x);
            BigDecimal yD = new BigDecimal(y);
            BigDecimal resultD = xD.divide(yD, RoundingMode.CEILING);
            resultD = resultD.multiply(yD);
            resultD = xD.subtract(resultD);
            int fr = resultD.intValue();
            if (!result.equals(fr)) {
                fail("FAIL: Long.ceilMod(%d, %d) = %d is different than BigDecimal result: %d%n", x, y, result, fr);

            }
        } catch (ArithmeticException ae) {
            if (y != 0) {
                fail("FAIL: long Math.ceilMod(%d, %d); unexpected ArithmeticException from bigdecimal");
            }
        }
    }

    /**
     * Invoke ceilDiv and return the result or any exception.
     * @param x the x value
     * @param y the y value
     * @return the result Integer or an exception.
     */
    static Object doCeilDiv(int x, int y) {
        try {
            return Math.ceilDiv(x, y);
        } catch (ArithmeticException ae) {
            return ae;
        }
    }

    /**
     * Invoke ceilDiv and return the result or any exception.
     * @param x the x value
     * @param y the y value
     * @return the result Integer or an exception.
     */
    static Object doCeilDiv(long x, int y) {
        try {
            return Math.ceilDiv(x, y);
        } catch (ArithmeticException ae) {
            return ae;
        }
    }

    /**
     * Invoke ceilDiv and return the result or any exception.
     * @param x the x value
     * @param y the y value
     * @return the result Integer or an exception.
     */
    static Object doCeilDiv(long x, long y) {
        try {
            return Math.ceilDiv(x, y);
        } catch (ArithmeticException ae) {
            return ae;
        }
    }

    /**
     * Invoke ceilMod and return the result or any exception.
     * @param x the x value
     * @param y the y value
     * @return the result Integer or an exception.
     */
    static Object doCeilMod(int x, int y) {
        try {
            return Math.ceilMod(x, y);
        } catch (ArithmeticException ae) {
            return ae;
        }
    }

    /**
     * Invoke ceilMod and return the result or any exception.
     * @param x the x value
     * @param y the y value
     * @return the result Integer or an exception.
     */
    static Object doCeilMod(long x, int y) {
        try {
            return Math.ceilMod(x, y);
        } catch (ArithmeticException ae) {
            return ae;
        }
    }

    /**
     * Invoke ceilMod and return the result or any exception.
     * @param x the x value
     * @param y the y value
     * @return the result Integer or an exception.
     */
    static Object doCeilMod(long x, long y) {
        try {
            return Math.ceilMod(x, y);
        } catch (ArithmeticException ae) {
            return ae;
        }
    }

    /**
     * Invoke ceilDiv and return the result or any exception.
     * @param x the x value
     * @param y the y value
     * @return the result Integer or an exception.
     */
    static Object doStrictCeilDiv(int x, int y) {
        try {
            return StrictMath.ceilDiv(x, y);
        } catch (ArithmeticException ae) {
            return ae;
        }
    }

    /**
     * Invoke ceilDiv and return the result or any exception.
     * @param x the x value
     * @param y the y value
     * @return the result Integer or an exception.
     */
    static Object doStrictCeilDiv(long x, int y) {
        try {
            return StrictMath.ceilDiv(x, y);
        } catch (ArithmeticException ae) {
            return ae;
        }
    }

    /**
     * Invoke ceilDiv and return the result or any exception.
     * @param x the x value
     * @param y the y value
     * @return the result Integer or an exception.
     */
    static Object doStrictCeilDiv(long x, long y) {
        try {
            return StrictMath.ceilDiv(x, y);
        } catch (ArithmeticException ae) {
            return ae;
        }
    }

    /**
     * Invoke ceilMod and return the result or any exception.
     * @param x the x value
     * @param y the y value
     * @return the result Integer or an exception.
     */
    static Object doStrictCeilMod(int x, int y) {
        try {
            return StrictMath.ceilMod(x, y);
        } catch (ArithmeticException ae) {
            return ae;
        }
    }

    /**
     * Invoke ceilMod and return the result or any exception.
     * @param x the x value
     * @param y the y value
     * @return the result Integer or an exception.
     */
    static Object doStrictCeilMod(long x, int y) {
        try {
            return StrictMath.ceilMod(x, y);
        } catch (ArithmeticException ae) {
            return ae;
        }
    }

    /**
     * Invoke ceilMod and return the result or any exception.
     * @param x the x value
     * @param y the y value
     * @return the result Integer or an exception.
     */
    static Object doStrictCeilMod(long x, long y) {
        try {
            return StrictMath.ceilMod(x, y);
        } catch (ArithmeticException ae) {
            return ae;
        }
    }

    /**
     * Returns a boolean by comparing the result and the expected value.
     * The equals method is not defined for ArithmeticException but it is
     * desirable to have equals return true if the expected and the result
     * both threw the same exception (class and message.)
     *
     * @param result the result from testing the method
     * @param expected the expected value
     * @return true if the result is equal to the expected values; false otherwise.
     */
    static boolean resultEquals(Object result, Object expected) {
        if (result.getClass() != expected.getClass()) {
            fail("FAIL: Result type mismatch, %s; expected: %s%n",
                    result.getClass().getName(), expected.getClass().getName());
            return false;
        }

        if (result.equals(expected)) {
            return true;
        }
        // Handle special case to compare ArithmeticExceptions
        if (result instanceof ArithmeticException && expected instanceof ArithmeticException) {
            return true;
        }
        return false;
    }

}
