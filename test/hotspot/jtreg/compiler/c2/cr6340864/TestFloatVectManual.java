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
 * @run main/manual/othervm -Xbatch -XX:CompileCommand=exclude,*::test() -Xmx128m -XX:+UnlockDiagnosticVMOptions -XX:+UseSignumIntrinsic compiler.c2.cr6340864.TestFloatVectManual
 * @run main/manual/othervm -Xbatch -XX:CompileCommand=exclude,*::test() -Xmx128m -XX:+UnlockDiagnosticVMOptions -XX:+UseSignumIntrinsic -XX:MaxVectorSize=8 compiler.c2.cr6340864.TestFloatVectManual
 * @run main/manual/othervm -Xbatch -XX:CompileCommand=exclude,*::test() -Xmx128m -XX:+UnlockDiagnosticVMOptions -XX:+UseSignumIntrinsic -XX:MaxVectorSize=16 compiler.c2.cr6340864.TestFloatVectManual
 * @run main/manual/othervm -Xbatch -XX:CompileCommand=exclude,*::test() -Xmx128m -XX:+UnlockDiagnosticVMOptions -XX:+UseSignumIntrinsic -XX:MaxVectorSize=32 compiler.c2.cr6340864.TestFloatVectManual
 */

package compiler.c2.cr6340864;

public class TestFloatVectManual {
  private static final int ARRLEN = 997;
  private static final int ITERS  = 11000;
  private static final float ADD_INIT = -7500.f;

  public static void main(String args[]) {
    System.out.println("Testing Float vectors");
    int errn = test();
    if (errn > 0) {
      System.err.println("FAILED: " + errn + " errors");
      System.exit(97);
    }
    System.out.println("PASSED");
  }

  static int test() {
    int[] res = new int[ARRLEN];
    float[] input = new float[ARRLEN];

    // Initialize
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

    final int e_start = 0;
    final int e_shift = 23;
    final int e_width = 8;
    final int e_max = (1 << e_width) - 1;
    final int f_start = 0;
    final int f_max = (1 << e_shift) - 1;

    for (int fi = f_start; fi <= f_max; fi++) {
      for (int ei = e_start; ei <= e_max; ei++) {
        int bits = (ei << e_shift) + fi;
        input[ei*2] = Float.intBitsToFloat(bits);
        bits = bits | (1 << 31);
        input[ei*2+1] = Float.intBitsToFloat(bits);
      }

      // run tests
      test_round(res, input);

      // verify results
      for (int ei = e_start; ei <= e_max; ei++) {
        for (int sign = 0; sign < 2; sign++) {
          int idx = ei*2+sign;
          if (res[idx] != Math.round(input[idx])) {
            errn++;
            System.err.println("round error, input: " + input[idx] +
                               ", res: " + res[idx] + "expected: " + Math.round(input[idx]) +
                               ", input hex: " + Float.floatToIntBits(input[idx]) +
                               ", fi: " + fi + ", ei: " + ei + ", sign: " + sign);
          }
          if (errn > 100) {
            return errn;
          }
        }
      }
    }
    return errn;
  }

  static void test_round(int[] a0, float[] a1) {
    for (int i = 0; i < a0.length; i+=1) {
      a0[i] = Math.round(a1[i]);
    }
  }
}
