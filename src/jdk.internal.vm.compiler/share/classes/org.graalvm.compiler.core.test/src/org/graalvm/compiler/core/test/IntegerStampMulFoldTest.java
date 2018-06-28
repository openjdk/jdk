/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.Collection;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.StampFactory;

@RunWith(Suite.class)
@SuiteClasses({IntegerStampMulFoldTest.OverflowTest.class, IntegerStampMulFoldTest.FoldTest.class})
public class IntegerStampMulFoldTest extends GraalCompilerTest {

    public static class OverflowTest {
        @Test
        public void testOverflowCheck() {
            // a.u * b.u overflows
            long a = Integer.MIN_VALUE;
            long b = -1;
            Assert.assertTrue(IntegerStamp.multiplicationOverflows(a, b, 32));
        }

        @Test
        public void testOverflowCheck01() {
            // a.u * b.u overflows
            long a = Integer.MAX_VALUE;
            long b = Integer.MAX_VALUE;
            Assert.assertTrue(IntegerStamp.multiplicationOverflows(a, b, 32));
        }

        @Test
        public void testOverflowCheck02() {
            // a.u * b.u overflows
            long a = Integer.MIN_VALUE;
            long b = Integer.MIN_VALUE;
            Assert.assertTrue(IntegerStamp.multiplicationOverflows(a, b, 32));
        }

        @Test
        public void testOverflowCheck03() {
            // a.u * b.u overflows
            long a = Integer.MIN_VALUE;
            long b = 1;
            Assert.assertFalse(IntegerStamp.multiplicationOverflows(a, b, 32));
        }

        @Test
        public void testOverflowCheck04() {
            // a.u * b.u overflows
            long a = Integer.MAX_VALUE;
            long b = 1;
            Assert.assertFalse(IntegerStamp.multiplicationOverflows(a, b, 32));
        }

        @Test
        public void testOverflowCheck05() {
            // a.u * b.u overflows
            long a = Integer.MAX_VALUE;
            long b = Integer.MIN_VALUE;
            Assert.assertTrue(IntegerStamp.multiplicationOverflows(a, b, 32));
        }

        @Test
        public void testOverflowCheck06() {
            // 31bits*31bits = 62bits + 1 carry is max 63 bits, we can never need 64 bits
            long a = Integer.MAX_VALUE;
            long b = Integer.MAX_VALUE;
            Assert.assertTrue("31bit*31bit overflows 62", IntegerStamp.multiplicationOverflows(a, b, 62));
            Assert.assertFalse("31bit*31bit does not overflow 63", IntegerStamp.multiplicationOverflows(a, b, 63));
            Assert.assertFalse("31bit*31bit does not overflow 64", IntegerStamp.multiplicationOverflows(a, b, 64));
        }

        @Test
        public void testOverflowCheck07() {
            long a = Long.MAX_VALUE;
            long b = 2;
            Assert.assertTrue(IntegerStamp.multiplicationOverflows(a, b, 64));
        }

        @Test
        public void testOverflowCheck08() {
            long a = Long.MAX_VALUE;
            long b = Long.MAX_VALUE;
            Assert.assertTrue(IntegerStamp.multiplicationOverflows(a, b, 64));
        }

        @Test
        public void testOverflowCheck09() {
            long a = -Long.MAX_VALUE;
            long b = Long.MAX_VALUE;
            Assert.assertTrue(IntegerStamp.multiplicationOverflows(a, b, 64));
        }

        @Test
        public void testOverflowCheck10() {
            long a = -Long.MAX_VALUE;
            long b = -Long.MAX_VALUE;
            Assert.assertTrue(IntegerStamp.multiplicationOverflows(a, b, 64));
        }

        @Test
        public void testOverflowCheck11() {
            long a = Long.MAX_VALUE;
            long b = -Long.MAX_VALUE;
            Assert.assertTrue(IntegerStamp.multiplicationOverflows(a, b, 64));
        }
    }

    @RunWith(Parameterized.class)
    public static class FoldTest {
        @Parameter(0) public long lowerBound1;
        @Parameter(1) public long upperBound1;
        @Parameter(2) public long lowerBound2;
        @Parameter(3) public long upperBound2;
        @Parameter(4) public int bits;

