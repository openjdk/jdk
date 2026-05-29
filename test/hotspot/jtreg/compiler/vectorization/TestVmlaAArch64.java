/*
 * Copyright 2026 Arm Limited and/or its affiliates.
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
 * @bug 8282541
 * @summary AArch64: Performance regression in long reduction microbenchmarks after JDK-8340093
 * @requires vm.compiler2.enabled
 * @requires os.simpleArch == "aarch64"
 * @library /test/lib /
 * @run driver compiler.vectorization.TestVmlaAArch64
 */

package compiler.vectorization;

import compiler.lib.ir_framework.*;

public class TestVmlaAArch64 {
  private static final int ARRLEN = 1024;
  private static final int ITERS  = 11000;

  private static long[] a;
  private static long[] b;
  private static long[] c;
  private static long lres;

  public static void main(String args[]) {
      if (System.getProperty("os.arch").equals("aarch64")) {
          TestFramework.runWithFlags("-XX:-AvoidMLAChain");
          TestFramework.runWithFlags("-XX:+AvoidMLAChain");
      }
      System.out.println("PASSED");
  }

  @Test
  @IR(applyIfCPUFeature = {"sve", "true"},
      applyIfAnd = {"MaxVectorSize", "<= 16", "AvoidMLAChain", "true"},
      counts = {IRNode.VMLA, "=0"})
  @IR(applyIfCPUFeature = {"sve", "true"},
      applyIf = {"AvoidMLAChain", "false"},
      counts = {IRNode.VMLA, ">0"})
  public long vector_add_dot_product() {
      long res = 0L;
      for (int i = 0; i < a.length; i++) {
          long val = a[i] * b[i];
          res += val;
      }
      return res;
  }

  @Run(test = {"vector_add_dot_product"}, mode = RunMode.STANDALONE)
  public void test_vector_add_dot_product() {
      a = new long[ARRLEN];
      b = new long[ARRLEN];
      for(int i = 0 ; i < ARRLEN; i++) {
          a[i] = i;
          b[i] = i;
      }
      for (int i = 0; i < ITERS; i++) {
          lres = vector_add_dot_product();
      }
  }

  @Test
  @IR(applyIfCPUFeature = {"sve", "true"},
      counts = {IRNode.VMLA, ">0"})
  public long vector_mul_add_shared() {
      long res = 0L;
      for (int i = 0; i < a.length; i++) {
          long val = a[i] * b[i];
          res += val + val * c[i];
      }
      return res;
  }

  @Run(test = {"vector_mul_add_shared"}, mode = RunMode.STANDALONE)
  public void test_vector_mul_add_shared() {
      a = new long[ARRLEN];
      b = new long[ARRLEN];
      c = new long[ARRLEN];
      for(int i = 0 ; i < ARRLEN; i++) {
          a[i] = i;
          b[i] = i;
          c[i] = i;
      }
      for (int i = 0; i < ITERS; i++) {
          lres = vector_mul_add_shared();
      }
  }
}
