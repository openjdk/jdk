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
 * @bug 8281518
 * @summary Test that transformation from "(x|y)-(x^y)" to "x&y" works
 *          as intended.
 * @library /test/lib /
 * @run driver compiler.c2.irTests.TestIRSubIdeal_XOrY_Minus_XXorY_
 */
public class TestIRSubIdeal_XOrY_Minus_XXorY_ {

    public static void main(String[] args) {
        TestFramework.run();
    }

    @Test
    @IR(failOn = {IRNode.OR_I, IRNode.XOR_I, IRNode.SUB_I})
    @IR(counts = {IRNode.AND_I, "1"})
    public int testInt(int x, int y) {
        return (x | y) - (x ^ y); // transformed to x & y
    }

    @Run(test = "testInt")
    public void checkTestInt(RunInfo info) {
        assertC2Compiled(info);
        Asserts.assertEquals(0x5050_A0A0, testInt(0x5A5A_A5A5, 0x5555_AAAA));
        Asserts.assertEquals(0x0000_0000, testInt(0x0000_0000, 0x0000_0000));
        Asserts.assertEquals(0xFFFF_FFFF, testInt(0xFFFF_FFFF, 0xFFFF_FFFF));
        Asserts.assertEquals(0x0000_0000, testInt(0xFFFF_FFFF, 0x0000_0000));
        Asserts.assertEquals(0x0000_0000, testInt(0x0000_0000, 0xFFFF_FFFF));
        Asserts.assertEquals(0x8000_0000, testInt(0x8000_0000, 0xFFFF_0000));
        Asserts.assertEquals(0x7FFF_FFFF, testInt(0x7FFF_FFFF, 0x7FFF_FFFF));
    }

    @Test
    @IR(failOn = {IRNode.OR_L, IRNode.XOR_L, IRNode.SUB_L})
    @IR(counts = {IRNode.AND_L, "1"})
    public long testLong(long x, long y) {
        return (x | y) - (x ^ y); // transformed to x & y
    }

    @Run(test = "testLong")
    public void checkTestLong(RunInfo info) {
        assertC2Compiled(info);
        Asserts.assertEquals(0x5050_A0A0_0000_50A0L, testLong(0x5A5A_A5A5_5AA5_55AAL, 0x5555_AAAA_A55A_5AA5L));
        Asserts.assertEquals(0x0000_0000_0000_0000L, testLong(0x0000_0000_0000_0000L, 0x0000_0000_0000_0000L));
        Asserts.assertEquals(0xFFFF_FFFF_FFFF_FFFFL, testLong(0xFFFF_FFFF_FFFF_FFFFL, 0xFFFF_FFFF_FFFF_FFFFL));
        Asserts.assertEquals(0x0000_0000_0000_0000L, testLong(0xFFFF_FFFF_FFFF_FFFFL, 0x0000_0000_0000_0000L));
        Asserts.assertEquals(0x0000_0000_0000_0000L, testLong(0x0000_0000_0000_0000L, 0xFFFF_FFFF_FFFF_FFFFL));
        Asserts.assertEquals(0x8000_0000_0000_0000L, testLong(0x8000_0000_0000_0000L, 0xFFFF_0000_0000_0000L));
        Asserts.assertEquals(0x7FFF_FFFF_FFFF_FFFFL, testLong(0x7FFF_FFFF_FFFF_FFFFL, 0x7FFF_FFFF_FFFF_FFFFL));
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
