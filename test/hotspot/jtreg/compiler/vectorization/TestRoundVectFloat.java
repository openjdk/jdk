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
 * @bug 8279508
 * @summary Auto-vectorize Math.round API
 * @requires vm.compiler2.enabled
 * @requires vm.cpu.features ~= ".*avx.*"
 * @requires os.simpleArch == "x64"
 * @library /test/lib /
 * @run driver compiler.vectorization.TestRoundVectFloat
 */

package compiler.vectorization;

import compiler.lib.ir_framework.*;

public class TestRoundVectFloat {
  private static final int ARRLEN = 1024;
  private static final int ITERS  = 11000;
  private static float  [] finp;
  private static int    [] iout;

  public static void main(String args[]) {
      TestFramework.runWithFlags("-XX:-TieredCompilation",
                                 "-XX:UseAVX=1",
                                 "-XX:CompileThresholdScaling=0.3");
      System.out.println("PASSED");
  }

  @Test
  @IR(applyIf = {"UseAVX", " > 1"}, counts = {"RoundVF" , " > 0 "})
  public void test_round_float(int[] iout, float[] finp) {
      for (int i = 0; i < finp.length; i+=1) {
          iout[i] = Math.round(finp[i]);
      }
  }

  @Run(test = {"test_round_float"}, mode = RunMode.STANDALONE)
  public void kernel_test_round() {
      finp = new float[ARRLEN];
      iout = new int[ARRLEN];
      for(int i = 0 ; i < ARRLEN; i++) {
          finp[i] = (float)i*1.4f;
      }
      for (int i = 0; i < ITERS; i++) {
          test_round_float(iout , finp);
      }
  }
}
