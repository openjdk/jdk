/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.core.test;

import org.graalvm.compiler.core.common.util.UnsignedLong;
import org.junit.Assert;
import org.junit.Test;

public class UnsignedLongTest {
    @Test
    public void testEquals() {
        UnsignedLong fortyTwo = new UnsignedLong(42);
        Assert.assertTrue(fortyTwo.equals(42));
        Assert.assertFalse(fortyTwo.equals(99));
        UnsignedLong longFortyTwo = new UnsignedLong(0x42_0000_8888L);
        Assert.assertTrue(longFortyTwo.equals(0x42_0000_8888L));
        Assert.assertFalse(longFortyTwo.equals(0x99_0000_8888L));
        UnsignedLong longUnsigned = new UnsignedLong(0x8000_7777_0000_8888L);
        Assert.assertTrue(longUnsigned.equals(0x8000_7777_0000_8888L));
        Assert.assertFalse(longUnsigned.equals(0xf000_7777_0000_8888L));
    }

    @Test
    public void testIsLessThan() {
        UnsignedLong fortyTwo = new UnsignedLong(42);
        Assert.assertTrue(fortyTwo.isLessThan(45));
        Assert.assertFalse(fortyTwo.isLessThan(42));
        Assert.assertFalse(fortyTwo.isLessThan(40));
        Assert.assertTrue(fortyTwo.isLessThan(0xffff_ffff_ffff_ffffL));
        UnsignedLong longUnsigned = new UnsignedLong(0x8000_7777_0000_8888L);
        Assert.assertTrue(longUnsigned.isLessThan(0xffff_ffff_ffff_ffffL));
        Assert.assertFalse(longUnsigned.isLessThan(42));
        Assert.assertFalse(longUnsigned.isLessThan(0x8000_0777_0000_8888L));
        Assert.assertFalse(longUnsigned.isLessThan(0x8000_7777_0000_8888L));
    }

    @Test
    public void testIsLessOrEqualTo() {
        UnsignedLong fortyTwo = new UnsignedLong(42);
        Assert.assertTrue(fortyTwo.isLessOrEqualTo(45));
        Assert.assertTrue(fortyTwo.isLessOrEqualTo(42));
        Assert.assertFalse(fortyTwo.isLessOrEqualTo(40));
        Assert.assertTrue(fortyTwo.isLessOrEqualTo(0xffff_ffff_ffff_ffffL));
        UnsignedLong longUnsigned = new UnsignedLong(0x8000_7777_0000_8888L);
        Assert.assertTrue(longUnsigned.isLessOrEqualTo(0xffff_ffff_ffff_ffffL));
        Assert.assertFalse(longUnsigned.isLessOrEqualTo(42));
        Assert.assertFalse(longUnsigned.isLessOrEqualTo(0x8000_0777_0000_8888L));
        Assert.assertTrue(longUnsigned.isLessOrEqualTo(0x8000_7777_0000_8888L));
    }

    @Test
    public void testTimes() {
        UnsignedLong fortyTwo = new UnsignedLong(42);
        Assert.assertEquals(42 * 42, fortyTwo.times(42).asLong());
        Assert.assertEquals(0xffff_ffff_ffff_fff0L, fortyTwo.times(0x618618618618618L).asLong());
    }

    @Test(expected = ArithmeticException.class)
    public void testTimesException() {
        UnsignedLong fortyTwo = new UnsignedLong(42);
        fortyTwo.times(0x618618618618619L);
    }

    @Test
    public void testMinus() {
        UnsignedLong fortyTwo = new UnsignedLong(42);
        Assert.assertEquals(0, fortyTwo.minus(42).asLong());
        Assert.assertEquals(40, fortyTwo.minus(2).asLong());
        UnsignedLong longUnsigned = new UnsignedLong(0xffff_ffff_ffff_fff0L);
        Assert.assertEquals(0, longUnsigned.minus(0xffff_ffff_ffff_fff0L).asLong());
    }

    @Test(expected = ArithmeticException.class)
    public void testMinusException() {
        UnsignedLong fortyTwo = new UnsignedLong(42);
        fortyTwo.minus(43);
    }

    @Test(expected = ArithmeticException.class)
    public void testMinusException2() {
        UnsignedLong longUnsigned = new UnsignedLong(0xffff_ffff_ffff_fff0L);
        longUnsigned.minus(0xffff_ffff_ffff_fff1L);
    }

    @Test
    public void testPlus() {
        UnsignedLong fortyTwo = new UnsignedLong(42);
        Assert.assertEquals(84, fortyTwo.plus(42).asLong());
        Assert.assertEquals(44, fortyTwo.plus(2).asLong());
        UnsignedLong longUnsigned = new UnsignedLong(0xffff_ffff_ffff_fff0L);
        Assert.assertEquals(0xffff_ffff_ffff_ffffL, longUnsigned.plus(0xf).asLong());
    }

    @Test(expected = ArithmeticException.class)
    public void testPlusException() {
        UnsignedLong fortyTwo = new UnsignedLong(42);
        fortyTwo.plus(0xffff_ffff_ffff_fff0L);
    }

    @Test(expected = ArithmeticException.class)
    public void testPlusException2() {
        UnsignedLong longUnsigned = new UnsignedLong(0xffff_ffff_ffff_fff0L);
        longUnsigned.plus(42);
    }

    @Test
    public void testWrappingTimes() {
        UnsignedLong fortyTwo = new UnsignedLong(42);
        Assert.assertEquals(0x1a, fortyTwo.wrappingTimes(0x618618618618619L).asLong());
    }

    @Test
    public void testWrappingPlus() {
        UnsignedLong fortyTwo = new UnsignedLong(42);
        Assert.assertEquals(0x1a, fortyTwo.wrappingPlus(0xffff_ffff_ffff_fff0L).asLong());
        UnsignedLong longUnsigned = new UnsignedLong(0xffff_ffff_ffff_fff0L);
        Assert.assertEquals(0x1a, longUnsigned.wrappingPlus(42).asLong());
    }
}
