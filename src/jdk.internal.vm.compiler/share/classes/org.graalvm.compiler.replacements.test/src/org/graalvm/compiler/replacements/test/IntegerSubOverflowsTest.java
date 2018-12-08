/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.replacements.test;

import org.junit.Assert;
import org.junit.Test;

import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.StampFactory;

public class IntegerSubOverflowsTest {

    @Test
    public void testOverflowCheck() {
        long a = Integer.MIN_VALUE;
        long b = -1;
        Assert.assertFalse(IntegerStamp.subtractionOverflows(a, b, 32));
    }

    @Test
    public void testOverflowCheck01() {
        long a = Integer.MAX_VALUE;
        long b = Integer.MAX_VALUE;
        Assert.assertFalse(IntegerStamp.subtractionOverflows(a, b, 32));
    }

    @Test
    public void testOverflowCheck02() {
        long a = Integer.MIN_VALUE;
        long b = Integer.MIN_VALUE;
        Assert.assertFalse(IntegerStamp.subtractionOverflows(a, b, 32));
    }

    @Test
    public void testOverflowCheck03() {
        long a = Integer.MIN_VALUE;
        long b = 1;
        Assert.assertTrue(IntegerStamp.subtractionOverflows(a, b, 32));
    }

    @Test
    public void testOverflowCheck04() {
        long a = Integer.MAX_VALUE;
        long b = 1;
        Assert.assertFalse(IntegerStamp.subtractionOverflows(a, b, 32));
    }

    @Test
    public void testOverflowCheck05() {
        long a = Integer.MAX_VALUE;
        long b = Integer.MIN_VALUE;
        Assert.assertTrue(IntegerStamp.subtractionOverflows(a, b, 32));
    }

    @Test
    public void testOverflowCheck06() {
        long a = Integer.MAX_VALUE;
        long b = Integer.MAX_VALUE;
        Assert.assertFalse(IntegerStamp.subtractionOverflows(a, b, 64));
    }

    @Test
    public void testOverflowCheck07() {
        long a = Long.MAX_VALUE;
        long b = 2;
        Assert.assertFalse(IntegerStamp.subtractionOverflows(a, b, 64));
    }

    @Test
    public void testOverflowCheck08() {
        long a = Long.MAX_VALUE;
        long b = Long.MAX_VALUE;
        Assert.assertFalse(IntegerStamp.subtractionOverflows(a, b, 64));
    }

    @Test
    public void testOverflowCheck09() {
        long a = -Long.MAX_VALUE;
        long b = Long.MAX_VALUE;
        Assert.assertTrue(IntegerStamp.subtractionOverflows(a, b, 64));
    }

    @Test
    public void testOverflowCheck10() {
        long a = -Long.MAX_VALUE;
        long b = -Long.MAX_VALUE;
        Assert.assertFalse(IntegerStamp.subtractionOverflows(a, b, 64));
    }

    @Test
    public void testOverflowCheck11() {
        long a = Long.MAX_VALUE;
        long b = -Long.MAX_VALUE;
        Assert.assertTrue(IntegerStamp.subtractionOverflows(a, b, 64));
    }

    @Test
    public void testOverflowCheckStamp() {
        IntegerStamp s1 = StampFactory.forInteger(32, Integer.MIN_VALUE, Integer.MIN_VALUE);
        IntegerStamp s2 = StampFactory.forInteger(32, -1, -1);
        Assert.assertFalse(IntegerStamp.subtractionCanOverflow(s1, s2));
    }

    @Test
    public void testOverflowCheckStamp01() {
        IntegerStamp s1 = StampFactory.forInteger(32, Integer.MAX_VALUE, Integer.MAX_VALUE);
        IntegerStamp s2 = StampFactory.forInteger(32, Integer.MAX_VALUE, Integer.MAX_VALUE);
        Assert.assertFalse(IntegerStamp.subtractionCanOverflow(s1, s2));
    }

    @Test
    public void testOverflowCheckStamp02() {
        IntegerStamp s1 = StampFactory.forInteger(32, Integer.MIN_VALUE, Integer.MIN_VALUE);
        IntegerStamp s2 = StampFactory.forInteger(32, Integer.MIN_VALUE, Integer.MIN_VALUE);
        Assert.assertFalse(IntegerStamp.subtractionCanOverflow(s1, s2));
    }

