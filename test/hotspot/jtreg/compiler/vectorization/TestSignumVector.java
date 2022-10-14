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

/**
 * @test
 * @bug 8282711 8290249
 * @summary Accelerate Math.signum function for AVX, AVX512 and aarch64 (Neon and SVE)
 * @requires vm.compiler2.enabled
 * @requires (os.simpleArch == "x64" & vm.cpu.features ~= ".*avx.*") | os.arch == "aarch64"
 * @library /test/lib /
 * @run driver compiler.vectorization.TestSignumVector
 */

package compiler.vectorization;

import compiler.lib.ir_framework.*;

public class TestSignumVector {
  private static final int ARRLEN = 1024;
  private static final int ITERS  = 11000;

  private static double [] dinp;
  private static double [] dout;
  private static float  [] finp;
  private static float  [] fout;

  public static void main(String args[]) {
      TestFramework.runWithFlags("-XX:-TieredCompilation", "-XX:+UnlockDiagnosticVMOptions",
                                 "-XX:+UseSignumIntrinsic", "-XX:CompileThresholdScaling=0.3");
      System.out.println("PASSED");
  }

  @Test
  @IR(counts = {"SignumVD" , " > 0 "})
  public void test_signum_double(double[] dout, double[] dinp) {
      for (int i = 0; i < dout.length; i+=1) {
          dout[i] = Math.signum(dinp[i]);
      }
  }

  @Run(test = {"test_signum_double"}, mode = RunMode.STANDALONE)
  public void kernel_test_signum_double() {
      dinp = new double[ARRLEN];
      dout = new double[ARRLEN];
      for(int i = 0 ; i < ARRLEN; i++) {
          dinp[i] = (double)i*1.4;
      }
      for (int i = 0; i < ITERS; i++) {
          test_signum_double(dout , dinp);
      }
  }

  @Test
  @IR(counts = {"SignumVF" , " > 0 "})
  public void test_signum_float(float[] fout, float[] finp) {
      for (int i = 0; i < finp.length; i+=1) {
          fout[i] = Math.signum(finp[i]);
      }
  }

  @Run(test = {"test_signum_float"}, mode = RunMode.STANDALONE)
  public void kernel_test_round() {
      finp = new float[ARRLEN];
      fout = new float[ARRLEN];
      for(int i = 0 ; i < ARRLEN; i++) {
          finp[i] = (float)i*1.4f;
      }
      for (int i = 0; i < ITERS; i++) {
          test_signum_float(fout , finp);
      }
  }
}
