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
 * @bug 8297384
 * @summary Test that Ideal transformations of AndINode* are being performed as expected.
 * @library /test/lib /
 * @run driver compiler.c2.irTests.AndINodeIdealizationTests
 */
public class AndINodeIdealizationTests {

    public static void main(String[] args) {
        TestFramework.run();
    }

    @Run(test = { "test1", "test2" })
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
        Asserts.assertEQ((0 - a) & 1, test1(a));
        Asserts.assertEQ((~a) & (~b), test2(a, b));
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
}
