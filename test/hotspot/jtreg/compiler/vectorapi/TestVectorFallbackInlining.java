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
 * @requires vm.compiler2.enabled
 * @run driver ${test.main.class}
 */

/**
 * Vector.withLane with a non-constant lane index cannot be intrinsified: the
 * insert inline expander bails out because the index is not a compile-time constant.
 * This is a species- and platform-agnostic limitation of the expander -- it is independent
 * of the element type, vector shape and the available CPU features -- so the vector
 * intrinsic reliably fails to expand and the fallback implementation
 * is exercised on every configuration.
 *
 * With IncrementalInlineVector enabled (the default) the fallback is late-inlined into the
 * caller, so no static call to intrinsic entry point survives; with the flag disabled, the
 * static call to the fallback remains.
 */
public class TestVectorFallbackInlining {
    private static final VectorSpecies<Float> SPECIES = FloatVector.SPECIES_MAX;
    private static final int LENGTH = 1024;

    // Non-final so the JIT cannot treat the lane index as a compile-time constant, which
    // is what forces the insert intrinsic to bail out to its fallback implementation.
    private static int INDEX = SPECIES.length() - 1;

    private static final float[] a = new float[LENGTH];
    private static final float[] c = new float[LENGTH];

    static {
        for (int i = 0; i < LENGTH; i++) {
            a[i] = i;
        }
    }

    // The fallback is only late-inlined if it also passes the regular inlining heuristics.
    // During warmup VectorSupport::insert is called from the interpreter often enough to be
    // compiled on its own, and with assertions enabled its nmethod grows beyond
    // InlineSmallCode, so C2 rejects it as "already compiled into a big method". Keep it
    // inlinable so that the rules below only depend on the late inlining machinery.
    private static final String FORCE_INLINE_FALLBACK =
            "-XX:CompileCommand=inline,jdk.internal.vm.vector.VectorSupport::insert";

    public static void main(String[] args) {
        TestFramework.runWithFlags("--add-modules=jdk.incubator.vector",
                                   FORCE_INLINE_FALLBACK,
                                   "-XX:+IncrementalInlineVector");
        TestFramework.runWithFlags("--add-modules=jdk.incubator.vector",
                                   FORCE_INLINE_FALLBACK,
                                   "-XX:-IncrementalInlineVector");
    }

    @Test
    @IR(counts = {IRNode.VECTORAPI_INSERT_OP, ">= 1"},
        applyIf = {"IncrementalInlineVector", "false"})
    @IR(failOn = {IRNode.VECTORAPI_INSERT_OP},
        applyIf = {"IncrementalInlineVector", "true"})
    public static void testInsert() {
        for (int i = 0; i < LENGTH; i += SPECIES.length()) {
            FloatVector va = FloatVector.fromArray(SPECIES, a, i);
            va.withLane(INDEX, 0.0f).intoArray(c, i);
        }
    }

    @Run(test = "testInsert")
    @Warmup(10000)
    public static void runInsert() {
        testInsert();
        float[] expected = new float[LENGTH];
        for (int i = 0; i < LENGTH; i++) {
            expected[i] = a[i];
        }
        for (int i = 0; i < LENGTH; i += SPECIES.length()) {
            expected[i + INDEX] = 0.0f;
        }
        Verify.checkEQ(expected, c);
    }
}
