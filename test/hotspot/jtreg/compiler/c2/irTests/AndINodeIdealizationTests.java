/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8297384 8335444
 * @key randomness
 * @summary Test that Ideal transformations of AndINode* are being performed as expected.
 * @library /test/lib /
 * @run driver compiler.c2.irTests.AndINodeIdealizationTests
 */
public class AndINodeIdealizationTests {

    public static void main(String[] args) {
        TestFramework.run();
    }

    @Run(test = { "test1", "test2", "test3", "test4", "test5", "test6", "test7", "test8", "test9", "test10" })
    public void runMethod() {
        int a = RunInfo.getRandom().nextInt();
        int b = RunInfo.getRandom().nextInt();

        int min = Integer.MIN_VALUE;
        int max = Integer.MAX_VALUE;

        assertResult(0, 0);
        assertResult(10, 20);
        assertResult(10, -20);
        assertResult(-10, 20);
        assertResult(-10, -20);
        assertResult(a, b);
        assertResult(b, a);
        assertResult(min, min);
        assertResult(max, max);
    }

    @DontCompile
    public void assertResult(int a, int b) {
        Asserts.assertEQ((0 - a) & 1, test1(a));
        Asserts.assertEQ((~a) & (~b), test2(a, b));
        Asserts.assertEQ((a & 15) >= 0, test3(a, b));
        Asserts.assertEQ((a & 15) > 15, test4(a, b));
        Asserts.assertEQ((a & (b >>> 1)) >= 0, test5(a, b));
        Asserts.assertEQ((a & (b >>> 30)) > 3, test6(a, b));
        Asserts.assertEQ(((byte)a & -8) >= -128, test7(a, b));
        Asserts.assertEQ(((byte)a & -8) <= 127, test8(a, b));
        Asserts.assertEQ(((a & 255) & (char)b) > 255, test9(a, b));
        Asserts.assertEQ((((a & 1) - 3) & ((b & 2) - 10)) > -8, test10(a, b));
    }

    @Test
    @IR(failOn = { IRNode.SUB })
    @IR(counts = { IRNode.AND, "1" })
    // Checks (0 - x) & 1 => x & 1
    public int test1(int x) {
        return (0 - x) & 1;
    }

    @Test
    @IR(failOn = { IRNode.AND })
    @IR(counts = { IRNode.OR, "1",
                   IRNode.XOR, "1" })
    // Checks (~a) & (~b) => ~(a | b)
    public int test2(int a, int b) {
        return (~a) & (~b);
    }

    @Test
    @IR(failOn = { IRNode.AND })
    // Checks a & 15 => [0, 15]
    public boolean test3(int a, int b) {
        return (a & 15) >= 0;
    }

    @Test
    @IR(failOn = { IRNode.AND })
    // Checks a & 15 => [0, 15]
    public boolean test4(int a, int b) {
        return (a & 15) > 15;
    }

    @Test
    @IR(failOn = { IRNode.AND })
    // Checks a & [0, int_max] => [0, int_max]
    public boolean test5(int a, int b) {
        return (a & (b >>> 1)) >= 0;
    }

    @Test
    @IR(failOn = { IRNode.AND })
    // Checks a & [0, 3] => [0, 3]
    public boolean test6(int a, int b) {
        return (a & (b >>> 30)) > 3;
    }

    @Test
    @IR(failOn = { IRNode.AND })
    // Checks [-128, 127] & -8 => [-128, 127]
    public boolean test7(int a, int b) {
        return ((byte)a & -8) >= -128;
    }

    @Test
    @IR(failOn = { IRNode.AND })
    // Checks [-128, 127] & -8 => [-128, 127]
    public boolean test8(int a, int b) {
        return ((byte)a & -8) <= 127;
    }

    @Test
    @IR(failOn = { IRNode.AND })
    // Checks that [0, 255] & [0, 65535] => [0, 255]
    public boolean test9(int a, int b) {
        return ((a & 255) & (char)b) > 255;
    }

    @Test
    @IR(failOn = { IRNode.AND })
    // Checks that [-3, -2] & [-10, -8] => [-16, -8]
    public boolean test10(int a, int b) {
        return (((a & 1) - 3) & ((b & 2) - 10)) > -8;
    }
}
