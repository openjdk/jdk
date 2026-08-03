/*
 * Copyright (c) 2025 SAP SE. All rights reserved.
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
 *
 */

package compiler.vectorapi;

import compiler.lib.ir_framework.*;

import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.VectorSpecies;

import jdk.test.lib.Asserts;

/**
 * @test
 * @bug 8370473
 * @library /test/lib /
 * @summary Test alignment of vector spill slots. It should match the vector size.
 * @modules jdk.incubator.vector
 * @requires vm.opt.final.MaxVectorSize == null | vm.opt.final.MaxVectorSize >= 16
 *
 * @run driver compiler.vectorapi.TestVectorSpilling
 */

public class TestVectorSpilling {

    private static final VectorSpecies<Integer> I_SPECIES = IntVector.SPECIES_128;
    private static int LENGTH = 1024;

    private static int[] ia1;
    private static int[] ia2;
    private static int[] ir ;

    public static void main(String[] args) {
        TestFramework.runWithFlags("--add-modules=jdk.incubator.vector");
    }

    static class LData {
        // Rading from a volatile field prevents cse optimization
        static volatile long vF = 1042;

        long l1, l2, l3, l4, l5, l6, l7, l8;
        public LData() {
            l1 = vF; l2 = vF; l3 = vF; l4 = vF; l5 = vF; l6 = vF; l7 = vF; l8 = vF;
        }
        public long sum() {
            return l1 + l2 + l3 + l4 + l5 + l6 + l7 + l8;
        }
    }


    @Run(test = "test16ByteSpilling")
    static void test16ByteSpilling_runner() {
        test16ByteSpilling(1, 2, 3, 4, 5, 6, 7, 8, 9);
    }

    @Test
    @IR(counts = {IRNode.MEM_TO_REG_SPILL_COPY_TYPE, "vectorx", "> 0"},
        phase = {CompilePhase.FINAL_CODE},
        applyIfCPUFeature= {"rvv", "false"})
    static long test16ByteSpilling(long l1, long l2, long l3, long l4, long l5, long l6, long l7, long l8,
                                   long l9 /* odd stack arg */) {
        // To be scalar replaced and spilled to stack
        LData d1 = new LData();
        LData d2 = new LData();
        LData d3 = new LData();

        for (int i = 0; i < LENGTH; i += I_SPECIES.length()) {
            IntVector a1v = IntVector.fromArray(I_SPECIES, ia1, i);
            IntVector a2v = IntVector.fromArray(I_SPECIES, ia2, i);
            int scalar = spillPoint();
            a1v.add(a2v)
               .add(scalar).intoArray(ir, i);
        }

        return l1 + l2 + l3 + l4 + l5 + l6 + l7 + l8 + l9 + d1.sum() + d2.sum() + d3.sum();
    }

    @DontInline
    static int spillPoint() {
        return 42;
    }

    static {
        ia1 = new int[LENGTH];
        ia2 = new int[LENGTH];
        ir  = new int[LENGTH];
    }

}
