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
 * @summary Test that Value method of NulHiLNode is working as expected.
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

    @DontCompile
    public void assertResult(long a, long b) {
        Asserts.assertEquals(Math.multiplyHigh(C1, C2), givenTwoConstant());
        Asserts.assertEquals(Math.multiplyHigh(0, b), givenLeftZero(b));
        Asserts.assertEquals(Math.multiplyHigh(a, 0), givenRightZero(a));
        Asserts.assertEquals(Math.multiplyHigh(a, b), givenTwoLong(a, b));
    }

    /**
     * If two parameters are constant, folding to constant node
     */
    @Test
    @IR(failOn = {IRNode.MUL_HI_L})
    @IR(counts = {IRNode.CON_L, "1"})
    public long givenTwoConstant() {
        return Math.multiplyHigh(C1, C2);
    }

    /**
     * One of parameters is zero, the result always zero
     */
    @Test
    @IR(failOn = {IRNode.MUL_HI_L})
    @IR(counts = {IRNode.CON_L, "1"})
    public long givenLeftZero(long b) {
        return Math.multiplyHigh(0, b);
    }

    /**
     * One of parameters is zero, the result always zero
     */
    @Test
    @IR(failOn = {IRNode.MUL_HI_L})
    @IR(counts = {IRNode.CON_L, "1"})
    public long givenRightZero(long a) {
        return Math.multiplyHigh(a, 0);
    }

    @Test
    @IR(counts = {IRNode.MUL_HI_L, "1"})
    public long givenTwoLong(long a, long b) {
        return Math.multiplyHigh(a, b);
    }
}
