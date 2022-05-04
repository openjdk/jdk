/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
import java.util.function.Function;

import static java.lang.Math.*;

/**
 * @test
 * @summary Test Math to*Exact(*) methods.
 * @bug 8279986
 */

/*
 * To avoid a combinatorial explosion of argument and return types,
 * all integer types are lifted to Long and
 * all floating-point types are lifted to Double.
 * These conversions are lossless, even for special floating-point values.
 */
public class ToExact {

    private static int failures;
    private static int errors;


    private ToExact() {
        throw new AssertionError();
    }

    public static void main(String[] args) {
        failures = 0;
        errors = 0;
        testToByteExact_long();
        testToByteExact_double();
        testToShortExact_long();
        testToShortExact_double();
        testToIntExact_long();
        testToIntExact_double();

        testToLongExact_double();
        testToDoubleExact_long();

        testToFloatExact_long();
        testToFloatExact_double();

        testToUnsignedIntExact_long();
        testToUnsignedIntExact_double();
        testToUnsignedShortExact_long();
        testToUnsignedShortExact_double();
        testToUnsignedByteExact_long();
        testToUnsignedByteExact_double();
        if (failures > 0 || errors > 0) {
            throw new RuntimeException(
                    String.format("%d failures and %d errors%n", failures, errors));
        }
    }

    private static void testToByteExact_long() {
        testCommonToExact_long("toByteExact", (Number v) -> toByteExact(v.longValue()));
        testToByteExact(Byte.MIN_VALUE - 1L, null);
        testToByteExact((long) Byte.MIN_VALUE, (long) Byte.MIN_VALUE);
        testToByteExact(Byte.MIN_VALUE + 1L, Byte.MIN_VALUE + 1L);
        testToByteExact(Byte.MAX_VALUE - 1L, Byte.MAX_VALUE - 1L);
        testToByteExact((long) Byte.MAX_VALUE, (long) Byte.MAX_VALUE);
        testToByteExact(Byte.MAX_VALUE + 1L, null);
    }

    private static void testToByteExact_double() {
        testCommonToExact_double("toByteExact", (Number v) -> toByteExact(v.doubleValue()));
        testToByteExact(-0x1p7 - 1, null);
        testToByteExact(-0x1p7, (long) Byte.MIN_VALUE);
        testToByteExact(-0x1p7 + 1, Byte.MIN_VALUE + 1L);
        testToByteExact(0x1p7 - 2, Byte.MAX_VALUE - 1L);
        testToByteExact(0x1p7 - 1, (long) Byte.MAX_VALUE);
        testToByteExact(0x1p7, null);
    }

    private static void testToShortExact_long() {
        testCommonToExact_long("toShortExact", (Number v) -> toShortExact(v.longValue()));
        testToShortExact(Short.MIN_VALUE - 1, null);
        testToShortExact(Short.MIN_VALUE, (long) Short.MIN_VALUE);
        testToShortExact(Short.MIN_VALUE + 1, Short.MIN_VALUE + 1L);
        testToShortExact(Short.MAX_VALUE - 1, Short.MAX_VALUE - 1L);
        testToShortExact(Short.MAX_VALUE, (long) Short.MAX_VALUE);
        testToShortExact(Short.MAX_VALUE + 1, null);
    }

    private static void testToShortExact_double() {
        testCommonToExact_double("toShortExact", (Number v) -> toShortExact(v.doubleValue()));
        testToShortExact(-0x1p15 - 1, null);
        testToShortExact(-0x1p15, (long) Short.MIN_VALUE);
        testToShortExact(-0x1p15 + 1, Short.MIN_VALUE + 1L);
        testToShortExact(0x1p15 - 2, Short.MAX_VALUE - 1L);
        testToShortExact(0x1p15 - 1, (long) Short.MAX_VALUE);
        testToShortExact(0x1p15, null);
    }

    private static void testToIntExact_long() {
        testCommonToExact_long("toIntExact", (Number v) -> toIntExact(v.longValue()));
        testToIntExact(Integer.MIN_VALUE - 1L, null);
        testToIntExact((long) Integer.MIN_VALUE, (long) Integer.MIN_VALUE);
        testToIntExact(Integer.MIN_VALUE + 1L, Integer.MIN_VALUE + 1L);
        testToIntExact(Integer.MAX_VALUE - 1L, Integer.MAX_VALUE - 1L);
        testToIntExact((long) Integer.MAX_VALUE, (long) Integer.MAX_VALUE);
        testToIntExact(Integer.MAX_VALUE + 1L, null);
    }

