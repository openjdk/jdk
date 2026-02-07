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
 * @summary Test that Value method of UMulHiLNode is working as expected.
 * @key randomness
 * @library /test/lib /
 * @run driver compiler.c2.gvn.UMulHiLNodeValueTests
 */
public class UMulHiLNodeValueTests {
    private static final RestrictableGenerator<Long> LONGS = Generators.G.longs();
    private static final long C1 = LONGS.next(), C2 = LONGS.next();

    public static void main(String[] args) {
        TestFramework.run();
    }

    @Run(test = {
            "givenTwoConstant", "givenLeftZero",
            "givenRightZero", "givenTwoLong"
    })
    public void run() {
        long aLong = LONGS.next();
        long bLong = LONGS.next();
        long minLong = Long.MIN_VALUE;
        long maxLong = Long.MAX_VALUE;

        assertResult(0, 0);
        assertResult(aLong, bLong);
        assertResult(minLong, minLong);
        assertResult(maxLong, maxLong);
    }

    @Run(test = "givenUint32RangeFolded")
    public void runWithUint32Inputs() {
        assertUint32Result(LONGS.next(), LONGS.next());
    }

    @DontCompile
    public void assertResult(long a, long b) {
        Asserts.assertEquals(Math.unsignedMultiplyHigh(C1, C2), givenTwoConstant());
        Asserts.assertEquals(Math.unsignedMultiplyHigh(0, b), givenLeftZero(b));
        Asserts.assertEquals(Math.unsignedMultiplyHigh(a, 0), givenRightZero(a));
        Asserts.assertEquals(Math.unsignedMultiplyHigh(a, b), givenTwoLong(a, b));
    }

    @DontCompile
    public void assertUint32Result(long a, long b) {
        long x = toUint32(a);
        long y = toUint32(b);
        Asserts.assertEquals(Math.unsignedMultiplyHigh(x, y), givenUint32RangeFolded(a, b));
    }

    /**
     * If two parameters are constant, folding to constant node
     */
    @Test
    @IR(failOn = {IRNode.UMUL_HI_L})
    @IR(counts = {IRNode.CON_L, "1"})
    public long givenTwoConstant() {
        return Math.unsignedMultiplyHigh(C1, C2);
    }

    /**
     * One of parameters is zero, the result always zero
     */
    @Test
    @IR(failOn = {IRNode.UMUL_HI_L})
    @IR(counts = {IRNode.CON_L, "1"})
    public long givenLeftZero(long b) {
        return Math.unsignedMultiplyHigh(0, b);
    }

    /**
     * One of parameters is zero, the result always zero
     */
    @Test
    @IR(failOn = {IRNode.UMUL_HI_L})
    @IR(counts = {IRNode.CON_L, "1"})
    public long givenRightZero(long a) {
        return Math.unsignedMultiplyHigh(a, 0);
    }

    @Test
    @IR(counts = {IRNode.UMUL_HI_L, "1"})
    public long givenTwoLong(long a, long b) {
        return Math.unsignedMultiplyHigh(a, b);
    }

    /**
     * Both operands are in [0, 0xffffffff] so high word is always zero.
     */
    @Test
    @IR(failOn = {IRNode.UMUL_HI_L})
    @IR(counts = {IRNode.CON_L, "1"})
    public long givenUint32RangeFolded(long a, long b) {
        long x = toUint32(a);
        long y = toUint32(b);
        return Math.unsignedMultiplyHigh(x, y);
    }

    @ForceInline
    private static long toUint32(long v) {
        return v & 0xffffffffL;
    }
}
