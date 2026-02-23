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

/*
 * @test id=ir
 * @bug 8378166
 * @summary Visual example of the Vector API: NBody / Particle Life simulation.
 * @library /test/lib /
 * @modules jdk.incubator.vector
 * @run driver ${test.main.class} ir
 */

/*
 * @test id=visual
 * @key headful
 * @library /test/lib /
 * @modules jdk.incubator.vector
 * @run main ${test.main.class} visual
 */

package compiler.gallery;

import jdk.test.lib.Utils;

import compiler.lib.ir_framework.*;

/**
 * This test is the JTREG version for automatic verification of the stand-alone
 * {@link ParticleLife}. If you just want to run the demo and play with it,
 * go look at the documentation in {@link ParticleLife}.
 * Here, we launch both a visual version that just runs for a few seconds, to see
 * that there are no crashes, but we don't do any specific verification.
 * We also have an IR test, that ensures that we get vectorization.
 */
public class TestParticleLife {
    public static void main(String[] args) throws InterruptedException {
        String mode = args[0];
        System.out.println("Running JTREG test in mode: " + mode);

        switch (mode) {
            case "ir" -> runIR();
            case "visual" -> runVisual();
            default -> throw new RuntimeException("Unknown mode: " + mode);
        }
    }

    private static void runIR() {
        System.out.println("Testing with IR rules...");
        TestFramework.runWithFlags("-XX:CompileCommand=inline,compiler.gallery.ParticleLife$State::update*",
                                   "--add-modules=jdk.incubator.vector");
    }

    private static void runVisual() throws InterruptedException {
        System.out.println("Testing with 2d Graphics (visual)...");

        // We will not do anything special here, just launch the application,
        // tell it to run for 10 second, interrupt it and let it shut down.
        Thread thread = new Thread() {
            public void run() {
                ParticleLife.main();
            }
        };
        thread.setDaemon(true);
        thread.start();
        Thread.sleep(Utils.adjustTimeout(10000)); // let demo run for 10 seconds
        thread.interrupt();
        Thread.sleep(Utils.adjustTimeout(1000)); // allow demo 1 second for shutdown
    }

    // ---------------------- For the IR testing part only --------------------------------
    ParticleLife.State state = new ParticleLife.State();

