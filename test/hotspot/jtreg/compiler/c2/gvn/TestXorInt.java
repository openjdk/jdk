/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package compiler.c2.gvn;

import compiler.lib.generators.Generator;
import compiler.lib.generators.Generators;
import compiler.lib.ir_framework.*;
import jdk.test.lib.Asserts;

/*
 * @test
 * @summary verify that constant folding is done on xor
 * @bug 8347645
 * @library /test/lib /
 * @requires vm.compiler2.enabled
 * @run driver compiler.c2.gvn.TestXorInt
 */

public class TestXorInt {
    private static final Generator<Integer> G = Generators.G.ints();
    private static final int CONST_1 = G.next();
    private static final int CONST_2 = G.next();

    public static void main(String[] args) {
        TestFramework.run();
    }

    @Test
    @IR(failOn = {IRNode.XOR})
    @IR(counts = {IRNode.CON_I, "1"})
    // Checks (c ^c)  => c (constant folded)
    public int testConstXor() {
        return CONST_1 ^ CONST_2;
    }

    @Check(test = "testConstXor")
    public void checkTestConstXor(int result) {
        Asserts.assertEquals(interpretedXor(CONST_1, CONST_2), result);
    }

    @DontCompile
    private static int interpretedXor(int x, int y) {
        return x ^ y;
    }

    @Test
    @IR(failOn = {IRNode.XOR})
    @IR(counts = {IRNode.CON_I, "1"})
    // Checks (x ^ x)  => c (constant folded)
    @Arguments(values = Argument.RANDOM_EACH)
    public int testXorSelf(int x) {
        return x ^ x;
    }

    @Check(test = "testXorSelf")
    public void checkTestXorSelf(int result) {
        Asserts.assertEquals(0, result);
    }
}



