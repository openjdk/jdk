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
 * @bug 8372153
 * @summary AArch64: Performance regression in long reduction microbenchmarks after JDK-8340093
 * @requires vm.compiler2.enabled
 * @modules jdk.incubator.vector
 * @library /test/lib /
 * @run driver compiler.vectorization.TestVmlaAArch64
 */

package compiler.vectorization;

import compiler.lib.ir_framework.*;

import jdk.incubator.vector.LongVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

import jdk.test.lib.Platform;

public class TestVmlaAArch64 {
  private static final int ARRLEN = 1024;
  private static final int ITERS  = 11000;
  private static final VectorSpecies<Long> SPECIES_128 = LongVector.SPECIES_128;

  private static long[] a;
  private static long[] b;
  private static long[] c;
  private static VectorMask<Long> mask;
  private static long lres;

  public static void main(String args[]) {
      if (Platform.isAArch64()) {
          TestFramework.runWithFlags("--add-modules=jdk.incubator.vector", "-XX:-AvoidMLAChain");
          TestFramework.runWithFlags("--add-modules=jdk.incubator.vector", "-XX:+AvoidMLAChain");
      } else {
          TestFramework.runWithFlags("--add-modules=jdk.incubator.vector");
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
      for (int i = 0; i < ARRLEN; i++) {
          a[i] = i;
          b[i] = i;
      }
      for (int i = 0; i < ITERS; i++) {
          lres = vector_add_dot_product();
      }
  }

  @Test
  @IR(applyIfCPUFeature = {"sve", "true"},
      applyIfAnd = {"MaxVectorSize", "<= 16", "AvoidMLAChain", "true"},
      counts = {IRNode.VMLS, "=0"})
  @IR(applyIfCPUFeature = {"sve", "true"},
      applyIf = {"AvoidMLAChain", "false"},
      counts = {IRNode.VMLS, ">0"})
  public long vector_sub_dot_product() {
      long res = 0L;
      for (int i = 0; i < a.length; i++) {
          long val = (a[i] * b[i]) + (a[i] * c[i]) - (b[i] * c[i]);
          res += val;
      }
      return res;
  }

  @Run(test = {"vector_sub_dot_product"}, mode = RunMode.STANDALONE)
  public void test_vector_sub_dot_product() {
      a = new long[ARRLEN];
      b = new long[ARRLEN];
      c = new long[ARRLEN];
      for (int i = 0; i < ARRLEN; i++) {
          a[i] = i;
          b[i] = i;
          c[i] = i;
      }
      for (int i = 0; i < ITERS; i++) {
          lres = vector_sub_dot_product();
      }
  }

  @Test
  @IR(applyIfCPUFeature = {"sve", "true"},
      applyIfAnd = {"MaxVectorSize", "<= 16", "AvoidMLAChain", "true"},
      // The peeled first iteration generates one VMLA, but the main loop should not.
      counts = {IRNode.VMLA, "=1"})
  @IR(applyIfCPUFeature = {"sve", "true"},
      applyIf = {"AvoidMLAChain", "false"},
      counts = {IRNode.VMLA, ">0"})
  public long vector_api_add_dot_product() {
      LongVector acc = LongVector.zero(SPECIES_128);
      for (int i = 0; i < SPECIES_128.loopBound(a.length); i += SPECIES_128.length()) {
          LongVector av = LongVector.fromArray(SPECIES_128, a, i);
          LongVector bv = LongVector.fromArray(SPECIES_128, b, i);
          acc = acc.add(av.mul(bv));
      }
      return acc.reduceLanes(VectorOperators.ADD);
  }

  @Run(test = {"vector_api_add_dot_product"}, mode = RunMode.STANDALONE)
  public void test_vector_api_add_dot_product() {
      a = new long[ARRLEN];
      b = new long[ARRLEN];
      for (int i = 0; i < ARRLEN; i++) {
          a[i] = i;
          b[i] = i;
      }
      for (int i = 0; i < ITERS; i++) {
          lres = vector_api_add_dot_product();
      }
  }

  @Test
  @IR(applyIfCPUFeature = {"sve", "true"},
      applyIfAnd = {"MaxVectorSize", "<= 16", "AvoidMLAChain", "true"},
      // The peeled first iteration generates one VMLS, but the main loop should not.
      counts = {IRNode.VMLS, "=1"})
  @IR(applyIfCPUFeature = {"sve", "true"},
      applyIf = {"AvoidMLAChain", "false"},
      counts = {IRNode.VMLS, ">0"})
  public long vector_api_sub_dot_product() {
      LongVector acc = LongVector.zero(SPECIES_128);
      for (int i = 0; i < SPECIES_128.loopBound(a.length); i += SPECIES_128.length()) {
          LongVector av = LongVector.fromArray(SPECIES_128, a, i);
          LongVector bv = LongVector.fromArray(SPECIES_128, b, i);
          acc = acc.sub(av.mul(bv));
      }
      return acc.reduceLanes(VectorOperators.ADD);
  }

  @Run(test = {"vector_api_sub_dot_product"}, mode = RunMode.STANDALONE)
  public void test_vector_api_sub_dot_product() {
      a = new long[ARRLEN];
      b = new long[ARRLEN];
      for (int i = 0; i < ARRLEN; i++) {
          a[i] = i;
          b[i] = i;
      }
      for (int i = 0; i < ITERS; i++) {
          lres = vector_api_sub_dot_product();
      }
  }

  @Test
  @IR(applyIfCPUFeature = {"sve", "true"},
      applyIfAnd = {"MaxVectorSize", "<= 16", "AvoidMLAChain", "true"},
      // The peeled first iteration generates one VMLA, but the main loop should not.
      counts = {IRNode.VMLA_MASKED, "=1"})
  @IR(applyIfCPUFeature = {"sve", "true"},
      applyIf = {"AvoidMLAChain", "false"},
      counts = {IRNode.VMLA_MASKED, ">0"})
  public long vector_api_add_dot_product_masked() {
      LongVector acc = LongVector.zero(SPECIES_128);
      for (int i = 0; i < SPECIES_128.loopBound(a.length); i += SPECIES_128.length()) {
          LongVector av = LongVector.fromArray(SPECIES_128, a, i);
          LongVector bv = LongVector.fromArray(SPECIES_128, b, i);
          acc = acc.add(av.mul(bv), mask);
      }
      return acc.reduceLanes(VectorOperators.ADD);
  }

  @Run(test = {"vector_api_add_dot_product_masked"}, mode = RunMode.STANDALONE)
  public void test_vector_api_add_dot_product_masked() {
      a = new long[ARRLEN];
      b = new long[ARRLEN];
      mask = VectorMask.fromLong(SPECIES_128, 1);
      for (int i = 0; i < ARRLEN; i++) {
          a[i] = i;
          b[i] = i;
      }
      for (int i = 0; i < ITERS; i++) {
          lres = vector_api_add_dot_product_masked();
      }
  }

  @Test
  @IR(applyIfCPUFeature = {"sve", "true"},
      applyIfAnd = {"MaxVectorSize", "<= 16", "AvoidMLAChain", "true"},
      // The peeled first iteration generates one VMLS, but the main loop should not.
      counts = {IRNode.VMLS_MASKED, "=1"})
  @IR(applyIfCPUFeature = {"sve", "true"},
      applyIf = {"AvoidMLAChain", "false"},
      counts = {IRNode.VMLS_MASKED, ">0"})
  public long vector_api_sub_dot_product_masked() {
      LongVector acc = LongVector.zero(SPECIES_128);
      for (int i = 0; i < SPECIES_128.loopBound(a.length); i += SPECIES_128.length()) {
          LongVector av = LongVector.fromArray(SPECIES_128, a, i);
          LongVector bv = LongVector.fromArray(SPECIES_128, b, i);
          acc = acc.sub(av.mul(bv), mask);
      }
      return acc.reduceLanes(VectorOperators.ADD);
  }

  @Run(test = {"vector_api_sub_dot_product_masked"}, mode = RunMode.STANDALONE)
  public void test_vector_api_sub_dot_product_masked() {
      a = new long[ARRLEN];
      b = new long[ARRLEN];
      mask = VectorMask.fromLong(SPECIES_128, 1);
      for (int i = 0; i < ARRLEN; i++) {
          a[i] = i;
          b[i] = i;
      }
      for (int i = 0; i < ITERS; i++) {
          lres = vector_api_sub_dot_product_masked();
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
      for (int i = 0; i < ARRLEN; i++) {
          a[i] = i;
          b[i] = i;
          c[i] = i;
      }
      for (int i = 0; i < ITERS; i++) {
          lres = vector_mul_add_shared();
      }
  }

  @Test
  @IR(applyIfCPUFeature = {"sve", "true"},
      counts = {IRNode.VMLS, ">0"})
  public long vector_mul_sub_shared() {
      long res = 0L;
      for (int i = 0; i < a.length; i++) {
          long val = a[i] * b[i];
          res += val - val * c[i];
      }
      return res;
  }

  @Run(test = {"vector_mul_sub_shared"}, mode = RunMode.STANDALONE)
  public void test_vector_mul_sub_shared() {
      a = new long[ARRLEN];
      b = new long[ARRLEN];
      c = new long[ARRLEN];
      for (int i = 0; i < ARRLEN; i++) {
          a[i] = i;
          b[i] = i;
          c[i] = i;
      }
      for (int i = 0; i < ITERS; i++) {
          lres = vector_mul_sub_shared();
      }
  }

  @Test
  @IR(applyIfCPUFeature = {"sve", "true"},
      applyIfAnd = {"MaxVectorSize", "<= 16", "AvoidMLAChain", "true"},
      counts = {IRNode.VMLA, "=0"})
  @IR(applyIfCPUFeature = {"sve", "true"},
      applyIf = {"AvoidMLAChain", "false"},
      counts = {IRNode.VMLA, ">0"})
  public long if_else_phi_add() {
      LongVector acc = LongVector.zero(SPECIES_128);
      for (int i = 0; i < SPECIES_128.loopBound(a.length); i += SPECIES_128.length()) {
          LongVector av = LongVector.fromArray(SPECIES_128, a, i);
          LongVector bv = LongVector.fromArray(SPECIES_128, b, i);
          LongVector cv = LongVector.fromArray(SPECIES_128, c, i);
          LongVector selected;
          if ((i & SPECIES_128.length()) == 0) {
              selected = av.mul(bv);
          } else {
              selected = bv.mul(cv);
          }
          acc = acc.add(selected.add(av.mul(cv)));
      }
      return acc.reduceLanes(VectorOperators.ADD);
  }

  @Run(test = {"if_else_phi_add"}, mode = RunMode.STANDALONE)
  public void test_if_else_phi_add() {
      a = new long[ARRLEN];
      b = new long[ARRLEN];
      c = new long[ARRLEN];
      for (int i = 0; i < ARRLEN; i++) {
          a[i] = i;
          b[i] = i;
          c[i] = i;
      }
      for (int i = 0; i < ITERS; i++) {
          lres = if_else_phi_add();
      }
  }

  @Test
  @IR(applyIfCPUFeature = {"sve", "true"},
      applyIfAnd = {"MaxVectorSize", "<= 16", "AvoidMLAChain", "true"},
      counts = {IRNode.VMLS, "=0"})
  @IR(applyIfCPUFeature = {"sve", "true"},
      applyIf = {"AvoidMLAChain", "false"},
      counts = {IRNode.VMLS, ">0"})
  public long if_else_phi_sub() {
      LongVector acc = LongVector.zero(SPECIES_128);
      for (int i = 0; i < SPECIES_128.loopBound(a.length); i += SPECIES_128.length()) {
          LongVector av = LongVector.fromArray(SPECIES_128, a, i);
          LongVector bv = LongVector.fromArray(SPECIES_128, b, i);
          LongVector cv = LongVector.fromArray(SPECIES_128, c, i);
          LongVector selected;
          if ((i & SPECIES_128.length()) == 0) {
              selected = av.mul(bv);
          } else {
              selected = bv.mul(cv);
          }
          acc = acc.add(selected.sub(av.mul(cv)));
      }
      return acc.reduceLanes(VectorOperators.ADD);
  }

  @Run(test = {"if_else_phi_sub"}, mode = RunMode.STANDALONE)
  public void test_if_else_phi_sub() {
      a = new long[ARRLEN];
      b = new long[ARRLEN];
      c = new long[ARRLEN];
      for (int i = 0; i < ARRLEN; i++) {
          a[i] = i;
          b[i] = i;
          c[i] = i;
      }
      for (int i = 0; i < ITERS; i++) {
          lres = if_else_phi_sub();
      }
  }
}