    @Test
    @Warmup(100)
    @IR(counts = {IRNode.REPLICATE_F,     "> 0",
                  IRNode.LOAD_VECTOR_F,   "> 0",
                  IRNode.SUB_VF,          "> 0",
                  IRNode.MUL_VF,          "> 0",
                  IRNode.ADD_VF,          "> 0",
                  IRNode.SQRT_VF,         "> 0",
                  IRNode.STORE_VECTOR,    "> 0"},
        applyIf = {"AlignVector", "false"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    private void testIR_updatePositions() {
        // This call should inline given the CompileCommand above.
        // We expect auto vectorization of the relatively simple loop.
        state.updatePositions();
    }

    @Test
    @Warmup(10)
    @IR(counts = {IRNode.REPLICATE_F,        "= 0",
                  IRNode.LOAD_VECTOR_F,      "= 0",
                  IRNode.REPLICATE_I,        "= 0",
                  IRNode.LOAD_VECTOR_I,      "= 0",
                  IRNode.ADD_VI,             "= 0",
                  IRNode.LOAD_VECTOR_GATHER, "= 0",
                  IRNode.SUB_VF,             "= 0",
                  IRNode.MUL_VF,             "= 0",
                  IRNode.ADD_VF,             "= 0",
                  IRNode.NEG_VF,             "= 0",
                  IRNode.SQRT_VF,            "= 0",
                  IRNode.DIV_VF,             "= 0",
                  IRNode.VECTOR_MASK_CMP,    "= 0",
                  IRNode.VECTOR_MASK_CAST,   "= 0",
                  IRNode.AND_V_MASK,         "= 0",
                  IRNode.VECTOR_BLEND_F,     "= 0",
                  IRNode.STORE_VECTOR,       "= 0",
                  IRNode.ADD_REDUCTION_VF,   "= 0"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"avx2", "true", "asimd", "true"})
    private void testIR_updateForcesScalar() {
        // This call should inline given the CompileCommand above.
        // We expect no vectorization, though it may in principle be possible
        // to auto vectorize one day.
        state.updateForcesScalar();
    }

    @Test
    @Warmup(10)
    @IR(counts = {IRNode.REPLICATE_F,        "> 0",
                  IRNode.LOAD_VECTOR_F,      "> 0",
                  IRNode.REPLICATE_I,        "> 0",
                  IRNode.LOAD_VECTOR_I,      "> 0",
                  IRNode.ADD_VI,             "> 0",
                  IRNode.LOAD_VECTOR_GATHER, "> 0",
                  IRNode.SUB_VF,             "> 0",
                  IRNode.MUL_VF,             "> 0",
                  IRNode.ADD_VF,             "> 0",
                  IRNode.NEG_VF,             "> 0",
                  IRNode.SQRT_VF,            "> 0",
                  IRNode.DIV_VF,             "> 0",
                  IRNode.VECTOR_MASK_CMP,    "> 0",
                  IRNode.VECTOR_MASK_CAST,   "> 0",
                  IRNode.VECTOR_BLEND_F,     "> 0",
                  IRNode.ADD_REDUCTION_VF,   "> 0"}, // instead we reduce the vector to a scalar
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeature = {"avx2", "true"})
    private void testIR_updateForcesVectorAPI_Inner_Gather() {
        // This call should inline given the CompileCommand above.
        // We expect the VectorAPI calls to intrinsify.
        state.updateForcesVectorAPI_Inner_Gather();
    }

    @Test
    @Warmup(10)
    @IR(counts = {IRNode.REPLICATE_F,        "> 0",
                  IRNode.LOAD_VECTOR_F,      "> 0",
                  IRNode.REPLICATE_I,        "= 0", // No gather operation
                  IRNode.LOAD_VECTOR_I,      "= 0", // No gather operation
                  IRNode.ADD_VI,             "= 0", // No gather operation
                  IRNode.LOAD_VECTOR_GATHER, "= 0", // No gather operation
                  IRNode.SUB_VF,             "> 0",
                  IRNode.MUL_VF,             "> 0",
                  IRNode.ADD_VF,             "> 0",
                  IRNode.NEG_VF,             "> 0",
                  IRNode.SQRT_VF,            "> 0",
                  IRNode.DIV_VF,             "> 0",
                  IRNode.VECTOR_MASK_CMP,    "> 0",
                  IRNode.VECTOR_MASK_CAST,   "> 0",
                  IRNode.VECTOR_BLEND_F,     "> 0",
                  IRNode.ADD_REDUCTION_VF,   "> 0"}, // instead we reduce the vector to a scalar
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"avx2", "true", "asimd", "true"})
    private void testIR_updateForcesVectorAPI_Inner_Rearranged() {
        // This call should inline given the CompileCommand above.
        // We expect the VectorAPI calls to intrinsify.
        state.updateForcesVectorAPI_Inner_Rearranged();
    }


    @Test
    @Warmup(10)
    @IR(counts = {IRNode.REPLICATE_F,        "> 0",
                  IRNode.LOAD_VECTOR_F,      "> 0",
                  IRNode.REPLICATE_I,        "> 0",
                  IRNode.LOAD_VECTOR_I,      "> 0",
                  IRNode.ADD_VI,             "> 0",
                  IRNode.LOAD_VECTOR_GATHER, "> 0",
                  IRNode.SUB_VF,             "> 0",
                  IRNode.MUL_VF,             "> 0",
                  IRNode.ADD_VF,             "> 0",
                  IRNode.NEG_VF,             "> 0",
                  IRNode.SQRT_VF,            "> 0",
                  IRNode.DIV_VF,             "> 0",
                  IRNode.VECTOR_MASK_CMP,    "> 0",
                  IRNode.VECTOR_MASK_CAST,   "> 0",
                  IRNode.VECTOR_BLEND_F,     "> 0",
                  IRNode.STORE_VECTOR,       "> 0",  // store back a vector
                  IRNode.ADD_REDUCTION_VF,   "= 0"}, // and no reduction operation
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeature = {"avx2", "true"})
    private void testIR_updateForcesVectorAPI_Outer() {
        // This call should inline given the CompileCommand above.
        // We expect the VectorAPI calls to intrinsify.
        state.updateForcesVectorAPI_Outer();
    }
}
