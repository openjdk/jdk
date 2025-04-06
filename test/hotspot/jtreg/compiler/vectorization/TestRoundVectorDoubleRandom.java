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
 * @bug 8321011
 * @summary Test vector intrinsic for Math.round(double) with random input in 64 bits range, verify IR at the same time.
 *
 * @library /test/lib /
 * @modules java.base/jdk.internal.math
 * @requires os.arch == "riscv64" & vm.cpu.features ~= ".*rvv.*"
 * @run main compiler.vectorization.TestRoundVectorDoubleRandom
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

public class TestRoundVectorDoubleRandom {
  private static final Random rand = new Random();

  private static final int ITERS  = 11000;
  private static final int ARRLEN = rand.nextInt(4096-997) + 997;
  private static final double ADD_INIT = -7500.;

  private static final double[] input = new double[ARRLEN];
  private static final long [] res = new long[ARRLEN];

  public static void main(String args[]) {
    TestFramework.runWithFlags("-XX:-TieredCompilation", "-XX:CompileThresholdScaling=0.3");
    TestFramework.runWithFlags("-XX:-TieredCompilation", "-XX:CompileThresholdScaling=0.3", "-XX:MaxVectorSize=8");
    TestFramework.runWithFlags("-XX:-TieredCompilation", "-XX:CompileThresholdScaling=0.3", "-XX:MaxVectorSize=16");
    TestFramework.runWithFlags("-XX:-TieredCompilation", "-XX:CompileThresholdScaling=0.3", "-XX:MaxVectorSize=32");
  }

  @Test
  @IR(counts = {IRNode.ROUND_VD, "> 0"},
      applyIf = {"MaxVectorSize", ">= 64"})
  static void test_round(long[] a0, double[] a1) {
    for (int i = 0; i < a0.length; i+=1) {
      a0[i] = Math.round(a1[i]);
    }
  }

  @Run(test = "test_round")
  @Warmup(ITERS)
  static void test_rounds(RunInfo runInfo) {
    // Initialize
    for (int i = 0; i < ARRLEN; i++) {
      double val = ADD_INIT+(double)i;
      input[i] = val;
    }

    test_round(res, input);
    // skip test/verify when warming up
    if (runInfo.isWarmUp()) {
      return;
    }

    int errn = 0;
    // a double precise float point is composed of 3 parts: sign/exponent/signicand
    // exponent part of a float value
    final int exponentShift = 52;
    final int exponentWidth = 11;
    final int exponentBound = 1 << exponentWidth;
    // significant part of a float value
    final int signicandWidth = exponentShift;
    final long signicandBound = 1L << signicandWidth;
    final int signicandNum = 256;

    // prepare for data of significand part
    long signicandValues[] = new long[signicandNum];
    int signicandIdx = 0;
    for (; signicandIdx < signicandWidth; signicandIdx++) {
      signicandValues[signicandIdx] = 1L << signicandIdx;
    }
    for (; signicandIdx < signicandNum; signicandIdx++) {
      signicandValues[signicandIdx] = rand.nextLong(signicandBound);
    }
    signicandValues[rand.nextInt(signicandNum)] = 0;

    // generate input arrays for testing, then run tests & verify results

    // generate input arrays by combining different parts
    for (long sv : signicandValues) {
      // generate test input by combining different parts:
      //   previously generated significand values,
      //   random value in exponent range,
      //   both positive and negative of previous combined values (exponent+significand)
      final int exponentStart = rand.nextInt(9);
      final int exponentStep = (1 << 3) + rand.nextInt(3);
      // Here, we could have iterated the whole range of exponent values, but it would
      // take more time to run the test, so just randomly choose some of exponent values.
      int ev = exponentStart;
      int inputIdx = 0;
      for (; ev < exponentBound; ev += exponentStep) {
        inputIdx = ev/exponentStep;
        // combine exponent and significand
        long bits = ((long)ev << exponentShift) + sv;
        // combine sign(+/-) with exponent and significand
        // positive values
        input[inputIdx*2] = Double.longBitsToDouble(bits);
        // negative values
        bits = bits | (1L << 63);
        input[inputIdx*2+1] = Double.longBitsToDouble(bits);
      }
      // add specific test cases where it looks like in binary format:
      //   s111 1111 1111 xxxx xxxx xxxx xxxx xxxx ...
      // these are for the NaN and Inf.
      inputIdx = inputIdx*2+2;
      long bits = (1L << exponentWidth) - 1L;
      bits <<= exponentShift;
      input[inputIdx++] = Double.longBitsToDouble(bits);
      bits = bits | (1L << 63);
      input[inputIdx] = Double.longBitsToDouble(bits);

      // run tests
      test_round(res, input);

      // verify results
      ev = exponentStart;
      inputIdx = ev/exponentStep;
      for (; ev < exponentBound; ev += exponentStep) {
        for (int sign = 0; sign < 2; sign++) {
          int idx = inputIdx * 2 + sign;
          if (res[idx] != golden_round(input[idx])) {
            errn++;
            System.err.println("round error, input: " + input[idx] +
                               ", res: " + res[idx] + "expected: " + golden_round(input[idx]) +
                               ", input hex: " + Double.doubleToLongBits(input[idx]) +
                               ", fi: " + sv + ", ei: " + ev + ", sign: " + sign);
          }
        }
      }
    }

    // generate pure random input arrays, which does not depend on significand/exponent values
    for(int i = 0; i < 128; i++) {
      for (int j = 0; j < ARRLEN; j++) {
        input[j] = rand.nextDouble();
      }

      // run tests
      test_round(res, input);

      // verify results
      for (int j = 0; j < ARRLEN; j++) {
        if (res[j] != golden_round(input[j])) {
          errn++;
          System.err.println("round error, input: " + input[j] +
                             ", res: " + res[j] + "expected: " + golden_round(input[j]) +
                             ", input hex: " + Double.doubleToLongBits(input[j]));
        }
      }
    }

    // test cases for NaN, Inf, subnormal, and so on
    {
      Double[] dv = new Double[] {
        +0.0,
        -0.0,
        Double.MAX_VALUE,
        Double.MIN_VALUE,
        Double.NEGATIVE_INFINITY,
        Double.POSITIVE_INFINITY,
        Double.NaN,
        Double.longBitsToDouble(0x7ff0000000000001L), // another NaN
        Double.MIN_NORMAL,
        0x0.fffffffffffffp-1022,   // Maximum Subnormal Value
        1.5,
        100.5,
        10000.5,
        -1.5,
        -100.5,
        -10000.5
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
                             ", input hex: " + Double.doubleToLongBits(input[j]));
        }
      }
    }

    if (errn > 0) {
      throw new RuntimeException("There are some round error detected!");
    }
  }
}
