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
 * @bug 8321011
 * @summary Test vector intrinsic for Math.round(double) in full 64 bits range.
 *          This is an extension of test cases in TestDoubleVect
 *
 * @run main/manual/othervm -Xbatch -XX:CompileCommand=exclude,*::test() -Xmx128m -XX:+UnlockDiagnosticVMOptions -XX:+UseSignumIntrinsic compiler.c2.cr6340864.TestDoubleVectManual
 * @run main/manual/othervm -Xbatch -XX:CompileCommand=exclude,*::test() -Xmx128m -XX:+UnlockDiagnosticVMOptions -XX:+UseSignumIntrinsic -XX:MaxVectorSize=8 compiler.c2.cr6340864.TestDoubleVectManual
 * @run main/manual/othervm -Xbatch -XX:CompileCommand=exclude,*::test() -Xmx128m -XX:+UnlockDiagnosticVMOptions -XX:+UseSignumIntrinsic -XX:MaxVectorSize=16 compiler.c2.cr6340864.TestDoubleVectManual
 * @run main/manual/othervm -Xbatch -XX:CompileCommand=exclude,*::test() -Xmx128m -XX:+UnlockDiagnosticVMOptions -XX:+UseSignumIntrinsic -XX:MaxVectorSize=32 compiler.c2.cr6340864.TestDoubleVectManual
 */

package compiler.c2.cr6340864;

public class TestDoubleVectManual {
  private static final int ARRLEN = 997;
  private static final int ITERS  = 11000;
  private static final double ADD_INIT = -7500.;

  public static void main(String args[]) {
    System.out.println("Testing Double vectors");
    int errn = test();
    if (errn > 0) {
      System.err.println("FAILED: " + errn + " errors");
      System.exit(97);
    }
    System.out.println("PASSED");
  }

  static int test() {
    double[] input = new double[ARRLEN];
    long  [] res = new long[ARRLEN];

    double[] a1 = new double[ARRLEN];
    // Initialize
    for (int i=0; i<ARRLEN; i++) {
      double val = ADD_INIT+(double)i;
      a1[i] = val;
    }

    // Warmup
    System.out.println("Warmup");
    for (int i=0; i<ITERS; i++) {
      test_round(res, a1);
    }

    // Test and verify results
    System.out.println("Verification");
    int errn = 0;

    final int e_start = 0;
    final int e_shift = 52;
    final int e_width = 11;
    final int e_max = (1 << e_width) - 1;
    final long f_start = 0;
    final long f_max = (1 << e_shift) - 1;
    final int group = 448;

    for (long fi = f_start; fi <= f_max; fi++) {
      for (int eg = e_start; eg <= e_max; eg+=group) {
        for (int ei = 0; ei < group; ei++) {
          int e = eg + ei;
          long bits = (e << e_shift) + fi;
          input[ei*2] = Double.longBitsToDouble(bits);
          bits = bits | (1 << 31);
          input[ei*2+1] = Double.longBitsToDouble(bits);
        }

        // run tests
        test_round(res, input);

        // verify results
        for (int ei = 0; ei <= group; ei++) {
          int e = eg + ei;
          for (int sign = 0; sign < 2; sign++) {
            int idx = ei*2+sign;
            if (res[idx] != Math.round(input[idx])) {
              errn++;
              System.err.println("round error, input: " + input[idx] +
                                ", res: " + res[idx] + "expected: " + Math.round(input[idx]) +
                                ", input hex: " + Double.doubleToLongBits(input[idx]) +
                                ", fi: " + fi + ", ei: " + ei + ", e: " + e + ", sign: " + sign);
            }
            if (errn > 100) {
              return errn;
            }
          }
        }
      }
    }
    return errn;
  }

  static void test_round(long[] a0, double[] a1) {
    for (int i = 0; i < a0.length; i+=1) {
      a0[i] = Math.round(a1[i]);
    }
  }
}
