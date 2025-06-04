/*
 * Copyright (c) 2023, Arm Limited. All rights reserved.
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

import jdk.incubator.vector.IntVector;

/*
 * @test
 * @bug 8304948
 * @summary C2 crashes when expanding VectorBox
 * @modules jdk.incubator.vector
 * @library /test/lib
 *
 * @run main/othervm -Xbatch -XX:-TieredCompilation -ea -XX:CompileCommand=dontinline,*VectorBoxExpandTest.test compiler.vectorapi.VectorBoxExpandTest
 */
public class VectorBoxExpandTest {

    private static final int ARR_LEN = 1024;
    private static final int NUM_ITER = 2000;

    private static int[] iarr = new int[ARR_LEN];
    private static IntVector g;
    private static int acc = 0;

    // C2 would generate IR graph like below:
    //
    //                ------------
    //               /            \
    //       Region |  VectorBox   |
    //            \ | /            |
    //             Phi             |
    //              |              |
    //              |              |
    //       Region |  VectorBox   |
    //            \ | /            |
    //             Phi             |
    //              |              |
    //              |\------------/
    //              |
    //
    //
    // which would be optimized by merge_through_phi through Phi::Ideal and some
    // other transformations. Finally C2 would expand VectorBox on a graph like
    // below:
    //
    //                ------------
    //               /            \
    //       Region |  Proj        |
    //            \ | /            |
    //             Phi             |
    //              |              |
    //              |              |
    //       Region |  Proj        |
    //            \ | /            |
    //             Phi             |
    //              |              |
    //              |\------------/
    //              |
    //              |      Phi
    //              |     /
    //           VectorBox
    //
    // where the cycle case should be taken into consideration as well.
    private static void test() {
        IntVector a = IntVector.fromArray(IntVector.SPECIES_PREFERRED, iarr, 0);

        for (int ic = 0; ic < NUM_ITER; ic++) {
            for (int i = 0; i < iarr.length; i++) {
                acc += System.identityHashCode(a);
                a = a.add(a);
                acc += System.identityHashCode(a);
            }
        }
        g = a;
        acc += System.identityHashCode(a);

    }

    public static void main(String[] args) {
        test();
        System.out.println("PASS");
    }
}
