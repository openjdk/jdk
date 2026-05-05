/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package compiler.c2.igvn;

import compiler.lib.ir_framework.*;
import java.util.Random;
import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;

/*
 * @test
 * @bug 8373731
 * @summary C2 IGVN should eliminate a + (b - c) after c = a * i folds to a,
 *          i.e. a + (b - a) -> b
 * @library /test/lib /
 * @run driver ${test.main.class}
 */

public class TestMissingAddSubElimination {
    private static final Random R = Utils.getRandomInstance();

    public static void main(String[] args) {
        TestFramework.runWithFlags("-XX:+IgnoreUnrecognizedVMOptions",
                                   "-XX:VerifyIterativeGVN=1000",
                                   "-XX:CompileCommand=compileonly,compiler.c2.igvn.TestMissingAddSubElimination::*");
    }

    @Run(test = {"testAddI1", "testAddI2", "testAddL1", "testAddL2"})
    public void runTestAdd(){
        int x = R.nextInt();
        int y = R.nextInt();
        Asserts.assertEQ(testAddI1(x, y), y);
        Asserts.assertEQ(testAddI2(x, y), x);

        long xl = R.nextLong();
        long yl = R.nextLong();
        Asserts.assertEQ(testAddL1(xl, yl), yl);
        Asserts.assertEQ(testAddL2(xl, yl), xl);
    }

    // int: x + (y - x) -> y
    @Test
    @IR(counts = {
            IRNode.ADD_I, "0",
            IRNode.SUB_I, "0",
            IRNode.MUL_I, "0"
    })
    int testAddI1(int x, int y) {
        int i;
        for (i = -10; i < 1; i++) { }
        int c = x * i;
        return x + (y - c);
    }

    // int: (x - y) + y -> x
    @Test
    @IR(counts = {
            IRNode.ADD_I, "0",
            IRNode.SUB_I, "0",
            IRNode.MUL_I, "0"
    })
    int testAddI2(int x, int y) {
        int i;
        for (i = -10; i < 1; i++) { }
        int c =  y * i;
        return (x - c) + y;
    }

    // long: x + (y - x) -> y
    @Test
    @IR(counts = {
            IRNode.ADD_L, "0",
            IRNode.SUB_L, "0",
            IRNode.MUL_L, "0"
    })
    long testAddL1(long x, long y) {
        int i;
        for (i = -10; i < 1; i++) { }
        long c = x * i;
        return x + (y - c);
    }

    // long: (x - y) + y -> x
    @Test
    @IR(counts = {
            IRNode.ADD_L, "0",
            IRNode.SUB_L, "0",
            IRNode.MUL_L, "0"
    })
    long testAddL2(long x, long y) {
        int i;
        for (i = -10; i < 1; i++) { }
        long c =  y * i;
        return (x - c) + y;
    }
}