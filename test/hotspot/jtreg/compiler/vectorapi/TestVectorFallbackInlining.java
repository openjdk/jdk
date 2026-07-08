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

package compiler.vectorapi;

import compiler.lib.ir_framework.*;
import compiler.lib.verify.Verify;
import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorSpecies;

/*
 * @test
 * @bug 8382713
 * @summary Verify that the fallback implementation of a failed vector intrinsic is
 *          inlined (rather than left as a static call) when IncrementalInlineVector
 *          is enabled.
 * @modules jdk.incubator.vector
 * @library /test/lib /
 * @requires os.simpleArch == "x64" & vm.cpu.features ~= ".*avx2.*"
 * @run driver compiler.vectorapi.TestVectorFallbackInlining
 */

/**
 * A 512-bit FloatVector operation cannot be intrinsified when AVX-512 is not used
 * (here forced with -XX:UseAVX=2), so the vector intrinsic fails to expand. With
 * IncrementalInlineVector enabled (the default) the fallback implementation
 * (VectorSupport::binaryOp) is inlined into the caller, absorbing the call
 * overhead; with the flag disabled, a static call to the fallback remains.
 */
public class TestVectorFallbackInlining {
    private static final VectorSpecies<Float> SPECIES = FloatVector.SPECIES_512;
    private static final int LENGTH = 1024;

    private static final float[] a = new float[LENGTH];
    private static final float[] b = new float[LENGTH];
    private static final float[] c = new float[LENGTH];

    static {
        for (int i = 0; i < LENGTH; i++) {
            a[i] = i;
            b[i] = LENGTH - i;
        }
    }

    public static void main(String[] args) {
        TestFramework.runWithFlags("--add-modules=jdk.incubator.vector",
                                   "-XX:UseAVX=2",
                                   "-XX:+IncrementalInlineVector");
        TestFramework.runWithFlags("--add-modules=jdk.incubator.vector",
                                   "-XX:UseAVX=2",
                                   "-XX:-IncrementalInlineVector");
    }

    @Test
    @IR(counts = {IRNode.VECTORAPI_BINARY_OP, ">= 1"},
        applyIf = {"IncrementalInlineVector", "false"})
    @IR(failOn = {IRNode.VECTORAPI_BINARY_OP},
        applyIf = {"IncrementalInlineVector", "true"})
    public static void testAdd() {
        for (int i = 0; i < LENGTH; i += SPECIES.length()) {
            FloatVector va = FloatVector.fromArray(SPECIES, a, i);
            FloatVector vb = FloatVector.fromArray(SPECIES, b, i);
            va.add(vb).intoArray(c, i);
        }
    }

    @Run(test = "testAdd")
    @Warmup(10000)
    public static void runAdd() {
        testAdd();
        float[] expected = new float[LENGTH];
        for (int i = 0; i < LENGTH; i++) {
            expected[i] = a[i] + b[i];
        }
        Verify.checkEQ(expected, c);
    }

    @Test
    @IR(counts = {IRNode.VECTORAPI_BINARY_OP, ">= 1"},
        applyIf = {"IncrementalInlineVector", "false"})
    @IR(failOn = {IRNode.VECTORAPI_BINARY_OP},
        applyIf = {"IncrementalInlineVector", "true"})
    public static void testMul() {
        for (int i = 0; i < LENGTH; i += SPECIES.length()) {
            FloatVector va = FloatVector.fromArray(SPECIES, a, i);
            FloatVector vb = FloatVector.fromArray(SPECIES, b, i);
            va.mul(vb).intoArray(c, i);
        }
    }

    @Run(test = "testMul")
    @Warmup(10000)
    public static void runMul() {
        testMul();
        float[] expected = new float[LENGTH];
        for (int i = 0; i < LENGTH; i++) {
            expected[i] = a[i] * b[i];
        }
        Verify.checkEQ(expected, c);
    }
}
