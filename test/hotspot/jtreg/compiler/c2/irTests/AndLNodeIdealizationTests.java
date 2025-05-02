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
 * @bug 8322589 8335444
 * @key randomness
 * @summary Test that Ideal transformations of AndLNode* are being performed as expected.
 * @library /test/lib /
 * @run driver compiler.c2.irTests.AndLNodeIdealizationTests
 */
public class AndLNodeIdealizationTests {

    public static void main(String[] args) {
        TestFramework.run();
    }

    @Run(test = { "test1", "test2", "test3", "test4", "test5", "test6", "test7", "test8", "test9" })
    public void runMethod() {
        long a = RunInfo.getRandom().nextLong();
        long b = RunInfo.getRandom().nextLong();

        long min = Long.MIN_VALUE;
        long max = Long.MAX_VALUE;

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
    public void assertResult(long a, long b) {
        Asserts.assertEQ((~a) & (~b), test1(a, b));
        Asserts.assertEQ((a & 15) >= 0, test2(a, b));
        Asserts.assertEQ((a & 15) > 15, test3(a, b));
        Asserts.assertEQ((a & (b >>> 1)) >= 0, test4(a, b));
        Asserts.assertEQ((a & (b >>> 62)) > 3, test5(a, b));
        Asserts.assertEQ(((byte)a & -8L) >= -128, test6(a, b));
        Asserts.assertEQ(((byte)a & -8L) <= 127, test7(a, b));
        Asserts.assertEQ(((a & 255) & (char)b) > 255, test8(a, b));
        Asserts.assertEQ((((a & 1) - 3) & ((b & 2) - 10)) > -8, test9(a, b));
    }

    @Test
    @IR(failOn = { IRNode.AND })
    @IR(counts = { IRNode.OR, "1",
                   IRNode.XOR, "1" })
    // Checks (~a) & (~b) => ~(a | b)
    public long test1(long a, long b) {
        return (~a) & (~b);
    }

    @Test
    @IR(failOn = { IRNode.AND })
    // Checks a & 15 => [0, 15]
    public boolean test2(long a, long b) {
        return (a & 15) >= 0;
    }

    @Test
    @IR(failOn = { IRNode.AND })
    // Checks a & 15 => [0, 15]
    public boolean test3(long a, long b) {
        return (a & 15) > 15;
    }

    @Test
    @IR(failOn = { IRNode.AND })
    // Checks a & [0, long_max] => [0, long_max]
    public boolean test4(long a, long b) {
        return (a & (b >>> 1)) >= 0;
    }

    @Test
    @IR(failOn = { IRNode.AND })
    // Checks a & [0, 3] => [0, 3]
    public boolean test5(long a, long b) {
        return (a & (b >>> 62)) > 3;
    }

    @Test
    @IR(failOn = { IRNode.AND })
    // Checks [-128, 127] & -8 => [-128, 127]
    public boolean test6(long a, long b) {
        return ((byte)a & -8L) >= -128;
    }

    @Test
    @IR(failOn = { IRNode.AND })
    // Checks [-128, 127] & -8 => [-128, 127]
    public boolean test7(long a, long b) {
        return ((byte)a & -8L) <= 127;
    }

    @Test
    @IR(failOn = { IRNode.AND })
    // Checks that [0, 255] & [0, 65535] => [0, 255]
    public boolean test8(long a, long b) {
        return ((a & 255) & (char)b) > 255;
    }

    @Test
    @IR(failOn = { IRNode.AND })
    // Checks that [-3, -2] & [-10, -8] => [-16, -8]
    public boolean test9(long a, long b) {
        return (((a & 1) - 3) & ((b & 2) - 10)) > -8;
    }
}
