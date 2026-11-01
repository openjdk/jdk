/*
 * Copyright (c) 2022, 2023, Arm Limited. All rights reserved.
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
 * @test
 * @summary Vectorization test with small strip mining iterations
 * @library /test/lib /
 * @requires vm.compiler2.enabled
 *
 * @run driver ${test.main.class}
 */

package compiler.vectorization.runner;

import compiler.lib.ir_framework.*;

import java.util.Random;

public class StripMinedLoopTest extends VectorizationTestRunner {

    private static final int SIZE = 543;

    private int[] a = new int[SIZE];
    private int[] b = new int[SIZE];

    public StripMinedLoopTest() {
        for (int i = 0; i < SIZE; i++) {
            a[i] = 2;
            b[i] = 3;
        }
    }

    // We must pass the flags directly to the Test VM, and not the Driver VM in the @run above.
    @Override
    protected String[] testVMFlags(String[] args) {
        return new String[]{"-XX:LoopStripMiningIter=10"};
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true", "rvv", "true"},
        counts = {IRNode.STORE_VECTOR, ">0"})
    public int[] stripMinedVectorLoop() {
        int[] res = new int[SIZE];
        for (int i = 0; i < SIZE; i++) {
            res[i] = a[i] + b[i];
        }
        return res;
    }

    @Test
    public int stripMinedReductionLoop() {
        int res = 0;
        for (int i = 0; i < SIZE; i++) {
            res += a[i];
        }
        return res;
    }

    @Test
    public int stripMinedOneIterationLoop() {
        int[] res = new int[SIZE];
        int i1, i2, i3, i4 = 11937;
        for (i1 = 1; i1 < SIZE; i1++) {
            for (i2 = 1; i2 < 2; i2++) {
                for (i3 = 1; i3 < 2; i3++) {
                    i4 &= i3;
                }
            }
            res[i1] = 0;
        }
        return res[0] + i4;
    }
}
