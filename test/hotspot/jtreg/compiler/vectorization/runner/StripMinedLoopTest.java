/*
 * Copyright (c) 2022, Arm Limited. All rights reserved.
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
 *
 * @build sun.hotspot.WhiteBox
 *        compiler.vectorization.runner.VectorizationTestRunner
 *
 * @run driver jdk.test.lib.helpers.ClassFileInstaller sun.hotspot.WhiteBox
 * @run main/othervm -Xbootclasspath/a:.
 *                   -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI
 *                   -XX:LoopStripMiningIter=10
 *                   compiler.vectorization.runner.StripMinedLoopTest
 *
 * @requires vm.compiler2.enabled & vm.flagless
 */

package compiler.vectorization.runner;

import java.util.Random;

public class StripMinedLoopTest extends VectorizationTestRunner {

    private static final int SIZE = 2345;

    private int[] a = new int[SIZE];
    private int[] b = new int[SIZE];

    public StripMinedLoopTest() {
        for (int i = 0; i < SIZE; i++) {
            a[i] = 2;
            b[i] = 3;
        }
    }

    @Test
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
}

