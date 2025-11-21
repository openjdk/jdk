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

/*
 * @test id=ir
 * @bug 8367657
 * @summary Visual example of auto vectorization: normal mapping.
 * @library /test/lib /
 * @run driver compiler.gallery.TestNormalMapping ir
 */

/*
 * @test id=visual
 * @key headful
 * @library /test/lib /
 * @run main compiler.gallery.TestNormalMapping visual
 */

package compiler.gallery;

import jdk.test.lib.Utils;

import compiler.lib.ir_framework.*;

/**
 * This test is the JTREG version for automatic verification of the stand-alone
 * {@link NormalMapping}. If you just want to run the demo and play with it,
 * go look at the documentation in {@link NormalMapping}.
 * Here, we launch both a visual version that just runs for a few seconds, to see
 * that there are no crashes, but we don't do any specific verification.
 * We also have an IR test, that ensures that we get vectorization.
 */
public class TestNormalMapping {
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
        String src = System.getProperty("test.src", null);
        if (src == null) { throw new RuntimeException("Could not find test.src property."); }
        TestFramework.runWithFlags("-Dtest.src=" + src,
                                   "-XX:CompileCommand=inline,compiler.gallery.NormalMapping$State::update",
                                   "-XX:CompileCommand=inline,compiler.gallery.NormalMapping$State::computeLight");
    }

    private static void runVisual() throws InterruptedException {
        System.out.println("Testing with 2d Graphics (visual)...");

        // We will not do anything special here, just launch the application,
        // tell it to run for 10 second, interrupt it and let it shut down.
        Thread thread = new Thread() {
            public void run() {
                NormalMapping.main(null);
            }
        };
        thread.setDaemon(true);
        thread.start();
        Thread.sleep(Utils.adjustTimeout(10000)); // let demo run for 10 seconds
        thread.interrupt();
        Thread.sleep(Utils.adjustTimeout(1000)); // allow demo 1 second for shutdown
    }

    // ---------------------- For the IR testing part only --------------------------------
    NormalMapping.State state = new NormalMapping.State(5);

    @Test
    @Warmup(1000)
    @IR(counts = {IRNode.REPLICATE_I,     IRNode.VECTOR_SIZE + "min(max_int, max_float)", "> 0",
                  IRNode.REPLICATE_F,     IRNode.VECTOR_SIZE + "min(max_int, max_float)", "> 0",
                  IRNode.LOAD_VECTOR_F,   IRNode.VECTOR_SIZE + "min(max_int, max_float)", "> 0",
                  IRNode.SUB_VF,          IRNode.VECTOR_SIZE + "min(max_int, max_float)", "> 0",
                  IRNode.MUL_VF,          IRNode.VECTOR_SIZE + "min(max_int, max_float)", "> 0",
                  IRNode.ADD_VF,          IRNode.VECTOR_SIZE + "min(max_int, max_float)", "> 0",
                  IRNode.SQRT_VF,         IRNode.VECTOR_SIZE + "min(max_int, max_float)", "> 0",
                  IRNode.MAX_VF,          IRNode.VECTOR_SIZE + "min(max_int, max_float)", "> 0",
                  IRNode.VECTOR_CAST_F2I, IRNode.VECTOR_SIZE + "min(max_int, max_float)", "> 0",
                  IRNode.AND_VI,          IRNode.VECTOR_SIZE + "min(max_int, max_float)", "> 0",
                  IRNode.LSHIFT_VI,       IRNode.VECTOR_SIZE + "min(max_int, max_float)", "> 0",
                  IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"AlignVector", "false"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    private void testIR() {
        // This call should inline givne the CompileCommand above.
        state.update();
    }
}
