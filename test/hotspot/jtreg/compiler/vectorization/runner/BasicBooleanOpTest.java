/*
 * Copyright (c) 2022, 2023, Arm Limited. All rights reserved.
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

/*
 * @test
 * @summary Vectorization test on basic boolean operations
 * @library /test/lib /
 *
 * @build jdk.test.whitebox.WhiteBox
 *        compiler.vectorization.runner.VectorizationTestRunner
 *
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:.
 *                   -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI
 *                   compiler.vectorization.runner.BasicBooleanOpTest
 *
 * @requires vm.compiler2.enabled & vm.flagless
 */

package compiler.vectorization.runner;

import compiler.lib.ir_framework.*;

public class BasicBooleanOpTest extends VectorizationTestRunner {

    private static final int SIZE = 6543;

    private boolean[] a;
    private boolean[] b;
    private boolean[] c;

    public BasicBooleanOpTest() {
        a = new boolean[SIZE];
        b = new boolean[SIZE];
        c = new boolean[SIZE];
        for (int i = 0; i < SIZE; i++) {
            a[i] = true;
            b[i] = false;
        }
    }

    // ---------------- Logic ----------------
    @Test
    public boolean[] vectorNot() {
        boolean[] res = new boolean[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = !a[i];
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeature = {"asimd", "true"},
        counts = {IRNode.AND_VB, ">0"})
    @IR(applyIfCPUFeatureAnd = {"avx512f", "false", "sse2", "true"},
        counts = {IRNode.AND_VB, ">0"})
    @IR(applyIfCPUFeature = {"avx512f", "true"},
        counts = {IRNode.MACRO_LOGIC_V, ">0"})
    public boolean[] vectorAnd() {
        boolean[] res = new boolean[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = a[i] & b[i];
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        counts = {IRNode.OR_VB, ">0"})
    public boolean[] vectorOr() {
        boolean[] res = new boolean[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = a[i] | b[i];
        }
        return res;
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        counts = {IRNode.XOR_VB, ">0"})
    public boolean[] vectorXor() {
        boolean[] res = new boolean[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = a[i] ^ b[i];
        }
        return res;
    }
}
