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
 * @bug 8382713 8394272
 * @summary Verify that the fallback implementation of a failed vector intrinsic is
 *          inlined (rather than left as a static call) for the selected set of
 *          operations when IncrementalInlineVector is left at its default value,
 *          for every operation when it is explicitly enabled, and for none when
 *          it is explicitly disabled.
 * @modules jdk.incubator.vector
 * @library /test/lib /
 * @requires vm.compiler2.enabled
 * @run driver ${test.main.class}
 */

/**
 * Vector.slice and Vector.withLane with a non-constant index cannot be intrinsified: the
 * slice and insert inline expanders bail out because the index is not a compile-time constant.
 * This is a species- and platform-agnostic limitation of the expanders -- it is independent
 * of the element type, vector shape and the available CPU features -- so the vector
 * intrinsics reliably fail to expand and the fallback implementations
 * are exercised on every configuration.
 *
 * Slice is a selected operation, so its fallback is late-inlined into the caller and no
 * static call to the intrinsic entry point survives, unless the flag is explicitly disabled.
 * Insert is not selected, so its static call survives unless the flag is explicitly enabled,
 * which selects every operation.
 */
public class TestVectorFallbackInlining {
    private static final VectorSpecies<Float> SPECIES = FloatVector.SPECIES_MAX;
    private static final int LENGTH = 1024;

    // Non-final so the JIT cannot treat the index as a compile-time constant, which
    // is what forces the slice and insert intrinsics to bail out to their fallback
    // implementations.
    private static int INDEX = SPECIES.length() - 1;

    private static final float[] a = new float[LENGTH];
    private static final float[] c = new float[LENGTH];

    static {
        for (int i = 0; i < LENGTH; i++) {
            a[i] = i;
        }
    }

    // The fallback is only late-inlined if it also passes the regular inlining heuristics.
    // During warmup the entry points are called from the interpreter often enough to be
    // compiled on their own, and with assertions enabled their nmethods grow beyond
    // InlineSmallCode, so C2 rejects them as too large. Keep them inlinable so that the
    // rules below only depend on the late inlining machinery.
    private static final String FORCE_INLINE_SLICE_FALLBACK =
            "-XX:CompileCommand=inline,jdk.internal.vm.vector.VectorSupport::sliceOp";
    private static final String FORCE_INLINE_INSERT_FALLBACK =
            "-XX:CompileCommand=inline,jdk.internal.vm.vector.VectorSupport::insert";

    public static void main(String[] args) {
        // Note: Some platforms don't activate EnableVectorSupport by default. Required here.
        // Left at its default value, only slice is selected.
        TestFramework.runWithFlags("--add-modules=jdk.incubator.vector",
                                   FORCE_INLINE_SLICE_FALLBACK,
                                   FORCE_INLINE_INSERT_FALLBACK,
                                   "-XX:+UnlockExperimentalVMOptions",
                                   "-XX:+EnableVectorSupport");
        // Explicitly enabled, so every operation is selected.
        TestFramework.runWithFlags("--add-modules=jdk.incubator.vector",
                                   FORCE_INLINE_SLICE_FALLBACK,
                                   FORCE_INLINE_INSERT_FALLBACK,
                                   "-XX:+IncrementalInlineVector",
                                   "-XX:+UnlockExperimentalVMOptions",
                                   "-XX:+EnableVectorSupport");
        // Explicitly disabled, so no operation is selected. Only slice needs a separate
        // class, see TestSliceFallbackNotInlined.
        new TestFramework(TestSliceFallbackNotInlined.class)
                .addFlags("--add-modules=jdk.incubator.vector",
                          FORCE_INLINE_SLICE_FALLBACK,
                          FORCE_INLINE_INSERT_FALLBACK,
                          "-XX:-IncrementalInlineVector",
                          "-XX:+UnlockExperimentalVMOptions",
                          "-XX:+EnableVectorSupport")
                .start();
    }

    // slice is a selected operation, so its fallback is late-inlined in both runs below.
    @Test
    @IR(failOn = {IRNode.VECTORAPI_SLICE_OP})
    public static void testSlice() {
        for (int i = 0; i < LENGTH; i += SPECIES.length()) {
            FloatVector va = FloatVector.fromArray(SPECIES, a, i);
            va.slice(INDEX).intoArray(c, i);
        }
    }

    @Run(test = "testSlice")
    @Warmup(10000)
    public static void runSlice() {
        testSlice();
        float[] expected = new float[LENGTH];
        for (int i = 0; i < LENGTH; i += SPECIES.length()) {
            for (int j = 0; j < SPECIES.length(); j++) {
                int src = i + INDEX + j;
                expected[i + j] = (INDEX + j < SPECIES.length()) ? a[src] : 0.0f;
            }
        }
        Verify.checkEQ(expected, c);
    }

    // insert is only selected when the flag is set explicitly.
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

/**
 * Covers slice with IncrementalInlineVector explicitly disabled, where its fallback is no
 * longer selected and the static call survives.
 */
class TestSliceFallbackNotInlined {
    private static final VectorSpecies<Float> SPECIES = FloatVector.SPECIES_MAX;
    private static final int LENGTH = 1024;

    private static int INDEX = SPECIES.length() - 1;

    private static final float[] a = new float[LENGTH];
    private static final float[] c = new float[LENGTH];

    static {
        for (int i = 0; i < LENGTH; i++) {
            a[i] = i;
        }
    }

    @Test
    @IR(counts = {IRNode.VECTORAPI_SLICE_OP, ">= 1"})
    public static void testSlice() {
        for (int i = 0; i < LENGTH; i += SPECIES.length()) {
            FloatVector va = FloatVector.fromArray(SPECIES, a, i);
            va.slice(INDEX).intoArray(c, i);
        }
    }

    @Run(test = "testSlice")
    @Warmup(10000)
    public static void runSlice() {
        testSlice();
        float[] expected = new float[LENGTH];
        for (int i = 0; i < LENGTH; i += SPECIES.length()) {
            for (int j = 0; j < SPECIES.length(); j++) {
                int src = i + INDEX + j;
                expected[i + j] = (INDEX + j < SPECIES.length()) ? a[src] : 0.0f;
            }
        }
        Verify.checkEQ(expected, c);
    }
}