    @Test
    public void testOverflowCheckStamp03() {
        IntegerStamp s1 = StampFactory.forInteger(32, Integer.MIN_VALUE, Integer.MIN_VALUE);
        IntegerStamp s2 = StampFactory.forInteger(32, 1, 1);
        Assert.assertTrue(IntegerStamp.subtractionCanOverflow(s1, s2));
    }

    @Test
    public void testOverflowCheckStamp04() {
        IntegerStamp s1 = StampFactory.forInteger(8, Byte.MIN_VALUE, Byte.MIN_VALUE);
        IntegerStamp s2 = StampFactory.forInteger(8, -1, -1);
        Assert.assertFalse(IntegerStamp.subtractionCanOverflow(s1, s2));
    }

    @Test
    public void testOverflowCheckStamp05() {
        IntegerStamp s1 = StampFactory.forInteger(8, Byte.MAX_VALUE, Byte.MAX_VALUE);
        IntegerStamp s2 = StampFactory.forInteger(8, Byte.MAX_VALUE, Byte.MAX_VALUE);
        Assert.assertFalse(IntegerStamp.subtractionCanOverflow(s1, s2));
    }

    @Test
    public void testOverflowCheckStamp06() {
        IntegerStamp s1 = StampFactory.forInteger(8, Byte.MIN_VALUE, Byte.MIN_VALUE);
        IntegerStamp s2 = StampFactory.forInteger(8, Byte.MIN_VALUE, Byte.MIN_VALUE);
        Assert.assertFalse(IntegerStamp.subtractionCanOverflow(s1, s2));
    }

    @Test
    public void testOverflowCheckStamp07() {
        IntegerStamp s1 = StampFactory.forInteger(8, Byte.MIN_VALUE, Byte.MIN_VALUE);
        IntegerStamp s2 = StampFactory.forInteger(8, 1, 1);
        Assert.assertTrue(IntegerStamp.subtractionCanOverflow(s1, s2));
    }

    @Test
    public void testOverflowCheckStamp08() {
        IntegerStamp s1 = StampFactory.forInteger(64, Long.MIN_VALUE, Long.MIN_VALUE);
        IntegerStamp s2 = StampFactory.forInteger(64, -1, -1);
        Assert.assertFalse(IntegerStamp.subtractionCanOverflow(s1, s2));
    }

    @Test
    public void testOverflowCheckStamp09() {
        IntegerStamp s1 = StampFactory.forInteger(64, Long.MAX_VALUE, Long.MAX_VALUE);
        IntegerStamp s2 = StampFactory.forInteger(64, Long.MAX_VALUE, Long.MAX_VALUE);
        Assert.assertFalse(IntegerStamp.subtractionCanOverflow(s1, s2));
    }

    @Test
    public void testOverflowCheckStamp10() {
        IntegerStamp s1 = StampFactory.forInteger(64, Long.MIN_VALUE, Long.MIN_VALUE);
        IntegerStamp s2 = StampFactory.forInteger(64, Long.MIN_VALUE, Long.MIN_VALUE);
        Assert.assertFalse(IntegerStamp.subtractionCanOverflow(s1, s2));
    }

    @Test
    public void testOverflowCheckStamp11() {
        IntegerStamp s1 = StampFactory.forInteger(64, Long.MIN_VALUE, Long.MIN_VALUE);
        IntegerStamp s2 = StampFactory.forInteger(64, 1, 1);
        Assert.assertTrue(IntegerStamp.subtractionCanOverflow(s1, s2));
    }

    @Test
    public void testOverflowBIgStamps01() {
        IntegerStamp s1 = StampFactory.forInteger(64, Long.MIN_VALUE, Long.MAX_VALUE);
        IntegerStamp s2 = StampFactory.forInteger(64, Long.MIN_VALUE, Long.MAX_VALUE);
        Assert.assertTrue(IntegerStamp.subtractionCanOverflow(s1, s2));
    }

    @Test
    public void testOverflowBIgStamps02() {
        IntegerStamp s1 = StampFactory.forInteger(64, Long.MIN_VALUE, Long.MAX_VALUE);
        IntegerStamp s2 = StampFactory.forInteger(64, Long.MIN_VALUE, Long.MIN_VALUE);
        Assert.assertTrue(IntegerStamp.subtractionCanOverflow(s1, s2));
    }
}
