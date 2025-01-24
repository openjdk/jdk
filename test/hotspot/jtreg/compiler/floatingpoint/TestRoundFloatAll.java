/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, Rivos Inc. All rights reserved.
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

/**
 * @test
 * @bug 8321010
 * @summary Test intrinsic for Math.round(float) in full 32 bits range
 *
 * @library /test/lib /
 * @modules java.base/jdk.internal.math
 * @requires os.arch == "riscv64"
 * @run main/othervm -XX:-TieredCompilation -Xbatch -XX:CompileThresholdScaling=0.3 -XX:-UseSuperWord
 *      -XX:CompileCommand=compileonly,compiler.floatingpoint.TestRoundFloatAll::test*
 *      compiler.floatingpoint.TestRoundFloatAll
 */

package compiler.floatingpoint;

import static compiler.lib.golden.GoldenRound.golden_round;

public class TestRoundFloatAll {

  public static void main(String args[]) {
    test();
  }

  // return true when test fails
  static boolean test(int n, float f) {
    int actual = Math.round(f);
    int expected = golden_round(f);
    if (actual != expected) {
      System.err.println("round error, input: " + f + ", res: " + actual + "expected: " + expected + ", input hex: " + n);
      return true;
    }
    return false;
  }

  static void test() {
    final int ITERS = 11000;
    boolean fail = false;

    // Warmup
    System.out.println("Warmup");
    for (int i=0; i<ITERS; i++) {
      float f = Float.intBitsToFloat(i);
      fail |= test(i, f);
    }
    if (fail) {
      throw new RuntimeException("Warmup failed");
    }

    // Test and verify results
    System.out.println("Verification");
    int testInt = 0;
    do {
      float testFloat = Float.intBitsToFloat(testInt);
      fail |= test(testInt, testFloat);
    } while (++testInt != 0);
    if (fail) {
      throw new RuntimeException("Test failed");
    }
  }
}
