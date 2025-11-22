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

package compiler.c2.gvn;

import compiler.lib.generators.Generators;
import compiler.lib.generators.RestrictableGenerator;
import compiler.lib.ir_framework.*;
import jdk.test.lib.Asserts;

/*
 * @test
 * @bug 8370196
 * @summary Test that Value method of MulHiLNode is working as expected.
 * @key randomness
 * @library /test/lib /
 * @run driver compiler.c2.gvn.MulHiLNodeValueTests
 */
public class MulHiLNodeValueTests {
    private static final RestrictableGenerator<Long> LONGS = Generators.G.longs();
    private static final long C1 = LONGS.next(), C2 = LONGS.next();

    public static void main(String[] args) {
        TestFramework.run();
    }

    @Run(test = {
            "givenConstants", "givenLeftZero",
            "givenRightZero", "givenTwoLong"
    })
    public void runWithLongInputs() {
        long aLong = LONGS.next();
        long bLong = LONGS.next();
        long minLong = Long.MIN_VALUE;
        long maxLong = Long.MAX_VALUE;

        assertLongInputsResult(0, 0);
        assertLongInputsResult(aLong, bLong);
        assertLongInputsResult(minLong, minLong);
        assertLongInputsResult(maxLong, maxLong);
    }

    @DontCompile
    public void assertLongInputsResult(long a, long b) {
        Asserts.assertEquals(Math.multiplyHigh(C1, C2), givenConstants());
        Asserts.assertEquals(Math.multiplyHigh(0, b), givenLeftZero(b));
        Asserts.assertEquals(Math.multiplyHigh(a, 0), givenRightZero(a));
        Asserts.assertEquals(Math.multiplyHigh(a, b), givenTwoLong(a, b));
    }

    @Run(test = {
            "givenNoOverflowPositiveInt", "givenNoOverflowNegativeInt"
    })
    public void runWithIntInputs() {
        assertIntInputsResult(LONGS.next(), LONGS.next());
    }

    @DontCompile
    public void assertIntInputsResult(long a, long b) {
        long x = nonZeroPositiveInt(a);
        long y = (a * b > 0) ? nonZeroPositiveInt(b) : negativeInt(b);
        long expected = Math.multiplyHigh(x, y);
        Asserts.assertEquals(expected, (a * b > 0) ? givenNoOverflowPositiveInt(x, y) : givenNoOverflowNegativeInt(x, y));
    }

    /**
     * If two parameters are constant, folding to constant node
     */
    @Test
    @IR(failOn = {IRNode.MUL_HI_L}, counts = {IRNode.CON_L, "1"})
    public long givenConstants() {
        return Math.multiplyHigh(C1, C2);
    }

    /**
     * One of parameters is zero, the result always zero
     */
    @Test
    @IR(failOn = {IRNode.MUL_HI_L}, counts = {IRNode.CON_L, "1"})
    public long givenLeftZero(long b) {
        return Math.multiplyHigh(0, b);
    }

    /**
     * One of parameters is zero, the result always zero
     */
    @Test
    @IR(failOn = {IRNode.MUL_HI_L}, counts = {IRNode.CON_L, "1"})
    public long givenRightZero(long a) {
        return Math.multiplyHigh(a, 0);
    }

    @Test
    @IR(counts = {IRNode.MUL_HI_L, "1"})
    public long givenTwoLong(long a, long b) {
        return Math.multiplyHigh(a, b);
    }

    /**
     * Product is always non-negative and fits into 64 bits -> high word is zero
     */
    @Test
    @IR(failOn = {IRNode.MUL_HI_L}, counts = {IRNode.CON_L, "1"})
    public long givenNoOverflowPositiveInt(long a, long b) {
        long x = nonZeroPositiveInt(a);
        long y = nonZeroPositiveInt(b);
        return Math.multiplyHigh(x, y);
    }

    /**
     * Product is always negative (non-negative * negative) and fits into 64 bits -> high word is -1
     */
    @Test
    @IR(failOn = {IRNode.MUL_HI_L}, counts = {IRNode.CON_L, "1"})
    public long givenNoOverflowNegativeInt(long a, long b) {
        long x = nonZeroPositiveInt(a);
        long y = negativeInt(b);
        return Math.multiplyHigh(x, y);
    }

    @ForceInline
    private static long nonZeroPositiveInt(long v) {
        return (v & 0x7fffffffL) + 1L;
    }

    @ForceInline
    private static long negativeInt(long v) {
        return -((v & 0x7fffffffL) + 1L);
    }

}