    private static void testToIntExact_double() {
        testCommonToExact_double("toIntExact", (Number v) -> toIntExact(v.doubleValue()));
        testToIntExact(-0x1p31 - 1, null);
        testToIntExact(-0x1p31, (long) Integer.MIN_VALUE);
        testToIntExact(-0x1p31 + 0.5, null);
        testToIntExact(-0x1p31 + 1, Integer.MIN_VALUE + 1L);
        testToIntExact(0x1p31 - 2, Integer.MAX_VALUE - 1L);
        testToIntExact(0x1p31 - 1, (long) Integer.MAX_VALUE);
        testToIntExact(0x1p31 - 0.5, null);
        testToIntExact(0x1p31, null);
    }

    private static void testToLongExact_double() {
        testCommonToExact_double("toLongExact", (Number v) -> toLongExact(v.doubleValue()));
        testToLongExact(-0x1.0p63, Long.MIN_VALUE);
        testToLongExact(-0x1p31 - 1, Integer.MIN_VALUE - 1L);
        testToLongExact(-0x1p31, (long) Integer.MIN_VALUE);
        testToLongExact(-0x1p31 + 0.5, null);
        testToLongExact(-0x1p31 + 1, Integer.MIN_VALUE + 1L);
        testToLongExact(0x1p31 - 2, Integer.MAX_VALUE - 1L);
        testToLongExact(0x1p31 - 1, (long) Integer.MAX_VALUE);
        testToLongExact(-0x1p31 - 0.5, null);
        testToLongExact(0x1p31, Integer.MAX_VALUE + 1L);
        testToLongExact(0x1.0p63, null);
    }

    private static void testToFloatExact_long() {
        testCommonToExact_long("toFloatExact", (Number v) -> toFloatExact(v.longValue()));
        testToFloatExact(Long.MIN_VALUE, (double) -0x1.0p63f);
        testToFloatExact(Long.MIN_VALUE + 1L, null);
        testToFloatExact(Integer.MIN_VALUE - 1L, null);
        testToFloatExact((long) Integer.MIN_VALUE, (double) -0x1p31f);
        testToFloatExact(Integer.MIN_VALUE + 1L, null);
        testToFloatExact(Integer.MAX_VALUE - 1L, null);
        testToFloatExact((long) Integer.MAX_VALUE, null);
        testToFloatExact(Integer.MAX_VALUE + 1L, (double) 0x1p31f);
        testToFloatExact(Long.MAX_VALUE - 1L, null);
        testToFloatExact(Long.MAX_VALUE, null);
    }

    private static void testToFloatExact_double() {
        testToFloatExact(-0x1p63, (double) -0x1p63f);
        testToFloatExact(-0x1p31 - 1, null);
        testToFloatExact(-0x1p31, (double) -0x1p31f);
        testToFloatExact(-0x1p31 + 0.5, null);
        testToFloatExact(-0x1p31 + 1, null);
        testToFloatExact(-0x1p24 + 1, (double) (-0x1p24f + 1f));
        testToFloatExact(-0x1p23 + 0.5, (double) (-0x1p23f + 0.5f));
        testToFloatExact(0x1p23 - 0.5, (double) (0x1p23f - 0.5f));
        testToFloatExact(0x1p24 - 1, (double) (0x1p24f - 1f));
        testToFloatExact(0x1p31 - 2, null);
        testToFloatExact(0x1p31 - 1, null);
        testToFloatExact(-0x1p31 - 0.5, null);
        testToFloatExact(0x1p31, (double) 0x1p31f);
        testToFloatExact(0x1.0p63, (double) 0x1p63f);

        testToFloatExact(Double.NEGATIVE_INFINITY, (double) Float.NEGATIVE_INFINITY);
        testToFloatExact(-0.0, (double) -0.0f);
        testToFloatExact(0.0, (double) 0.0f);
        testToFloatExact(Double.POSITIVE_INFINITY, (double) Float.POSITIVE_INFINITY);
        testToFloatExact(Double.NaN, (double) Float.NaN);
    }

    private static void testToDoubleExact_long() {
        testCommonToExact_long("toDoubleExact", (Number v) -> toDoubleExact(v.longValue()));
        testToDoubleExact(Long.MIN_VALUE, -0x1.0p63);
        testToDoubleExact(Long.MIN_VALUE + 1L, null);
        testToDoubleExact(Integer.MIN_VALUE - 1L, -0x1p31 - 1);
        testToDoubleExact((long) Integer.MIN_VALUE, -0x1p31);
        testToDoubleExact(Integer.MIN_VALUE + 1L, -0x1p31 + 1);
        testToDoubleExact(Integer.MAX_VALUE - 1L, 0x1p31 - 2);
        testToDoubleExact((long) Integer.MAX_VALUE, 0x1p31 - 1);
        testToDoubleExact(Integer.MAX_VALUE + 1L, 0x1p31);
        testToDoubleExact(Long.MAX_VALUE - 1L, null);
        testToDoubleExact(Long.MAX_VALUE, null);
    }

