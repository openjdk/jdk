/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @run main compiler.vectorization.TestRoundVectorFloatRandom -XX:-TieredCompilation -XX:CompileThresholdScaling=0.3
 * @run main compiler.vectorization.TestRoundVectorFloatRandom -XX:-TieredCompilation -XX:CompileThresholdScaling=0.3 -XX:MaxVectorSize=8
 * @run main compiler.vectorization.TestRoundVectorFloatRandom -XX:-TieredCompilation -XX:CompileThresholdScaling=0.3 -XX:MaxVectorSize=16
 * @run main compiler.vectorization.TestRoundVectorFloatRandom -XX:-TieredCompilation -XX:CompileThresholdScaling=0.3 -XX:MaxVectorSize=32
 */

package compiler.vectorization;

import jdk.internal.math.FloatConsts;

public class TestRoundVectorFloatAll {
  private static final int ITERS  = 11000;
  private static final int ARRLEN = 997;
  private static final float ADD_INIT = -7500.f;

  public static void main(String args[]) {
    test();
  }

  static int golden_round(float a) {
    // below code is copied from java.base/share/classes/java/lang/Math.java
    //  public static int round(float a) { ... }

    int intBits = Float.floatToRawIntBits(a);
    int biasedExp = (intBits & FloatConsts.EXP_BIT_MASK)
            >> (FloatConsts.SIGNIFICAND_WIDTH - 1);
    int shift = (FloatConsts.SIGNIFICAND_WIDTH - 2
            + FloatConsts.EXP_BIAS) - biasedExp;
    if ((shift & -32) == 0) { // shift >= 0 && shift < 32
        // a is a finite number such that pow(2,-32) <= ulp(a) < 1
        int r = ((intBits & FloatConsts.SIGNIF_BIT_MASK)
                | (FloatConsts.SIGNIF_BIT_MASK + 1));
        if (intBits < 0) {
            r = -r;
        }
        // In the comments below each Java expression evaluates to the value
        // the corresponding mathematical expression:
        // (r) evaluates to a / ulp(a)
        // (r >> shift) evaluates to floor(a * 2)
        // ((r >> shift) + 1) evaluates to floor((a + 1/2) * 2)
        // (((r >> shift) + 1) >> 1) evaluates to floor(a + 1/2)
        return ((r >> shift) + 1) >> 1;
    } else {
        // a is either
        // - a finite number with abs(a) < exp(2,FloatConsts.SIGNIFICAND_WIDTH-32) < 1/2
        // - a finite number with ulp(a) >= 1 and hence a is a mathematical integer
        // - an infinity or NaN
        return (int) a;
    }
  }

  static int test() {
    float[] input = new float[ARRLEN];
    int [] res = new int[ARRLEN];

    for (int i=0; i<ARRLEN; i++) {
      float val = ADD_INIT+(float)i;
      input[i] = val;
    }
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
        input[i] = (int)(l+i);
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
