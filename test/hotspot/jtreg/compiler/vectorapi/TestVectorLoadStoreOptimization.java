/*
 * Copyright (c) 2025, NVIDIA CORPORATION & AFFILIATES. All rights reserved.
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

import compiler.lib.generators.*;
import compiler.lib.ir_framework.*;
import jdk.incubator.vector.*;
import jdk.test.lib.Asserts;

/**
 * @test 8371603
 * @key randomness
 * @library /test/lib /
 * @summary Test the missing optimization issues for vector load/store caused by JDK-8286941
 * @modules jdk.incubator.vector
 *
 * @run driver ${test.main.class}
 */
public class TestVectorLoadStoreOptimization {
    private static final int LENGTH = 1024;
    private static final Generators random = Generators.G;

    private static final VectorSpecies<Integer> SPECIES = IntVector.SPECIES_PREFERRED;

    private static int[] a;

    static {
        a = new int[LENGTH];
        random.fill(random.ints(), a);
    }

    // Test that "LoadVectorNode::Ideal()" calls "LoadNode::Ideal()" as expected,
    // which sees the previous stores that go to the same position in-dependently,
    // and optimize out the load with matched store values.
    @Test
    @IR(counts = { IRNode.LOAD_VECTOR_I, "1" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testLoadVector() {
        IntVector v1 = IntVector.fromArray(SPECIES, a, 0);
        v1.intoArray(a, SPECIES.length());
        v1.intoArray(a, 2 * SPECIES.length());
        // The second load vector equals to the first one and should be optimized
        // out by "LoadNode::Ideal()".
        IntVector v2 = IntVector.fromArray(SPECIES, a, SPECIES.length());
        v2.intoArray(a, 3 * SPECIES.length());
    }

    @Check(test = "testLoadVector")
    public static void testLoadVectorVerify() {
        for (int i = SPECIES.length(); i < 4 * SPECIES.length(); i += SPECIES.length()) {
            for (int j = 0; j < SPECIES.length(); j++) {
                Asserts.assertEquals(a[i + j], a[j]);
            }
        }
    }

    // Test that "StoreVectorNode::Ideal()" calls "StoreNode::Ideal()" as expected,
    // which can get rid of previous stores that go to the same position.
    @Test
    @IR(counts = { IRNode.STORE_VECTOR, "1" },
        applyIfCPUFeatureOr = {"asimd", "true", "avx", "true", "rvv", "true"})
    public static void testStoreVector() {
        IntVector v1 = IntVector.fromArray(SPECIES, a, 0 * SPECIES.length());
        IntVector v2 = IntVector.fromArray(SPECIES, a, 1 * SPECIES.length());
        // Useless store to same position as below, which should be optimized out by
        // "StoreNode::Ideal()".
        v1.intoArray(a, 3 * SPECIES.length());
        v2.intoArray(a, 3 * SPECIES.length());
    }

    @Check(test = "testStoreVector")
    public static void testStoreVectorVerify() {
        for (int i = 3 * SPECIES.length(); i < 4 * SPECIES.length(); i++) {
            Asserts.assertEquals(a[i], a[i - 2 * SPECIES.length()]);
        }
    }

    public static void main(String[] args) {
        TestFramework testFramework = new TestFramework();
        testFramework.setDefaultWarmup(10000)
                     .addFlags("--add-modules=jdk.incubator.vector")
                     .start();
    }
}