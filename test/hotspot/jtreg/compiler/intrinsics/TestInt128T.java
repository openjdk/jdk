/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package compiler.intrinsics;

import compiler.lib.generators.Generator;
import compiler.lib.generators.Generators;
import compiler.lib.ir_framework.*;
import jdk.internal.misc.Int128T;
import jdk.test.lib.Asserts;

/*
 * @test
 * @bug 8383724
 * @summary Test the C2 implementation of int128_t operations
 * @key randomness
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @run driver ${test.main.class}
 */
public class TestInt128T {
    static final Generator<Long> LONGS = Generators.G.longs();

    public static void main(String[] args) {
        var framework = new TestFramework();
        framework.setDefaultWarmup(1);
        framework.addFlags("--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED");
        framework.start();
    }

    @Test
    @IR(counts = {IRNode.ADD_I128T, "1"}, phase = CompilePhase.BEFORE_MACRO_EXPANSION)
    @IR(failOn = IRNode.ADD_I128T, phase = CompilePhase.BEFORE_MATCHING)
    public long testAddLo(long lo1, long hi1, long lo2, long hi2) {
        return Int128T.addLo(lo1, hi1, lo2, hi2);
    }

    @Run(test = "testAddLo")
    public void runAddLo() {
        long lo1 = LONGS.next();
        long hi1 = LONGS.next();
        long lo2 = LONGS.next();
        long hi2 = LONGS.next();
        Asserts.assertEQ(lo1 + lo2, testAddLo(lo1, hi1, lo2, hi2));
    }

    @Test
    @IR(counts = {IRNode.ADD_I128T, "1"}, phase = CompilePhase.BEFORE_MACRO_EXPANSION)
    @IR(failOn = IRNode.ADD_I128T, phase = CompilePhase.BEFORE_MATCHING)
    public long testAddHi(long lo1, long hi1, long lo2, long hi2) {
        return Int128T.addHi(lo1, hi1, lo2, hi2);
    }

    @Run(test = "testAddHi")
    public void runAddHi() {
        long lo1 = LONGS.next();
        long hi1 = LONGS.next();
        long lo2 = LONGS.next();
        long hi2 = LONGS.next();
        Asserts.assertEQ(0L, testAddHi(0, 0, 0, 0));
        Asserts.assertEQ(1L, testAddHi(1, 0, -1, 0));
        Asserts.assertEQ(0L, testAddHi(1, 0, -1, -1));
        Asserts.assertEQ(hi1 + hi2, testAddHi(0, hi1, 0, hi2));
        Asserts.assertEQ(hi1 + hi2, testAddHi(Long.MAX_VALUE, hi1, Long.MAX_VALUE, hi2));
        Asserts.assertEQ(hi1 + hi2 + 1, testAddHi(Long.MIN_VALUE, hi1, Long.MIN_VALUE, hi2));
        Asserts.assertEQ(hi1 + hi2 + 1, testAddHi(-1, hi1, 1, hi2));
        Asserts.assertEQ(hi1 + hi2 + 1, testAddHi(-1, hi1, -1, hi2));

        if (Long.compareUnsigned(lo1 + lo2, lo1) >= 0) {
            Asserts.assertEQ(hi1 + hi2, testAddHi(lo1, hi1, lo2, hi2));
        }
        if (Long.compareUnsigned(lo1 + lo2, lo2) >= 0) {
            Asserts.assertEQ(hi1 + hi2, testAddHi(lo1, hi1, lo2, hi2));
        }
        if (Long.compareUnsigned(lo1 + lo2, lo1) < 0) {
            Asserts.assertEQ(hi1 + hi2 + 1, testAddHi(lo1, hi1, lo2, hi2));
        }
    }

    @Test
    @IR(counts = {IRNode.SUB_I128T, "1"}, phase = CompilePhase.BEFORE_MACRO_EXPANSION)
    @IR(failOn = IRNode.SUB_I128T, phase = CompilePhase.BEFORE_MATCHING)
    public long testSubLo(long lo1, long hi1, long lo2, long hi2) {
        return Int128T.subLo(lo1, hi1, lo2, hi2);
    }

    @Run(test = "testSubLo")
    public void runSubLo() {
        long lo1 = LONGS.next();
        long hi1 = LONGS.next();
        long lo2 = LONGS.next();
        long hi2 = LONGS.next();
        Asserts.assertEQ(lo1 - lo2, testSubLo(lo1, hi1, lo2, hi2));
    }

    @Test
    @IR(counts = {IRNode.SUB_I128T, "1"}, phase = CompilePhase.BEFORE_MACRO_EXPANSION)
    @IR(failOn = IRNode.SUB_I128T, phase = CompilePhase.BEFORE_MATCHING)
    public long testSubHi(long lo1, long hi1, long lo2, long hi2) {
        return Int128T.subHi(lo1, hi1, lo2, hi2);
    }

    @Run(test = "testSubHi")
    public void runSubHi() {
        long lo1 = LONGS.next();
        long hi1 = LONGS.next();
        long lo2 = LONGS.next();
        long hi2 = LONGS.next();
        Asserts.assertEQ(0L, testSubHi(0, 0, 0, 0));
        Asserts.assertEQ(0L, testSubHi(0, 1, 1, 0));
        Asserts.assertEQ(-1L, testSubHi(-1, -1, 0, 0));
        Asserts.assertEQ(-1L, testSubHi(0, 0, -1, 0));
        Asserts.assertEQ(hi1, testSubHi(0, hi1, 0, 0));
        Asserts.assertEQ(hi1 - 1, testSubHi(0, hi1, 1, 0));
        Asserts.assertEQ(hi1 - hi2, testSubHi(0, hi1, 0, hi2));
        Asserts.assertEQ(hi1 - hi2, testSubHi(-1, hi1, -1, hi2));
        Asserts.assertEQ(hi1 - hi2, testSubHi(-1, hi1, 0, hi2));
        Asserts.assertEQ(hi1 - hi2 - 1, testSubHi(0, hi1, 1, hi2));
        Asserts.assertEQ(hi1 - hi2 - 1, testSubHi(0, hi1, -1, hi2));
        Asserts.assertEQ(hi1 - hi2 - 1, testSubHi(-2, hi1, -1, hi2));
        if (Long.compareUnsigned(lo1, lo2) >= 0) {
            Asserts.assertEQ(hi1 - hi2, testSubHi(lo1, hi1, lo2, hi2));
        } else {
            Asserts.assertEQ(hi1 - hi2 - 1, testSubHi(lo1, hi1, lo2, hi2));
        }
    }
}
