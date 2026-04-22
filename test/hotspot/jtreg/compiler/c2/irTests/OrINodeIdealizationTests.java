/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8322077 8353359
 * @summary Test that Ideal transformations of OrINode* are being performed as expected.
 * @library /test/lib /
 * @run driver compiler.c2.irTests.OrINodeIdealizationTests
 */
public class OrINodeIdealizationTests {

    public static void main(String[] args) {
        TestFramework.run();
    }

    @Run(test = { "test1", "test2", "test3" })
    public void runMethod() {
        int a = RunInfo.getRandom().nextInt();
        int b = RunInfo.getRandom().nextInt();

        int min = Integer.MIN_VALUE;
        int max = Integer.MAX_VALUE;

        assertResult(0, 0);
        assertResult(a, b);
        assertResult(min, min);
        assertResult(max, max);
    }

    @DontCompile
    public void assertResult(int a, int b) {
        Asserts.assertEQ((~a) | (~b), test1(a, b));
        Asserts.assertEQ((a | 3) | 6, test2(a));
        Asserts.assertEQ((a | 3) | a, test3(a));
    }

    // Checks (~a) | (~b) => ~(a & b)
    @Test
    @IR(failOn = { IRNode.OR })
    @IR(counts = { IRNode.AND, "1",
                   IRNode.XOR, "1" })
    public int test1(int a, int b) {
        return (~a) | (~b);
    }

    // Checks (a | 3) | 6 => a | (3 | 6) => a | 7
    @Test
    @IR(counts = { IRNode.OR, "1"})
    public int test2(int a) {
        return (a | 3) | 6;
    }

    // Checks (a | 3) | a => (a | a) | 3 => a | 3
    @Test
    @IR(counts = { IRNode.OR, "1"})
    public int test3(int a) {
        return (a | 3) | a;
    }
}
