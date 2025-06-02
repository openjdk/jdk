/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2025, Rivos Inc. All rights reserved.
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
 * @bug 8318220
 * @summary Test ReverseI intrinsic
 *
 * @library /test/lib /
 * @requires os.arch == "riscv64" & vm.cpu.features ~= ".*zbkb.*"
 * @run main/othervm compiler.c2.riscv64.TestIntegerReverse
 */

package compiler.c2.riscv64;

import compiler.lib.ir_framework.*;
import java.util.Random;
import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;

import static compiler.lib.golden.GoldenReverse.golden_reverse_integer;

public class TestIntegerReverse {

  static Random r = Utils.getRandomInstance();
  static final int ITERS  = 11000;
  static final int ARRLEN = 997;
  static int input[] = new int[ARRLEN];
  static int outputI[] = new int[ARRLEN];
  static long outputL[] = new long[ARRLEN];
  static int err;

  public static void main(String args[]) {
    TestFramework.runWithFlags("-XX:-TieredCompilation", "-XX:CompileThresholdScaling=0.3",
                               "-XX:+UnlockDiagnosticVMOptions", "-XX:-UseRVV"); // Only test scalar version
  }

  @Test
  @IR(counts = {IRNode.REVERSE_I, "> 0"})
  static void test_reverse_ia(int[] input, int[] outputI) {
    for (int i = 0; i < input.length; i+=1) {
      outputI[i] = Integer.reverse(input[i]);
    }
  }

  @Test
  @IR(counts = {IRNode.REVERSE_I, "> 0"})
  static void test_reverse_la(int[] input, long[] outputL) {
    for (int i = 0; i < input.length; i+=1) {
      outputL[i] = Integer.reverse(input[i]);
    }
  }

  @Test
  @IR(counts = {IRNode.REVERSE_I, "> 0"})
  static void test_reverse_l(int input, long expected) {
    if (Integer.reverse(input) != expected) {
      err++;
      System.out.println("Test failure, input: " + input +
                         ", actual: " + Integer.reverse(input) +
                         ", expected: " + expected);
    }
  }

  @Run(test = {"test_reverse_ia", "test_reverse_la", "test_reverse_l"})
  @Warmup(ITERS)
  static void test(RunInfo runInfo) {
    // Initialize
    for (int i = 0; i < ARRLEN; i++) {
      input[i] = r.nextInt();
    }
    input[0] = 0;
    input[1] = 1;
    input[2] = -1;
    input[3] = Integer.MIN_VALUE;
    input[4] = Integer.MAX_VALUE;

    test_reverse_ia(input, outputI);
    test_reverse_la(input, outputL);
    for (int i = 0; i < ARRLEN; i++) {
      test_reverse_l(input[i], golden_reverse_integer(input[i]));
    }
    // skip test/verify when warming up
    if (runInfo.isWarmUp()) {
      return;
    }

    test_reverse_ia(input, outputI);
    test_reverse_la(input, outputL);

    for (int i = 0; i < ARRLEN; i++) {
      int golden_val = golden_reverse_integer(input[i]);
      Asserts.assertEquals(outputI[i], golden_val,
                          "Test failure (integer array), input: " + input[i] +
                          ", actual: " + outputI[i] +
                          ", expected: " + golden_val);
      Asserts.assertEquals(outputL[i], (long)golden_val,
                          "Test failure (long array), input: " + input[i] +
                          ", actual: " + outputL[i] +
                          ", expected: " + (long)golden_val);
    }

    err = 0;
    for (int i = 0; i < ARRLEN; i++) {
      test_reverse_l(input[i], golden_reverse_integer(input[i]));
    }
    Asserts.assertTrue(err == 0, "Some tests(" + err + ") failed, check previous log for details");
  }
}
