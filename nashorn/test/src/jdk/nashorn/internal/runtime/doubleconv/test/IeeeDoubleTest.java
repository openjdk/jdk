/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

// This file is available under and governed by the GNU General Public
// License version 2 only, as published by the Free Software Foundation.
// However, the following notice accompanied the original version of this
// file:
//
// Copyright 2006-2008 the V8 project authors. All rights reserved.

package jdk.nashorn.internal.runtime.doubleconv.test;

import org.testng.annotations.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * Ieee class tests
 */
@SuppressWarnings({"unchecked", "javadoc"})
public class IeeeDoubleTest {

    static final Method asDiyFp;
    static final Method asNormalizedDiyFp;
    static final Method doubleToLong;
    static final Method longToDouble;
    static final Method isDenormal;
    static final Method isSpecial;
    static final Method isInfinite;
    static final Method isNaN;
    static final Method value;
    static final Method sign;
    static final Method nextDouble;
    static final Method previousDouble;
    static final Method normalizedBoundaries;
    static final Method Infinity;
    static final Method NaN;
    static final Method f;
    static final Method e;
    static final Constructor<?> DiyFpCtor;

    static {
        try {
            final Class<?> IeeeDouble = Class.forName("jdk.nashorn.internal.runtime.doubleconv.IeeeDouble");
            final Class DiyFp = Class.forName("jdk.nashorn.internal.runtime.doubleconv.DiyFp");
            asDiyFp = method(IeeeDouble, "asDiyFp", long.class);
            asNormalizedDiyFp = method(IeeeDouble, "asNormalizedDiyFp", long.class);
            doubleToLong = method(IeeeDouble, "doubleToLong", double.class);
            longToDouble = method(IeeeDouble, "longToDouble", long.class);
            isDenormal = method(IeeeDouble, "isDenormal", long.class);
            isSpecial = method(IeeeDouble, "isSpecial", long.class);
            isInfinite = method(IeeeDouble, "isInfinite", long.class);
            isNaN = method(IeeeDouble, "isNaN", long.class);
            value = method(IeeeDouble, "value", long.class);
            sign = method(IeeeDouble, "sign", long.class);
            nextDouble = method(IeeeDouble, "nextDouble", long.class);
            previousDouble = method(IeeeDouble, "previousDouble", long.class);
            Infinity = method(IeeeDouble, "Infinity");
            NaN = method(IeeeDouble, "NaN");
            normalizedBoundaries = method(IeeeDouble, "normalizedBoundaries", long.class, DiyFp, DiyFp);
            DiyFpCtor = DiyFp.getDeclaredConstructor();
            DiyFpCtor.setAccessible(true);
            f = method(DiyFp, "f");
            e = method(DiyFp, "e");
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Method method(final Class<?> clazz, final String name, final Class<?>... params) throws NoSuchMethodException {
        final Method m = clazz.getDeclaredMethod(name, params);
        m.setAccessible(true);
        return m;
    }

    @Test
    public void testUint64Conversions() throws Exception {
        // Start by checking the byte-order.
        final long ordered = 0x0123456789ABCDEFL;
        assertEquals(3512700564088504e-318, value.invoke(null, ordered));

        final long min_double64 = 0x0000000000000001L;
        assertEquals(5e-324, value.invoke(null, min_double64));

        final long max_double64 = 0x7fefffffffffffffL;
        assertEquals(1.7976931348623157e308, value.invoke(null, max_double64));
    }


    @Test
    public void testDoubleAsDiyFp() throws Exception {
        final long ordered = 0x0123456789ABCDEFL;
        Object diy_fp = asDiyFp.invoke(null, ordered);
        assertEquals(0x12 - 0x3FF - 52, e.invoke(diy_fp));
        // The 52 mantissa bits, plus the implicit 1 in bit 52 as a UINT64.
        assertTrue(0x0013456789ABCDEFL == (long) f.invoke(diy_fp));

        final long min_double64 = 0x0000000000000001L;
        diy_fp = asDiyFp.invoke(null, min_double64);
        assertEquals(-0x3FF - 52 + 1, e.invoke(diy_fp));
        // This is a denormal; so no hidden bit.
        assertTrue(1L == (long) f.invoke(diy_fp));

        final long max_double64 = 0x7fefffffffffffffL;
        diy_fp = asDiyFp.invoke(null, max_double64);
        assertEquals(0x7FE - 0x3FF - 52, e.invoke(diy_fp));
        assertTrue(0x001fffffffffffffL == (long) f.invoke(diy_fp));
    }


    @Test
    public void testAsNormalizedDiyFp() throws Exception {
        final long ordered = 0x0123456789ABCDEFL;
        Object diy_fp = asNormalizedDiyFp.invoke(null, ordered);
        assertEquals(0x12 - 0x3FF - 52 - 11, (int) e.invoke(diy_fp));
        assertTrue((0x0013456789ABCDEFL << 11) == (long) f.invoke(diy_fp));

        final long min_double64 = 0x0000000000000001L;
        diy_fp = asNormalizedDiyFp.invoke(null, min_double64);
        assertEquals(-0x3FF - 52 + 1 - 63, e.invoke(diy_fp));
        // This is a denormal; so no hidden bit.
        assertTrue(0x8000000000000000L == (long) f.invoke(diy_fp));

        final long max_double64 = 0x7fefffffffffffffL;
        diy_fp = asNormalizedDiyFp.invoke(null, max_double64);
        assertEquals(0x7FE - 0x3FF - 52 - 11, e.invoke(diy_fp));
        assertTrue((0x001fffffffffffffL << 11) == (long) f.invoke(diy_fp));
    }


    @Test
    public void testIsDenormal() throws Exception {
        final long min_double64 = 0x0000000000000001L;
        assertTrue((boolean) isDenormal.invoke(null, min_double64));
        long bits = 0x000FFFFFFFFFFFFFL;
        assertTrue((boolean) isDenormal.invoke(null, bits));
        bits = 0x0010000000000000L;
        assertTrue(!(boolean) isDenormal.invoke(null, bits));
    }

    @Test
    public void testIsSpecial() throws Exception {
        assertTrue((boolean) isSpecial.invoke(null, doubleToLong.invoke(null, Infinity.invoke(null))));
        assertTrue((boolean) isSpecial.invoke(null, doubleToLong.invoke(null, -(double) Infinity.invoke(null))));
        assertTrue((boolean) isSpecial.invoke(null, doubleToLong.invoke(null, NaN.invoke(null))));
        final long bits = 0xFFF1234500000000L;
        assertTrue((boolean) isSpecial.invoke(null, bits));
        // Denormals are not special:
        assertTrue(!(boolean) isSpecial.invoke(null, doubleToLong.invoke(null, 5e-324)));
        assertTrue(!(boolean) isSpecial.invoke(null, doubleToLong.invoke(null, -5e-324)));
        // And some random numbers:
        assertTrue(!(boolean) isSpecial.invoke(null, doubleToLong.invoke(null, 0.0)));
        assertTrue(!(boolean) isSpecial.invoke(null, doubleToLong.invoke(null, -0.0)));
        assertTrue(!(boolean) isSpecial.invoke(null, doubleToLong.invoke(null, 1.0)));
        assertTrue(!(boolean) isSpecial.invoke(null, doubleToLong.invoke(null, -1.0)));
        assertTrue(!(boolean) isSpecial.invoke(null, doubleToLong.invoke(null, 1000000.0)));
        assertTrue(!(boolean) isSpecial.invoke(null, doubleToLong.invoke(null, -1000000.0)));
        assertTrue(!(boolean) isSpecial.invoke(null, doubleToLong.invoke(null, 1e23)));
        assertTrue(!(boolean) isSpecial.invoke(null, doubleToLong.invoke(null, -1e23)));
        assertTrue(!(boolean) isSpecial.invoke(null, doubleToLong.invoke(null, 1.7976931348623157e308)));
        assertTrue(!(boolean) isSpecial.invoke(null, doubleToLong.invoke(null, -1.7976931348623157e308)));
    }

        @Test
    public void testIsInfinite() throws Exception {
        assertTrue((boolean) isInfinite.invoke(null, doubleToLong.invoke(null, Infinity.invoke(null))));
        assertTrue((boolean) isInfinite.invoke(null, doubleToLong.invoke(null, -(double) Infinity.invoke(null))));
        assertTrue(!(boolean) isInfinite.invoke(null, doubleToLong.invoke(null, NaN.invoke(null))));
        assertTrue(!(boolean) isInfinite.invoke(null, doubleToLong.invoke(null, 0.0)));
        assertTrue(!(boolean) isInfinite.invoke(null, doubleToLong.invoke(null, -0.0)));
        assertTrue(!(boolean) isInfinite.invoke(null, doubleToLong.invoke(null, 1.0)));
        assertTrue(!(boolean) isInfinite.invoke(null, doubleToLong.invoke(null, -1.0)));
        final long min_double64 = 0x0000000000000001L;
        assertTrue(!(boolean) isInfinite.invoke(null, min_double64));
    }

        @Test
    public void testIsNan() throws Exception {
        assertTrue((boolean) isNaN.invoke(null, doubleToLong.invoke(null, NaN.invoke(null))));
        final long other_nan = 0xFFFFFFFF00000001L;
        assertTrue((boolean) isNaN.invoke(null, other_nan));
        assertTrue(!(boolean) isNaN.invoke(null, doubleToLong.invoke(null, Infinity.invoke(null))));
        assertTrue(!(boolean) isNaN.invoke(null, doubleToLong.invoke(null, -(double) Infinity.invoke(null))));
        assertTrue(!(boolean) isNaN.invoke(null, doubleToLong.invoke(null, 0.0)));
        assertTrue(!(boolean) isNaN.invoke(null, doubleToLong.invoke(null, -0.0)));
        assertTrue(!(boolean) isNaN.invoke(null, doubleToLong.invoke(null, 1.0)));
        assertTrue(!(boolean) isNaN.invoke(null, doubleToLong.invoke(null, -1.0)));
        final long min_double64 = 0x0000000000000001L;
        assertTrue(!(boolean) isNaN.invoke(null, min_double64));
    }

    @Test
    public void testSign() throws Exception {
        assertEquals(1, (int) sign.invoke(null, doubleToLong.invoke(null, 1.0)));
        assertEquals(1, (int) sign.invoke(null, doubleToLong.invoke(null, Infinity.invoke(null))));
        assertEquals(-1, (int) sign.invoke(null, doubleToLong.invoke(null, -(double) Infinity.invoke(null))));
        assertEquals(1, (int) sign.invoke(null, doubleToLong.invoke(null, 0.0)));
        assertEquals(-1, (int) sign.invoke(null, doubleToLong.invoke(null, -0.0)));
        final long min_double64 = 0x0000000000000001L;
        assertEquals(1, (int) sign.invoke(null, min_double64));
    }

    @Test
    public void testNormalizedBoundaries() throws Exception {
        Object boundary_plus = DiyFpCtor.newInstance();
        Object boundary_minus = DiyFpCtor.newInstance();
        Object diy_fp = asNormalizedDiyFp.invoke(null, doubleToLong.invoke(null, 1.5));
        normalizedBoundaries.invoke(null, doubleToLong.invoke(null, 1.5), boundary_minus, boundary_plus);
        assertEquals(e.invoke(diy_fp), e.invoke(boundary_minus));
        assertEquals(e.invoke(diy_fp), e.invoke(boundary_plus));
        // 1.5 does not have a significand of the form 2^p (for some p).
        // Therefore its boundaries are at the same distance.
        assertTrue((long) f.invoke(diy_fp) - (long) f.invoke(boundary_minus) == (long) f.invoke(boundary_plus) - (long) f.invoke(diy_fp));
        assertTrue((1 << 10) == (long) f.invoke(diy_fp) - (long) f.invoke(boundary_minus));

        diy_fp =asNormalizedDiyFp.invoke(null, doubleToLong.invoke(null, 1.0));
        normalizedBoundaries.invoke(null, doubleToLong.invoke(null, 1.0), boundary_minus, boundary_plus);
        assertEquals(e.invoke(diy_fp), e.invoke(boundary_minus));
        assertEquals(e.invoke(diy_fp), e.invoke(boundary_plus));
        // 1.0 does have a significand of the form 2^p (for some p).
        // Therefore its lower boundary is twice as close as the upper boundary.
        assertTrue((long) f.invoke(boundary_plus) - (long) f.invoke(diy_fp) > (long) f.invoke(diy_fp) - (long) f.invoke(boundary_minus));
        assertTrue((1L << 9) == (long) f.invoke(diy_fp) - (long) f.invoke(boundary_minus));
        assertTrue((1L << 10) == (long) f.invoke(boundary_plus) - (long) f.invoke(diy_fp));

        final long min_double64 = 0x0000000000000001L;
        diy_fp = asNormalizedDiyFp.invoke(null, min_double64);
        normalizedBoundaries.invoke(null, min_double64, boundary_minus, boundary_plus);
        assertEquals(e.invoke(diy_fp), e.invoke(boundary_minus));
        assertEquals(e.invoke(diy_fp), e.invoke(boundary_plus));
        // min-value does not have a significand of the form 2^p (for some p).
        // Therefore its boundaries are at the same distance.
        assertTrue((long) f.invoke(diy_fp) - (long) f.invoke(boundary_minus) == (long) f.invoke(boundary_plus) - (long) f.invoke(diy_fp));
        // Denormals have their boundaries much closer.
        assertTrue(1L << 62 == (long) f.invoke(diy_fp) - (long) f.invoke(boundary_minus));

        final long smallest_normal64 = 0x0010000000000000L;
        diy_fp = asNormalizedDiyFp.invoke(null, smallest_normal64);
        normalizedBoundaries.invoke(null, smallest_normal64, boundary_minus, boundary_plus);
        assertEquals(e.invoke(diy_fp), e.invoke(boundary_minus));
        assertEquals(e.invoke(diy_fp), e.invoke(boundary_plus));
        // Even though the significand is of the form 2^p (for some p), its boundaries
        // are at the same distance. (This is the only exception).
        assertTrue((long) f.invoke(diy_fp) - (long) f.invoke(boundary_minus) == (long) f.invoke(boundary_plus) - (long) f.invoke(diy_fp));
        assertTrue(1L << 10 == (long) f.invoke(diy_fp) - (long) f.invoke(boundary_minus));

        final long largest_denormal64 = 0x000FFFFFFFFFFFFFL;
        diy_fp = asNormalizedDiyFp.invoke(null, largest_denormal64);
        normalizedBoundaries.invoke(null, largest_denormal64, boundary_minus, boundary_plus);
        assertEquals(e.invoke(diy_fp),  e.invoke(boundary_minus));
        assertEquals(e.invoke(diy_fp),  e.invoke(boundary_plus));
        assertTrue((long) f.invoke(diy_fp) - (long) f.invoke(boundary_minus) == (long) f.invoke(boundary_plus) - (long) f.invoke(diy_fp));
        assertTrue(1L << 11 == (long) f.invoke(diy_fp) - (long) f.invoke(boundary_minus));

        final long max_double64 = 0x7fefffffffffffffL;
        diy_fp = asNormalizedDiyFp.invoke(null, max_double64);
        normalizedBoundaries.invoke(null, max_double64, boundary_minus, boundary_plus);
        assertEquals(e.invoke(diy_fp),  e.invoke(boundary_minus));
        assertEquals(e.invoke(diy_fp),  e.invoke(boundary_plus));
        // max-value does not have a significand of the form 2^p (for some p).
        // Therefore its boundaries are at the same distance.
        assertTrue((long) f.invoke(diy_fp) - (long) f.invoke(boundary_minus) == (long) f.invoke(boundary_plus) - (long) f.invoke(diy_fp));
        assertTrue(1L << 10 == (long) f.invoke(diy_fp) - (long) f.invoke(boundary_minus));
    }

    @Test
    public void testNextDouble() throws Exception {
        assertEquals(4e-324, (double) nextDouble.invoke(null, doubleToLong.invoke(null, 0.0)));
        assertEquals(0.0, (double) nextDouble.invoke(null, doubleToLong.invoke(null, -0.0)));
        assertEquals(-0.0, (double) nextDouble.invoke(null, doubleToLong.invoke(null, -4e-324)));
        assertTrue((int) sign.invoke(null, doubleToLong.invoke(null, nextDouble.invoke(null, doubleToLong.invoke(null, -0.0)))) > 0);
        assertTrue((int) sign.invoke(null, doubleToLong.invoke(null, nextDouble.invoke(null, doubleToLong.invoke(null, -4e-324)))) < 0);
        final long d0 = (long) doubleToLong.invoke(null, -4e-324);
        final long d1 = (long) doubleToLong.invoke(null, nextDouble.invoke(null, d0));
        final long d2 = (long) doubleToLong.invoke(null, nextDouble.invoke(null, d1));
        assertEquals(-0.0, value.invoke(null, d1));
        assertTrue((int) sign.invoke(null, d1) < 0);
        assertEquals(0.0, value.invoke(null, d2));
        assertTrue((int) sign.invoke(null, d2) > 0);
        assertEquals(4e-324, (double) nextDouble.invoke(null, d2));
        assertEquals(-1.7976931348623157e308, (double) nextDouble.invoke(null, doubleToLong.invoke(null, -(double) Infinity.invoke(null))));
        assertEquals(Infinity.invoke(null), (double) nextDouble.invoke(null, 0x7fefffffffffffffL));
    }

    @Test
    public void testPreviousDouble() throws Exception {
        assertEquals(0.0, (double) previousDouble.invoke(null, doubleToLong.invoke(null, 4e-324)));
        assertEquals(-0.0, (double) previousDouble.invoke(null, doubleToLong.invoke(null, 0.0)));
        assertTrue((int) sign.invoke(null, doubleToLong.invoke(null, previousDouble.invoke(null, doubleToLong.invoke(null, 0.0)))) < 0);
        assertEquals(-4e-324, previousDouble.invoke(null, doubleToLong.invoke(null, -0.0)));
        final long d0 = (long) doubleToLong.invoke(null, 4e-324);
        final long d1 = (long) doubleToLong.invoke(null, previousDouble.invoke(null, d0));
        final long d2 = (long) doubleToLong.invoke(null, previousDouble.invoke(null, d1));
        assertEquals(0.0, value.invoke(null, d1));
        assertTrue((int) sign.invoke(null, d1) > 0);
        assertEquals(-0.0, value.invoke(null, d2));
        assertTrue((int) sign.invoke(null, d2) < 0);
        assertEquals(-4e-324, (double) previousDouble.invoke(null, d2));
        assertEquals(1.7976931348623157e308, (double) previousDouble.invoke(null, doubleToLong.invoke(null, Infinity.invoke(null))));
        assertEquals(-(double) Infinity.invoke(null), (double) previousDouble.invoke(null, 0xffefffffffffffffL));
    }

}
