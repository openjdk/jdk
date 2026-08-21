/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026, NTT DATA
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
import jdk.incubator.vector.*;
import jtreg.SkippedException;

/**
 * @test
 * @bug 8378312 8378902
 * @library /test/lib /
 * @summary VectorAPI: libraryUnaryOp and libraryBinaryOp should be intrinsified. This test would be run on SVML/SLEEF supported platforms only.
 * @modules jdk.incubator.vector
 *
 * @run driver compiler.vectorapi.TestVectorLibraryUnaryOpAndBinaryOp
 */

public class TestVectorLibraryUnaryOpAndBinaryOp {

    @Test
    @IR(counts = { IRNode.CALL_LEAF_VECTOR, "= 1" }, applyIfCPUFeatureOr = { "asimd", "true", "avx", "true" })
    public static void testUnary() {
        var vec = FloatVector.broadcast(FloatVector.SPECIES_128, 3.14f);
        vec.lanewise(VectorOperators.COS);
    }

    @Test
    @IR(counts = { IRNode.CALL_LEAF_VECTOR, "= 1" }, applyIfCPUFeatureOr = { "asimd", "true", "avx", "true" })
    public static void testBinary() {
        var vec = FloatVector.broadcast(FloatVector.SPECIES_128, 2.0f);
        vec.lanewise(VectorOperators.HYPOT, 1.0f);
    }

    private static void checkVectorMathLib() {
        try {
            // Check jsvml first
            System.loadLibrary("jsvml");
        } catch (UnsatisfiedLinkError _) {
            try {
                // Check sleef if jsvml not found
                System.loadLibrary("sleef");
            } catch (UnsatisfiedLinkError _) {
                // This test is run on unsupported platform - should be skipped
                throw new SkippedException("SVML / SLEEF not found");
            }
        }
    }

    public static void main(String[] args) {
        checkVectorMathLib();

        TestFramework testFramework = new TestFramework();
        testFramework.addFlags("--add-modules=jdk.incubator.vector")
                     .start();
    }

}
