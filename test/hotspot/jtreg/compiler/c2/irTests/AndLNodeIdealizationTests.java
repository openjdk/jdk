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
 * @bug 8322589
 * @summary Test that Ideal transformations of AndLNode* are being performed as expected.
 * @library /test/lib /
 * @run driver compiler.c2.irTests.AndLNodeIdealizationTests
 */
public class AndLNodeIdealizationTests {

    public static void main(String[] args) {
        TestFramework.run();
    }

    @Run(test = { "test1" })
    public void runMethod() {
        long a = RunInfo.getRandom().nextLong();
        long b = RunInfo.getRandom().nextLong();

        long min = Long.MIN_VALUE;
        long max = Long.MAX_VALUE;

        assertResult(0, 0);
        assertResult(a, b);
        assertResult(min, min);
        assertResult(max, max);
    }

    @DontCompile
    public void assertResult(long a, long b) {
        Asserts.assertEQ((~a) & (~b), test1(a, b));
    }

    @Test
    @IR(failOn = { IRNode.AND })
    @IR(counts = { IRNode.OR, "1",
                   IRNode.XOR, "1" })
    // Checks (~a) & (~b) => ~(a | b)
    public long test1(long a, long b) {
        return (~a) & (~b);
    }
}
