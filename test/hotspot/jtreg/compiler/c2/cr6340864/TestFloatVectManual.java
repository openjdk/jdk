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
 *          This is an extension of test cases in TestFloatVect
 *
 * @requires vm.compiler2.enabled
 * @requires (vm.cpu.features ~= ".*avx.*" & os.simpleArch == "x64") |
 *           os.simpleArch == "aarch64"
 *
 * @library /test/lib /
 * @run driver compiler.c2.cr6340864.TestFloatVectManual
 */

package compiler.c2.cr6340864;

import java.util.Random;
import compiler.lib.ir_framework.DontCompile;
import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.IRNode;
import compiler.lib.ir_framework.Run;
import compiler.lib.ir_framework.RunInfo;
import compiler.lib.ir_framework.Test;
import compiler.lib.ir_framework.TestFramework;
import compiler.lib.ir_framework.Warmup;

public class TestFloatVectManual {
  private static final int ITERS  = 11000;
  private static final int ARRLEN = 997;
  private static final float ADD_INIT = -7500.f;

  private static final float[] input = new float[ARRLEN];
  private static final int[] res = new int[ARRLEN];
  private static final Random rand = new Random();

  public static void main(String args[]) {
    TestFramework.runWithFlags("-XX:-TieredCompilation", "-XX:CompileThresholdScaling=0.3");
    TestFramework.runWithFlags("-XX:-TieredCompilation", "-XX:CompileThresholdScaling=0.3", "-XX:MaxVectorSize=8");
    TestFramework.runWithFlags("-XX:-TieredCompilation", "-XX:CompileThresholdScaling=0.3", "-XX:MaxVectorSize=16");
    TestFramework.runWithFlags("-XX:-TieredCompilation", "-XX:CompileThresholdScaling=0.3", "-XX:MaxVectorSize=32");
  }

  @DontCompile
  int golden_round(float f) {
    return Math.round(f);
  }

  @Test
  @IR(counts = {IRNode.ROUND_VF, "> 0"})
  void test_round(int[] a0, float[] a1) {
    for (int i = 0; i < a0.length; i+=1) {
      a0[i] = Math.round(a1[i]);
    }
  }

  @Run(test = "test_round")
  @Warmup(ITERS)
  void test_rounds(RunInfo runInfo) {
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
    final int e_start = 0;
    final int e_shift = 23;
    final int e_width = 8;
    final int e_bound = 1 << e_width;
    final int f_width = e_shift;
    final int f_bound = 1 << f_width;
    final int f_num = 128;

    // prepare test data
    int fis[] = new int[f_num];
    int fidx = 0;
    for (; fidx < f_width; fidx++) {
      fis[fidx] = 1 << fidx;
    }
    fis[fidx++] = 0;
    for (; fidx < f_num; fidx++) {
      fis[fidx] = rand.nextInt(f_bound);
    }

    // run test & verify
    for (int fi : fis) {
      for (int ei = e_start; ei < e_bound; ei++) {
        int bits = (ei << e_shift) + fi;
        input[ei*2] = Float.intBitsToFloat(bits);
        bits = bits | (1 << 31);
        input[ei*2+1] = Float.intBitsToFloat(bits);
      }

      // test
      test_round(res, input);

      // verify results
      for (int ei = e_start; ei < e_bound; ei++) {
        for (int sign = 0; sign < 2; sign++) {
          int idx = ei * 2 + sign;
          if (res[idx] != Math.round(input[idx])) {
            errn++;
            System.err.println("round error, input: " + input[idx] +
                               ", res: " + res[idx] + "expected: " + Math.round(input[idx]) +
                               ", input hex: " + Float.floatToIntBits(input[idx]) +
                               ", fi: " + fi + ", ei: " + ei + ", sign: " + sign);
          }
        }
      }
    }
    if (errn > 0) {
      throw new RuntimeException("There are some round error detected!");
    }
  }
}