    private static void testToUnsignedIntExact_long() {
        testToUnsignedIntExact(-1L, null);
        testToUnsignedIntExact(0L, 0L);
        testToUnsignedIntExact((long) Integer.MAX_VALUE, (long) Integer.MAX_VALUE);
        testToUnsignedIntExact((1L << Integer.SIZE) - 1, (1L << Integer.SIZE) - 1);
        testToUnsignedIntExact(1L << Integer.SIZE, null);
    }

    private static void testToUnsignedIntExact_double() {
        testToUnsignedIntExact(-1.0, null);
        testToUnsignedIntExact(0.0, 0L);
        testToUnsignedIntExact(0.5, null);
        testToUnsignedIntExact(0x1.fffffffcp30, (long) Integer.MAX_VALUE);
        testToUnsignedIntExact(0x1.fffffffep31 - 0.5, null);
        testToUnsignedIntExact(0x1.fffffffep31, (1L << Integer.SIZE) - 1);
        testToUnsignedIntExact(0x1.0p32, null);
    }

    private static void testToUnsignedShortExact_long() {
        testToUnsignedShortExact(-1L, null);
        testToUnsignedShortExact(0L, 0L);
        testToUnsignedShortExact((long) Short.MAX_VALUE, (long) Short.MAX_VALUE);
        testToUnsignedShortExact((1L << Short.SIZE) - 1, (1L << Short.SIZE) - 1);
        testToUnsignedShortExact(1L << Short.SIZE, null);
    }

    private static void testToUnsignedShortExact_double() {
        testToUnsignedShortExact(-1.0, null);
        testToUnsignedShortExact(0.0, 0L);
        testToUnsignedShortExact(0.5, null);
        testToUnsignedShortExact(0x1.fffcp14, (long) Short.MAX_VALUE);
        testToUnsignedShortExact(0x1.fffep15 - 0.5, null);
        testToUnsignedShortExact(0x1.fffep15, (1L << Short.SIZE) - 1);
        testToUnsignedShortExact(0x1.0p16, null);
    }

    private static void testToUnsignedByteExact_long() {
        testToUnsignedByteExact(-1L, null);
        testToUnsignedByteExact(0L, 0L);
        testToUnsignedByteExact((long) Byte.MAX_VALUE, (long) Byte.MAX_VALUE);
        testToUnsignedByteExact((1L << Byte.SIZE) - 1, (1L << Byte.SIZE) - 1);
        testToUnsignedByteExact(1L << Byte.SIZE, null);
    }

    private static void testToUnsignedByteExact_double() {
        testToUnsignedByteExact(-1.0, null);
        testToUnsignedByteExact(0.0, 0L);
        testToUnsignedByteExact(0.5, null);
        testToUnsignedByteExact(0x1.fcp6, (long) Byte.MAX_VALUE);
        testToUnsignedByteExact(0x1.fep7 - 0.5, null);
        testToUnsignedByteExact(0x1.fep7, (1L << Byte.SIZE) - 1);
        testToUnsignedByteExact(0x1.0p8, null);
    }



    /*
     * support methods
     */
    private static void testToByteExact(long arg, Long expected) {
        testToExact("toByteExact", v -> toByteExact(v.longValue()), arg, expected);
    }

    private static void testToUnsignedByteExact(long arg, Long expected) {
        testToExact("toUnsignedByteExact", v -> toUnsignedByteExact(v.longValue()), arg, expected);
    }

    private static void testToUnsignedByteExact(double arg, Long expected) {
        testToExact("toUnsignedByteExact", v -> toUnsignedByteExact(v.doubleValue()), arg, expected);
    }

    private static void testToByteExact(double arg, Long expected) {
        testToExact("toByteExact", v -> toByteExact(v.doubleValue()), arg, expected);
    }

    private static void testToShortExact(long arg, Long expected) {
        testToExact("toShortExact", v -> toShortExact(v.longValue()), arg, expected);
    }

    private static void testToUnsignedShortExact(long arg, Long expected) {
        testToExact("toUnsignedShortExact", v -> toUnsignedShortExact(v.longValue()), arg, expected);
    }

    private static void testToUnsignedShortExact(double arg, Long expected) {
        testToExact("toUnsignedShortExact", v -> toUnsignedShortExact(v.doubleValue()), arg, expected);
    }

    private static void testToShortExact(double arg, Long expected) {
        testToExact("toShortExact", v -> toShortExact(v.doubleValue()), arg, expected);
    }

    private static void testToIntExact(long arg, Long expected) {
        testToExact("toIntExact", v -> toIntExact(v.longValue()), arg, expected);
    }

