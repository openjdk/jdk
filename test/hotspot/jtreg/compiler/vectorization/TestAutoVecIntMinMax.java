/*
 * Copyright (c) 2022, Arm Limited. All rights reserved.
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

import compiler.lib.ir_framework.*;
import java.util.Random;
import jdk.test.lib.Utils;

/*
 * @test
 * @bug 8288107
 * @summary Auto-vectorization enhancement for integer Math.max/Math.min operations
 * @library /test/lib /
 * @requires vm.compiler2.enabled
 * @requires (os.simpleArch == "x64" & (vm.opt.UseSSE == "null" | vm.opt.UseSSE > 3))
 *           | os.arch == "aarch64" | (os.arch == "riscv64" & vm.opt.UseRVV == true)
 * @run driver compiler.c2.irTests.TestAutoVecIntMinMax
 */

public class TestAutoVecIntMinMax {
    private final static int LENGTH = 2000;
    private final static Random RANDOM = Utils.getRandomInstance();

    private static int[] a;
    private static int[] b;
    private static int[] c;

    static {
        a = new int[LENGTH];
        b = new int[LENGTH];
        c = new int[LENGTH];
        for(int i = 0; i < LENGTH; i++) {
            a[i] = RANDOM.nextInt();
            b[i] = RANDOM.nextInt();
        }
    }

    public static void main(String[] args) {
        TestFramework.run();
    }

    // Test for auto-vectorization of Math.min operation on an array of integers
    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I,  " >0 "})
    @IR(counts = {IRNode.MIN_VI, " >0 "})
    @IR(counts = {IRNode.STORE_VECTOR, " >0 "})
    private static void testIntMin(int[] a, int[] b) {
        for(int i = 0; i < LENGTH; i++) {
            c[i] = Math.min(a[i], b[i]);
        }
    }

    // Test for auto-vectorization of StrictMath.min operation on an array of integers
    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I,  " >0 "})
    @IR(counts = {IRNode.MIN_VI, " >0 "})
    @IR(counts = {IRNode.STORE_VECTOR, " >0 "})
    private static void testIntStrictMin(int[] a, int[] b) {
        for(int i = 0; i < LENGTH; i++) {
            c[i] = StrictMath.min(a[i], b[i]);
        }
    }

    // Test for auto-vectorization of Math.max operation on an array of integers
    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I,  " >0 "})
    @IR(counts = {IRNode.MAX_VI, " >0 "})
    @IR(counts = {IRNode.STORE_VECTOR, " >0 "})
    private static void testIntMax(int[] a, int[] b) {
        for(int i = 0; i < LENGTH; i++) {
            c[i] = Math.max(a[i], b[i]);
        }
    }

    // Test for auto-vectorization of StrictMath.max operation on an array of integers
    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I,  " >0 "})
    @IR(counts = {IRNode.MAX_VI, " >0 "})
    @IR(counts = {IRNode.STORE_VECTOR, " >0 "})
    private static void testIntStrictMax(int[] a, int[] b) {
        for(int i = 0; i < LENGTH; i++) {
            c[i] = StrictMath.max(a[i], b[i]);
        }
    }

    @Run(test = {"testIntMin", "testIntStrictMin", "testIntMax", "testIntStrictMax"})
    private void testIntMinMax_runner() {
        testIntMin(a, b);
        testIntStrictMin(a, b);
        testIntMax(a, b);
        testIntStrictMax(a, b);
    }
}
