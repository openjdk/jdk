/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @bug 8355992
 * @summary Tests for StrictMath.*PowExact and .*unsignedMultiplyExact
 * @run junit IntegralPowTest
 */

import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static java.lang.StrictMath.*;
import static java.math.BigInteger.ONE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class IntegralPowTest {

    private static final long MASK_32 = (1L << Integer.SIZE) - 1;  // 2^32 - 1
    private static final BigInteger MASK_64 = ONE.shiftLeft(Long.SIZE).subtract(ONE);  // 2^64 - 1
    private static final double INT_F = Integer.SIZE * Math.log(2);
    private static final double LONG_F = Long.SIZE * Math.log(2);
    private static final int INT_XMAX = 0x1_0000;
    private static final int LONG_XMAX = 0x10_0000;

    private static BigInteger unsignedBigInteger(int x) {
        return BigInteger.valueOf(x & MASK_32);
    }

    private static BigInteger unsignedBigInteger(long x) {
        return BigInteger.valueOf(x).and(MASK_64);
    }

    private static int slowPowExact(int x, int n) {
        BigInteger pow = BigInteger.valueOf(x).pow(n);
        return pow.intValueExact();
    }

    private static int slowUnsignedPowExact(int x, int n) {
        BigInteger pow = unsignedBigInteger(x).pow(n);
        if (pow.bitLength() > Integer.SIZE) {
            throw new ArithmeticException();
        }
        return pow.intValue();
    }

    private static long slowPowExact(long x, int n) {
        BigInteger pow = BigInteger.valueOf(x).pow(n);
        return pow.longValueExact();
    }

    private static long slowUnsignedPowExact(long x, int n) {
        BigInteger pow = unsignedBigInteger(x).pow(n);
        if (pow.bitLength() > Long.SIZE) {
            throw new ArithmeticException();
        }
        return pow.longValue();
    }

    @Test
    void testIntUnsignedMultiplyExact() {
        assertEquals(0, unsignedMultiplyExact(0, 0));
        assertEquals(0, unsignedMultiplyExact(1, 0));
        assertEquals(0, unsignedMultiplyExact(-1, 0));
        assertEquals(1, unsignedMultiplyExact(1, 1));
        assertEquals(-1, unsignedMultiplyExact(1, -1));
        assertEquals(1 << 31, unsignedMultiplyExact(1 << 15, 1 << 16));
        assertEquals(1 << 31, unsignedMultiplyExact(1 << 10, 1 << 21));
        /* 2^32 - 1 = (2^16 + 1) (2^16 - 1) */
        assertEquals(-1, unsignedMultiplyExact((1 << 16) + 1, (1 << 16) - 1));

        assertThrows(ArithmeticException.class, () -> unsignedMultiplyExact(-1, -1));
    }

    @Test
    void testLongIntUnsignedMultiplyExact() {
        assertEquals(0L, unsignedMultiplyExact(0L, 0));
        assertEquals(0L, unsignedMultiplyExact(1L, 0));
        assertEquals(0L, unsignedMultiplyExact(-1L, 0));
        assertEquals(1L, unsignedMultiplyExact(1L, 1));
        assertEquals(-3 & MASK_32, unsignedMultiplyExact(1L, -3));
        assertEquals(1L << 50, unsignedMultiplyExact(1L << 25, 1 << 25));
        /* 2^64 - 1 = (2^32 + 1) (2^32 - 1) */
        assertEquals(-1L, unsignedMultiplyExact((1L << 32) + 1, -1));

        assertThrows(ArithmeticException.class, () -> unsignedMultiplyExact(-1L, -1));
    }

    @Test
    void testLongUnsignedMultiplyExact() {
        assertEquals(0L, unsignedMultiplyExact(0L, 0L));
        assertEquals(0L, unsignedMultiplyExact(1L, 0L));
        assertEquals(0L, unsignedMultiplyExact(-1L, 0L));
        assertEquals(1L, unsignedMultiplyExact(1L, 1L));
        assertEquals(-1L, unsignedMultiplyExact(1L, -1L));
        assertEquals(1L << 63, unsignedMultiplyExact(1L << 31, 1L << 32));
        assertEquals(1L << 63, unsignedMultiplyExact(1L << 25, 1L << 38));
        /* 2^64 - 1 = (2^32 + 1) (2^32 - 1) */
        assertEquals(-1L, unsignedMultiplyExact((1L << 32) + 1, (1L << 32) - 1));

        assertThrows(ArithmeticException.class, () -> unsignedMultiplyExact(-1L, -1L));
    }

    @Test
    void testIntPowExact() {
        assertEquals(1, powExact(0, 0));
        assertEquals(0, powExact(0, 1_000_000));
        assertEquals(1, powExact(1, 0));
        assertEquals(1, powExact(1, 1_000_000));
        assertEquals(1, powExact(-1, 0));
        assertEquals(1, powExact(-1, 1_000_000));
        assertEquals(-1, powExact(-1, 1_000_001));

        assertEquals(1 << -2, powExact(2, Integer.SIZE - 2));
        assertEquals(-1 << -1, powExact(-2, Integer.SIZE - 1));
        assertEquals(1_000_000_000, powExact(10, 9));
        assertEquals(-1_000_000_000, powExact(-10, 9));

        assertThrows(ArithmeticException.class, () -> powExact(0, -1_000_000));
        assertThrows(ArithmeticException.class, () -> powExact(1, -1_000_000));
        assertThrows(ArithmeticException.class, () -> powExact(2, Integer.SIZE - 1));
        assertThrows(ArithmeticException.class, () -> powExact(10, 10));
        assertThrows(ArithmeticException.class, () -> powExact(-10, 10));
    }

    @Test
    void testUnsignedIntPowExact() {
        assertEquals(1, unsignedPowExact(0, 0));
        assertEquals(0, unsignedPowExact(0, 1_000_000));
        assertEquals(1, unsignedPowExact(1, 0));
        assertEquals(1, unsignedPowExact(1, 1_000_000));
        assertEquals(1, unsignedPowExact(-1, 0));
        assertEquals(-1, unsignedPowExact(-1, 1));

        assertEquals(1 << -1, unsignedPowExact(2, Integer.SIZE - 1));
        assertEquals(1_000_000_000, unsignedPowExact(10, 9));

        assertThrows(ArithmeticException.class, () -> unsignedPowExact(0, -1_000_000));
        assertThrows(ArithmeticException.class, () -> unsignedPowExact(1, -1_000_000));
        assertThrows(ArithmeticException.class, () -> unsignedPowExact(-1, 2));
        assertThrows(ArithmeticException.class, () -> unsignedPowExact(2, Integer.SIZE));
        assertThrows(ArithmeticException.class, () -> unsignedPowExact(10, 10));
    }

    @Test
    void testLongPowExact() {
        assertEquals(1L, powExact(0L, 0));
        assertEquals(0L, powExact(0L, 1_000_000));
        assertEquals(1L, powExact(1L, 0));
        assertEquals(1L, powExact(1L, 1_000_000));
        assertEquals(1L, powExact(-1L, 0));
        assertEquals(1L, powExact(-1L, 1_000_000));
        assertEquals(-1L, powExact(-1L, 1_000_001));

        assertEquals(1L << -2, powExact(2L, Long.SIZE - 2));
        assertEquals(-1L << -1, powExact(-2L, Long.SIZE - 1));
        assertEquals(1_000_000_000_000_000_000L, powExact(10L, 18));
        assertEquals(-100_000_000_000_000_000L, powExact(-10L, 17));
        assertEquals(1_000_000_000_000_000_000L, powExact(-10L, 18));

        assertThrows(ArithmeticException.class, () -> powExact(0L, -1_000_000));
        assertThrows(ArithmeticException.class, () -> powExact(1L, -1_000_000));
        assertThrows(ArithmeticException.class, () -> powExact(2L, Long.SIZE - 1));
        assertThrows(ArithmeticException.class, () -> powExact(10L, 19));
        assertThrows(ArithmeticException.class, () -> powExact(-10L, 19));
    }

    @Test
    void testUnsignedLongPowExact() {
        assertEquals(1L, unsignedPowExact(0L, 0));
        assertEquals(0L, unsignedPowExact(0L, 1_000_000));
        assertEquals(1L, unsignedPowExact(1L, 0));
        assertEquals(1L, unsignedPowExact(1L, 1_000_000));
        assertEquals(1L, unsignedPowExact(-1L, 0));
        assertEquals(-1L, unsignedPowExact(-1L, 1));

        assertEquals(1L << -1, unsignedPowExact(2L, Long.SIZE - 1));
        assertEquals(10 * 1_000_000_000_000_000_000L, unsignedPowExact(10L, 19));

        assertThrows(ArithmeticException.class, () -> unsignedPowExact(0L, -1_000_000));
        assertThrows(ArithmeticException.class, () -> unsignedPowExact(1L, -1_000_000));
        assertThrows(ArithmeticException.class, () -> unsignedPowExact(-1L, 2));
        assertThrows(ArithmeticException.class, () -> unsignedPowExact(2L, Long.SIZE));
        assertThrows(ArithmeticException.class, () -> unsignedPowExact(10L, 20));
    }

    /*
     * Assumes that x^n != 0
     * Returns x^n, or 0 on overflow
     */
    private static int expected(int x, int n) {
        try {
            return powExact(x, n);
        } catch (ArithmeticException ignore) {
            return 0;
        }
    }

    /*
     * Assumes that x^n != 0
     * Returns x^n, or 0 on overflow
     */
    private static int actual(int x, int n) {
        try {
            return slowPowExact(x, n);
        } catch (ArithmeticException ignore) {
            return 0;
        }
    }

    /*
     * Assumes that x^n != 0
     * Returns x^n, or 0 on overflow
     */
    private static int expectedUnsigned(int x, int n) {
        try {
            return unsignedPowExact(x, n);
        } catch (ArithmeticException ignore) {
            return 0;
        }
    }

    /*
     * Assumes that x^n != 0
     * Returns x^n, or 0 on overflow
     */
    private static int actualUnsigned(int x, int n) {
        try {
            return slowUnsignedPowExact(x, n);
        } catch (ArithmeticException ignore) {
            return 0;
        }
    }

    /*
     * Assumes that x^n != 0
     * Returns x^n, or 0 on overflow
     */
    private static long expected(long x, int n) {
        try {
            return powExact(x, n);
        } catch (ArithmeticException ignore) {
            return 0;
        }
    }

    /*
     * Assumes that x^n != 0
     * Returns x^n, or 0 on overflow
     */
    private static long actual(long x, int n) {
        try {
            return slowPowExact(x, n);
        } catch (ArithmeticException ignore) {
            return 0;
        }
    }

    /*
     * Assumes that x^n != 0
     * Returns x^n, or 0 on overflow
     */
    private static long expectedUnsigned(long x, int n) {
        try {
            return unsignedPowExact(x, n);
        } catch (ArithmeticException ignore) {
            return 0;
        }
    }

    /*
     * Assumes that x^n != 0
     * Returns x^n, or 0 on overflow
     */
    private static long actualUnsigned(long x, int n) {
        try {
            return slowUnsignedPowExact(x, n);
        } catch (ArithmeticException ignore) {
            return 0;
        }
    }

    /* signed int */

    @Test
    void testPositiveIntPowExact() {
        for (int x = 2; x <= INT_XMAX; x += 1) {
            /* An estimate for the max n such that x^n does not overflow. */
            int nmax = (int) ceil(INT_F / log(x));
            for (int n = 0; n <= nmax; ++n) {
                assertEquals(actual(x, n), expected(x, n));
            }
            int x0 = x;
            assertThrows(ArithmeticException.class, () -> powExact(x0, nmax + 1));
        }
    }

    @Test
    void testNegativeIntPowExact() {
        for (int x = 2; x <= INT_XMAX; x += 1) {
            /* An estimate for the max n such that (-x)^n does not overflow. */
            int nmax = (int) ceil(INT_F / log(x));
            for (int n = 0; n <= nmax; ++n) {
                assertEquals(actual(-x, n), expected(-x, n));
            }
            int x0 = x;
            assertThrows(ArithmeticException.class, () -> powExact(-x0, nmax + 1));
        }
    }

    /* unsigned int */

    @Test
    void testSmallUnsignedIntPowExact() {
        for (int x = 2; x <= INT_XMAX; x += 1) {
            /* An estimate for the max n such that x^n does not overflow. */
            int nmax = (int) ceil(INT_F / log(x));
            for (int n = 0; n <= nmax; ++n) {
                assertEquals(actualUnsigned(x, n), expectedUnsigned(x, n));
            }
            int x0 = x;
            assertThrows(ArithmeticException.class, () -> unsignedPowExact(x0, nmax + 1));
        }
    }

    /* signed long */

    @Test
    void testPositiveLongPowExact() {
        for (long x = 2; x <= LONG_XMAX; x += 5) {
            /* An estimate for the max n such that x^n does not overflow. */
            int nmax = (int) ceil(LONG_F / log(x));
            for (int n = 0; n <= nmax; ++n) {
                assertEquals(actual(x, n), expected(x, n));
            }
            long x0 = x;
            assertThrows(ArithmeticException.class, () -> powExact(x0, nmax + 1));
        }
    }

    @Test
    void testNegativeLongPowExact() {
        for (long x = 2; x <= LONG_XMAX; x += 5) {
            /* An estimate for the max n such that (-x)^n does not overflow. */
            int nmax = (int) ceil(LONG_F / log(x));
            for (int n = 0; n <= nmax; ++n) {
                assertEquals(actual(-x, n), expected(-x, n));
            }
            long x0 = x;
            assertThrows(ArithmeticException.class, () -> powExact(-x0, nmax + 1));
        }
    }

    /* unsigned long */

    @Test
    void testSmallUnsignedLongPowExact() {
        for (long x = 2; x <= LONG_XMAX; x += 5) {
            /* An estimate for the max n such that x^n does not overflow. */
            int nmax = (int) ceil(LONG_F / log(x));
            for (int n = 0; n <= nmax; ++n) {
                assertEquals(actualUnsigned(x, n), expectedUnsigned(x, n));
            }
            long x0 = x;
            assertThrows(ArithmeticException.class, () -> unsignedPowExact(x0, nmax + 1));
        }
    }

}
