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

package compiler.loopopts.superword;

import jdk.test.lib.Utils;
import java.util.Random;

/*
 * @test
 * @bug 8372451
 * @summary Test long reduction chain. Triggered bug with long chain of dead ReductionVector
 *          vtnodes after optimize_move_non_strict_order_reductions_out_of_loop.
 * @library /test/lib /
 * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions -XX:LoopUnrollLimit=1000 -XX:MaxVectorSize=8 -Xbatch
 *                   -XX:CompileCommand=compileonly,${test.main.class}::test
 *                   ${test.main.class}
 * @run main ${test.main.class}
 */

public class TestLongReductionChain {
    static int RANGE = 1024*8;
    private static final Random RANDOM = Utils.getRandomInstance();

    public static void main(String[] args) {
        int[] aI = generateI();
        int[] bI = generateI();
        int gold = test(aI, bI);

        for (int i = 0; i < 1000; i++) {
            int result = test(aI, bI);
            if (result != gold) {
                throw new RuntimeException("wrong value");
            }
        }
    }

    static int[] generateI() {
        int[] a = new int[RANGE];
        for (int i = 0; i < a.length; i++) {
            a[i] = RANDOM.nextInt();
        }
        return a;
    }

    // Test creates a very long reduction chain, especially with -XX:LoopUnrollLimit=1000.
    // Limiting the reduction vectors to 2 elements gets us a very long chain -XX:MaxVectorSize=8.
    // During VTransform::optimize this means a long chain of nodes needs to be found as dead.
    // Before the fix, this took too many rounds, and we hit an assert.
    static int test(int[] a, int[] b) {
        int s = 0;
        for (int i = 0; i < RANGE; i+=8) {
            s += a[i+0] * b[i+0];
            s += a[i+1] * b[i+1];
            s += a[i+2] * b[i+2];
            s += a[i+3] * b[i+3];

            s += a[i+4] & b[i+4];
            s += a[i+5] & b[i+5];
            s += a[i+6] & b[i+6];
            s += a[i+7] & b[i+7];
        }
        return s;
    }
}
