/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8320330
 * @summary Test that RShiftINode optimizations are being performed as expected.
 * @library /test/lib /
 * @run driver compiler.c2.irTests.RShiftINodeIdealizationTests
 */
public class RShiftINodeIdealizationTests {
    public static void main(String[] args) {
        TestFramework.run();
    }

    @Run(test = { "test1", "test2", "test3", "test4" })
    public void runMethod() {
        int a = RunInfo.getRandom().nextInt();
        int b = RunInfo.getRandom().nextInt();
        int c = RunInfo.getRandom().nextInt();
        int d = RunInfo.getRandom().nextInt();

        int min = Integer.MIN_VALUE;
        int max = Integer.MAX_VALUE;

        assertResult(a, 0);
        assertResult(a, b);
        assertResult(b, a);
        assertResult(c, d);
        assertResult(d, c);
        assertResult(min, max);
        assertResult(max, min);
        assertResult(min, min);
        assertResult(max, max);
    }

    @DontCompile
    public void assertResult(int x, int y) {
        Asserts.assertEQ((x >> y) >= 0 ? 0 : 1, test1(x, y));
        Asserts.assertEQ(((x & 127) >> y) >= 0 ? 0 : 1, test2(x, y));
        Asserts.assertEQ(((-(x & 127) - 1) >> y) >= 0 ? 0 : 1, test3(x, y));
        Asserts.assertEQ((x >> 30) > 4 ? 0 : 1, test4(x, y));
    }

    @Test
    @IR(counts = { IRNode.RSHIFT, "1" })
    public int test1(int x, int y) {
        return (x >> y) >= 0 ? 0 : 1;
    }

    @Test
    @IR(failOn = { IRNode.RSHIFT })
    public int test2(int x, int y) {
        return ((x & 127) >> y) >= 0 ? 0 : 1;
    }

    @Test
    @IR(failOn = { IRNode.RSHIFT })
    public int test3(int x, int y) {
        return ((-(x & 127) - 1) >> y) >= 0 ? 0 : 1;
    }

    @Test
    @IR(failOn = { IRNode.RSHIFT })
    public int test4(int x, int y) {
        return (x >> 30) > 4 ? 0 : 1;
    }
}
