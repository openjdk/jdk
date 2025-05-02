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
 * @summary Test vector intrinsic for Math.round(float) in full 32 bits range
 *
 * @library /test/lib /
 * @modules java.base/jdk.internal.math
 * @requires os.arch == "riscv64" & vm.cpu.features ~= ".*rvv.*"
 * @run main/othervm -XX:-TieredCompilation -XX:CompileThresholdScaling=0.3 -XX:+UseSuperWord -XX:CompileCommand=compileonly,compiler.vectorization.TestRoundVectorFloatAll::test* compiler.vectorization.TestRoundVectorFloatAll
 * @run main/othervm -XX:-TieredCompilation -XX:CompileThresholdScaling=0.3 -XX:MaxVectorSize=32 -XX:+UseSuperWord -XX:CompileCommand=compileonly,compiler.vectorization.TestRoundVectorFloatAll::test* compiler.vectorization.TestRoundVectorFloatAll
 */

package compiler.vectorization;

import java.util.Random;
import static compiler.lib.golden.GoldenRound.golden_round;

public class TestRoundVectorFloatAll {
  private static final Random rand = new Random();

  private static final int ITERS  = 11000;
  private static final int ARRLEN = rand.nextInt(4096-997) + 997;
  private static final float ADD_INIT = -7500.f;

  public static void main(String args[]) {
    test();
  }

  static void test() {
    float[] input = new float[ARRLEN];
    int [] res = new int[ARRLEN];

    // Warmup
    System.out.println("Warmup");
    for (int i=0; i<ITERS; i++) {
      test_round(res, input);
    }

    // Test and verify results
    System.out.println("Verification");
    int errn = 0;
    for (long l = Integer.MIN_VALUE; l <= Integer.MAX_VALUE; l+=ARRLEN) {
      for (int i = 0; i < ARRLEN; i++) {
        input[i] = Float.intBitsToFloat((int)(i+l));
      }

      // run tests
      test_round(res, input);

      // verify results
      for (int j = 0; j < ARRLEN; j++) {
        if (res[j] != golden_round(input[j])) {
          errn++;
          System.err.println("round error, input: " + input[j] +
                             ", res: " + res[j] + "expected: " + golden_round(input[j]) +
                             ", input hex: " + Float.floatToIntBits(input[j]));
        }
      }
    }

    if (errn > 0) {
      throw new RuntimeException("There are some round error detected!");
    }
  }

  static void test_round(int[] a0, float[] a1) {
    for (int i = 0; i < a0.length; i+=1) {
      a0[i] = Math.round(a1[i]);
    }
  }
}
