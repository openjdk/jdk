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
 * @key randomness
 * @bug 8321010
 * @summary Test vector intrinsic for Math.round(float) with random input in 32 bits range, verify IR at the same time.
 *
 * @library /test/lib /
 * @modules java.base/jdk.internal.math
 * @requires os.arch == "riscv64" & vm.cpu.features ~= ".*rvv.*"
 * @run main compiler.vectorization.TestRoundVectorFloatRandom
 */

package compiler.vectorization;

import java.util.Random;
import static compiler.lib.golden.GoldenRound.golden_round;
import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.IRNode;
import compiler.lib.ir_framework.Run;
import compiler.lib.ir_framework.RunInfo;
import compiler.lib.ir_framework.Test;
import compiler.lib.ir_framework.TestFramework;
import compiler.lib.ir_framework.Warmup;

public class TestRoundVectorFloatRandom {
  private static final Random rand = new Random();

  private static final int ITERS  = 11000;
  private static final int ARRLEN = rand.nextInt(4096-997) + 997;
  private static final float ADD_INIT = -7500.f;

  private static final float[] input = new float[ARRLEN];
  private static final int[] res = new int[ARRLEN];

  public static void main(String args[]) {
    TestFramework.runWithFlags("-XX:-TieredCompilation", "-XX:CompileThresholdScaling=0.3");
    TestFramework.runWithFlags("-XX:-TieredCompilation", "-XX:CompileThresholdScaling=0.3", "-XX:MaxVectorSize=8");
    TestFramework.runWithFlags("-XX:-TieredCompilation", "-XX:CompileThresholdScaling=0.3", "-XX:MaxVectorSize=16");
    TestFramework.runWithFlags("-XX:-TieredCompilation", "-XX:CompileThresholdScaling=0.3", "-XX:MaxVectorSize=32");
  }

  @Test
  @IR(counts = {IRNode.ROUND_VF, "> 0"},
      applyIf = {"MaxVectorSize", ">= 32"})
  static void test_round(int[] a0, float[] a1) {
    for (int i = 0; i < a0.length; i+=1) {
      a0[i] = Math.round(a1[i]);
    }
  }

  @Run(test = "test_round")
  @Warmup(ITERS)
  static void test_rounds(RunInfo runInfo) {
    // Initialize
    for (int i = 0; i < ARRLEN; i++) {
      float val = ADD_INIT+(float)i;
      input[i] = val;
    }

    test_round(res, input);
    // skip test/verify when warming up
    if (runInfo.isWarmUp()) {
      return;
    }

    int errn = 0;
    // a single precise float point is composed of 3 parts: sign/exponent/signicand
    // exponent part of a float value
    final int exponentStart = 0;
    final int exponentShift = 23;
    final int exponentWidth = 8;
    final int exponentBound = 1 << exponentWidth;
    // significant part of a float value
    final int signicandWidth = exponentShift;
    final int signicandBound = 1 << signicandWidth;
    final int signicandNum = 128;

    // prepare for data of significand part
    int signicandValues[] = new int[signicandNum];
    int signicandIdx = 0;
    for (; signicandIdx < signicandWidth; signicandIdx++) {
      signicandValues[signicandIdx] = 1 << signicandIdx;
    }
    for (; signicandIdx < signicandNum; signicandIdx++) {
      signicandValues[signicandIdx] = rand.nextInt(signicandBound);
    }
    signicandValues[rand.nextInt(signicandNum)] = 0;

    // generate input arrays for testing, then run tests & verify results

    // generate input arrays by combining different parts
    for (int sv : signicandValues) {
      // generate test input by combining different parts:
      //   previously generated significand values,
      //   all values in exponent range,
      //   both positive and negative of previous combined values (exponent+significand)
      for (int ev = exponentStart; ev < exponentBound; ev++) {
        // combine exponent and significand
        int bits = (ev << exponentShift) + sv;
        // combine sign(+/-) with exponent and significand
        // positive values
        input[ev*2] = Float.intBitsToFloat(bits);
        // negative values
        bits = bits | (1 << 31);
        input[ev*2+1] = Float.intBitsToFloat(bits);
      }

      // run tests
      test_round(res, input);

      // verify results
      for (int ev = exponentStart; ev < exponentBound; ev++) {
        for (int sign = 0; sign < 2; sign++) {
          int idx = ev * 2 + sign;
          if (res[idx] != golden_round(input[idx])) {
            errn++;
            System.err.println("round error, input: " + input[idx] +
                               ", res: " + res[idx] + "expected: " + golden_round(input[idx]) +
                               ", input hex: " + Float.floatToIntBits(input[idx]) +
                               ", fi: " + sv + ", ei: " + ev + ", sign: " + sign);
          }
        }
      }
    }

    // generate pure random input arrays, which does not depend on significand/exponent values
    for(int i = 0; i < 128; i++) {
      for (int j = 0; j < ARRLEN; j++) {
        input[j] = rand.nextFloat();
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

    // test cases for NaN, Inf, subnormal, and so on
    {
      Float[] dv = new Float[] {
        +0.0f,
        -0.0f,
        Float.MAX_VALUE,
        Float.MIN_VALUE,
        Float.NEGATIVE_INFINITY,
        Float.POSITIVE_INFINITY,
        Float.NaN,
        Float.intBitsToFloat(0x7f800001), // another NaN
        Float.MIN_NORMAL,
        0x0.fffffep-126f,   // Maximum Subnormal Value
        1.5f,
        100.5f,
        10000.5f,
        -1.5f,
        -100.5f,
        -10000.5f
      };
      for (int j = 0; j < ARRLEN; j++) {
        input[j] = dv[rand.nextInt(dv.length)];
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
}