        @Test
        public void computeStamp() {
            IntegerStamp a = StampFactory.forInteger(bits, lowerBound1, upperBound1);
            IntegerStamp b = StampFactory.forInteger(bits, lowerBound2, upperBound2);
            IntegerStamp result = foldMul(a, b);
            for (long l1 = lowerBound1; l1 <= upperBound1; l1++) {
                for (long l2 = lowerBound2; l2 <= upperBound2; l2++) {
                    long res = 0;
                    if (bits == 8) {
                        res = (byte) (l1 * l2);
                    } else if (bits == 16) {
                        res = (short) (l1 * l2);
                    } else if (bits == 32) {
                        res = (int) (l1 * l2);
                    } else if (bits == 64) {
                        res = l1 * l2;
                    }
                    Assert.assertTrue(result.contains(res));
                    if (l2 == Long.MAX_VALUE) {
                        // do not want to overflow the check loop
                        break;
                    }
                }
                if (l1 == Long.MAX_VALUE) {
                    // do not want to overflow the check loop
                    break;
                }
            }
        }

        @Parameters(name = "a[{0} - {1}] b[{2} - {3}] bits=32")
        public static Collection<Object[]> data() {
            ArrayList<Object[]> tests = new ArrayList<>();

            // zero related
            addTest(tests, -2, 2, 3, 3, 8);
            addTest(tests, 0, 0, 1, 1, 8);
            addTest(tests, 1, 1, 0, 0, 8);
            addTest(tests, -1, 1, 0, 1, 8);
            addTest(tests, -1, 1, 1, 1, 8);
            addTest(tests, -1, 1, -1, 1, 8);

            addTest(tests, -2, 2, 3, 3, 16);
            addTest(tests, 0, 0, 1, 1, 16);
            addTest(tests, 1, 1, 0, 0, 16);
            addTest(tests, -1, 1, 0, 1, 16);
            addTest(tests, -1, 1, 1, 1, 16);
            addTest(tests, -1, 1, -1, 1, 16);

            addTest(tests, -2, 2, 3, 3, 32);
            addTest(tests, 0, 0, 1, 1, 32);
            addTest(tests, 1, 1, 0, 0, 32);
            addTest(tests, -1, 1, 0, 1, 32);
            addTest(tests, -1, 1, 1, 1, 32);
            addTest(tests, -1, 1, -1, 1, 32);

            addTest(tests, 0, 0, 1, 1, 64);
            addTest(tests, 1, 1, 0, 0, 64);
            addTest(tests, -1, 1, 0, 1, 64);
            addTest(tests, -1, 1, 1, 1, 64);
            addTest(tests, -1, 1, -1, 1, 64);

            // bounds
            addTest(tests, Byte.MIN_VALUE, Byte.MIN_VALUE + 1, Byte.MAX_VALUE - 1, Byte.MAX_VALUE, 8);
            addTest(tests, Byte.MIN_VALUE, Byte.MIN_VALUE + 1, -1, -1, 8);
            addTest(tests, Integer.MIN_VALUE, Integer.MIN_VALUE + 0xFF, Integer.MAX_VALUE - 0xFF,
                            Integer.MAX_VALUE, 32);
            addTest(tests, Integer.MIN_VALUE, Integer.MIN_VALUE + 0xFFF, -1, -1, 32);
            addTest(tests, Integer.MIN_VALUE, Integer.MIN_VALUE + 0xFF, Integer.MAX_VALUE - 0xFF,
                            Integer.MAX_VALUE, 64);
            addTest(tests, Integer.MIN_VALUE, Integer.MIN_VALUE + 0xFFF, -1, -1, 64);
            addTest(tests, Long.MIN_VALUE, Long.MIN_VALUE + 0xFFF, -1, -1, 64);

            // constants
            addTest(tests, 2, 2, 2, 2, 32);
            addTest(tests, 1, 1, 2, 2, 32);
            addTest(tests, 2, 2, 4, 4, 32);
            addTest(tests, 3, 3, 3, 3, 32);
            addTest(tests, -4, -4, 3, 3, 32);
            addTest(tests, -4, -4, -3, -3, 32);
            addTest(tests, 4, 4, -3, -3, 32);

            addTest(tests, 2, 2, 2, 2, 64);
            addTest(tests, 1, 1, 2, 2, 64);
            addTest(tests, 3, 3, 3, 3, 64);

            addTest(tests, Long.MAX_VALUE, Long.MAX_VALUE, 1, 1, 64);
            addTest(tests, Long.MAX_VALUE, Long.MAX_VALUE, -1, -1, 64);
            addTest(tests, Long.MIN_VALUE, Long.MIN_VALUE, -1, -1, 64);
            addTest(tests, Long.MIN_VALUE, Long.MIN_VALUE, 1, 1, 64);

            return tests;
        }

        private static void addTest(ArrayList<Object[]> tests, long lowerBound1, long upperBound1, long lowerBound2, long upperBound2, int bits) {
            tests.add(new Object[]{lowerBound1, upperBound1, lowerBound2, upperBound2, bits});
        }

    }

    private static IntegerStamp foldMul(IntegerStamp a, IntegerStamp b) {
        return (IntegerStamp) IntegerStamp.OPS.getMul().foldStamp(a, b);
    }

}
