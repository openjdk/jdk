/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8283466
 * @summary When skeleton predicates are not copied into peeled loop and initialized, this can happen:
 *          1. The rangecheck from a load is hoisted outside of the counted loop.
 *          2. The counted loop is peeled (we disable unswitching and unrolling with arguments)
 *          3. The type inside the peeled loop may now be narrower.
 *          4. The dataflow can die when a type becomes impossible.
 *          5. The rangecheck is still before the peeling, and is not copied to the peeled loop. Hence
 *             we do not statically realize that the peeled loop can never be entered.
 *
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+StressLCM -XX:+StressGCM -XX:+StressCCP -XX:+StressIGVN
 *                   -Xcomp -XX:-TieredCompilation
 *                   -XX:LoopMaxUnroll=0 -XX:LoopUnrollLimit=0 -XX:-LoopUnswitching
 *                   -XX:CompileCommand=compileonly,compiler.loopopts.TestPeelingSkeletonPredicateInitialization::*
 *                   compiler.loopopts.TestPeelingSkeletonPredicateInitialization
*/

package compiler.loopopts;

public class TestPeelingSkeletonPredicateInitialization {
  int N = 400;
  int array[] = new int[N];
  int array2[] = new int[N];
  void run(int X, int inv, boolean b) {
    // have the arguments so the values are unknown to C2, cannot optimize things away
    try {
      // not sure why needed. maybe has sth to do with div_by_zero below?
      int tmp = 1 / 0;
    } catch (ArithmeticException e) {
    }
    for(int i = 2; i > X; i-=3) {
      // potential div_by_zero: somehow the exit is not loop exit but rethrow
      // also: i-1 only works in peeled iteration
      // in peeled loop: ConvI2L dies because it knows dataflow is impossible
      // If skeleton_predicate is missing for peeled loop, then controlflow does not die
      array[i - 1] /= inv;
      // loop invariant check that is not hoisted: this becomes reason for peeling
      array[inv] += 1;
      if (b) {
        // seems to be required for the memory phi so that it can be mangled when data flow dies
        array[inv] += 1;
        array2[inv] += 1;
      }
    }
  }
  public static void main(String[] strArr) {
    try {
      TestPeelingSkeletonPredicateInitialization _instance = new TestPeelingSkeletonPredicateInitialization();
      for (int i = 0; i < 10000; i++) {
        _instance.run(100, 3, false);
      }
    } catch (Exception ex) {
    }
  }
}

