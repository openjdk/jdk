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

package compiler.c2.irTests;

import jdk.test.lib.Asserts;
import compiler.lib.ir_framework.*;

/*
 * @test
 * @bug 8278114
 * @summary Test that transformation from (x + x) >> c to x >> (c + 1) works as intended.
 * @library /test/lib /
 * @run driver compiler.c2.irTests.TestIRLShiftIdeal_XPlusX_LShiftC
 */
public class TestIRLShiftIdeal_XPlusX_LShiftC {

    private static final int[] INT_IN = {
        -10, -2, -1, 0, 1, 2, 10,
        0x8000_0000, 0x7FFF_FFFF, 0x5678_1234,
    };

    private static final int[][] INT_OUT = {
        // Do testInt0(x) for each x in INT_IN
        {
            -160, -32, -16, 0, 16, 32, 160,
            0x0000_0000, 0xFFFF_FFF0, 0x6781_2340,
        },

        // Do testInt1(x) for each x in INT_IN
        {
            -10485760, -2097152, -1048576, 0, 1048576, 2097152, 10485760,
            0x0000_0000, 0xFFF0_0000, 0x2340_0000,
        },
    };

    private static final long[] LONG_IN = {
        -10L, -2L, -1L, 0L, 1L, 2L, 10L,
        0x8000_0000_0000_0000L, 0x7FFF_FFFF_FFFF_FFFFL, 0x5678_1234_4321_8765L,
    };

    private static final long[][] LONG_OUT = {
        // Do testLong0(x) for each x in LONG_IN
        {
            -160L, -32L, -16L, 0L, 16L, 32L, 160L,
            0x0000_0000_0000_0000L, 0xFFFF_FFFF_FFFF_FFF0L, 0x6781_2344_3218_7650L,
        },

        // Do testLong1(x) for each x in LONG_IN
        {
            -687194767360L, -137438953472L, -68719476736L, 0L, 68719476736L, 137438953472L, 687194767360L,
            0x0000_0000_0000_0000L, 0xFFFF_FFF0_0000_0000L, 0x3218_7650_0000_0000L,
        },
    };

    public static void main(String[] args) {
        TestFramework.run();
    }

    @Test
    @IR(failOn = {IRNode.ADD_I, IRNode.MUL_I})
    @IR(counts = {IRNode.LSHIFT_I, "1"})
    public int testInt0(int x) {
        return (x + x) << 3; // transformed to x << 4
    }

    @Run(test = "testInt0")
    public void checkTestInt0(RunInfo info) {
        assertC2Compiled(info);
        for (int i = 0; i < INT_IN.length; i++) {
            Asserts.assertEquals(INT_OUT[0][i], testInt0(INT_IN[i]));
        }
    }

    @Test
    @IR(failOn = {IRNode.MUL_I})
    @IR(counts = {IRNode.LSHIFT_I, "1",
                  IRNode.ADD_I, "1"})
    public int testInt1(int x) {
        return (x + x) << 19; // no transformation because 19 is
                              // greater than 16 (see implementation
                              // in LShiftINode::Ideal)
    }

    @Run(test = "testInt1")
    public void checkTestInt1(RunInfo info) {
        assertC2Compiled(info);
        for (int i = 0; i < INT_IN.length; i++) {
            Asserts.assertEquals(INT_OUT[1][i], testInt1(INT_IN[i]));
        }
    }

    @Test
    @IR(failOn = {IRNode.ADD_L, IRNode.MUL_L})
    @IR(counts = {IRNode.LSHIFT_L, "1"})
    public long testLong0(long x) {
        return (x + x) << 3; // transformed to x << 4
    }

    @Run(test = "testLong0")
    public void checkTestLong0(RunInfo info) {
        assertC2Compiled(info);
        for (int i = 0; i < LONG_IN.length; i++) {
            Asserts.assertEquals(LONG_OUT[0][i], testLong0(LONG_IN[i]));
        }
    }

    @Test
    @IR(failOn = {IRNode.ADD_L, IRNode.MUL_L})
    @IR(counts = {IRNode.LSHIFT_L, "1"})
    public long testLong1(long x) {
        return (x + x) << 35; // transformed to x << 36
    }

    @Run(test = "testLong1")
    public void checkTestLong1(RunInfo info) {
        assertC2Compiled(info);
        for (int i = 0; i < LONG_IN.length; i++) {
            Asserts.assertEquals(LONG_OUT[1][i], testLong1(LONG_IN[i]));
        }
    }

    private void assertC2Compiled(RunInfo info) {
        // Test VM allows C2 to work
        Asserts.assertTrue(info.isC2CompilationEnabled());
        if (!info.isWarmUp()) {
            // C2 compilation happens
            Asserts.assertTrue(info.isTestC2Compiled());
        }
    }
}