    private static void testToUnsignedIntExact(long arg, Long expected) {
        testToExact("toUnsignedIntExact", v -> toUnsignedIntExact(v.longValue()), arg, expected);
    }

    private static void testToUnsignedIntExact(double arg, Long expected) {
        testToExact("toUnsignedIntExact", v -> toUnsignedIntExact(v.doubleValue()), arg, expected);
    }

    private static void testToIntExact(double arg, Long expected) {
        testToExact("toIntExact", v -> toIntExact(v.doubleValue()), arg, expected);
    }

    private static void testToLongExact(double arg, Long expected) {
        testToExact("toLongExact", v -> toLongExact(v.doubleValue()), arg, expected);
    }

    private static void testToDoubleExact(long arg, Double expected) {
        testToExact("toDoubleExact", v -> toDoubleExact(v.longValue()), arg, expected);
    }

    private static void testToFloatExact(long arg, Double expected) {
        testToExact("toFloatExact", v -> toFloatExact(v.longValue()), arg, expected);
    }

    private static void testToFloatExact(double arg, Double expected) {
        testToExact("toFloatExact", v -> toFloatExact(v.doubleValue()), arg, expected);
    }

    /*
     * expected is null if the underlying conversion is expected to throw.
     */
    private static void testToExact(String name, Function<? super Number, ? extends Number> toExact,
                                    Number arg, Number expected) {
        if (!((arg instanceof Long || arg instanceof Double) &&
                (expected == null || expected instanceof Long || expected instanceof Double))) {
            throw new AssertionError(String.format("arg class=%s, expected class=%s",
                    arg.getClass(), expected.getClass()));
        }
        try {
            var result = toExact.apply(arg);
            if (expected == null || !isNumericallyEqual(result, expected)) {
                failures += 1;
                failure(name, arg, result);
            }
        } catch (ArithmeticException swallowed) {
            if (expected != null) {
                errors += 1;
                error(name, arg);
            }
        }
    }

    private static void testCommonToExact_long(String name, Function<? super Number, ? extends Number> toExact) {
        testToExact(name, toExact, -100L, -100L);
        testToExact(name, toExact, -1L, -1L);
        testToExact(name, toExact, 0L, 0L);
        testToExact(name, toExact, 1L, 1L);
        testToExact(name, toExact, 100L, 100L);
    }

    private static void testCommonToExact_double(String name, Function<? super Number, ? extends Number> toExact) {
        testToExact(name, toExact, -100.5, null);
        testToExact(name, toExact, -100.0, -100L);
        testToExact(name, toExact, -1.5, null);
        testToExact(name, toExact, -1.0, -1L);
        testToExact(name, toExact, -0.0, 0L);
        testToExact(name, toExact, 0.0, 0L);
        testToExact(name, toExact, 1.0, 1L);
        testToExact(name, toExact, 1.5, null);
        testToExact(name, toExact, 100.0, 100L);
        testToExact(name, toExact, 100.5, null);

        testToExact(name, toExact, -Double.POSITIVE_INFINITY, null);
        testToExact(name, toExact, -Double.MAX_VALUE, null);
        testToExact(name, toExact, -Double.MIN_NORMAL, null);
        testToExact(name, toExact, -Double.MIN_VALUE, null);
        testToExact(name, toExact, Double.MIN_VALUE, null);
        testToExact(name, toExact, Double.MIN_NORMAL, null);
        testToExact(name, toExact, Double.MAX_VALUE, null);
        testToExact(name, toExact, Double.POSITIVE_INFINITY, null);

        testToExact(name, toExact, Double.NaN, null);
    }

    private static boolean isNumericallyEqual(Number result, Number expected) {
        return
            result instanceof Long
            ? expected instanceof Long
                ? result.longValue() == expected.longValue()
                : isNumericallyEqual(result.longValue(), expected.doubleValue())
            : expected instanceof Long
                ? isNumericallyEqual(expected.longValue(), result.doubleValue())
                : Double.compare(result.doubleValue(), expected.doubleValue()) == 0;
    }

    private static boolean isNumericallyEqual(long value, double result) {
        return new BigDecimal(value).compareTo(new BigDecimal(result)) == 0;
    }

    private static void failure(String meth, Number arg, Number result) {
        var as = arg instanceof Long ? "%d" : "%a";
        var rs = result instanceof Long ? "%d" : "%a";
        System.out.format("FAIL: " + meth + "(" + as + ") returns " + rs + "%n",
                arg.longValue(), result.longValue());
    }

    private static void error(String meth, Number arg) {
        var as = arg instanceof Long ? "%d" : "%a";
        System.out.format("ERROR: " + meth + "(" + as + ") unexpectedly throws%n",
                arg.longValue());
    }

}
