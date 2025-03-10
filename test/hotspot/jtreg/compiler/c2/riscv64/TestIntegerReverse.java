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
  static int output[] = new int[ARRLEN];
  static boolean err;

  public static void main(String args[]) {
    TestFramework.runWithFlags("-XX:-TieredCompilation", "-XX:CompileThresholdScaling=0.3",
                               "-XX:+UnlockDiagnosticVMOptions", "-XX:-UseRVV"); // Only test scalar version
  }

  @Test
  @IR(counts = {IRNode.REVERSE_I, "> 0"})
  static void test_reverse(int[] input, int[] output) {
    for (int i = 0; i < input.length; i+=1) {
      output[i] = Integer.reverse(input[i]);
    }
  }

  @Run(test = "test_reverse")
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

    test_reverse(input, output);
    // skip test/verify when warming up
    if (runInfo.isWarmUp()) {
      return;
    }

    test_reverse(input, output);

    for (int i = 0; i < ARRLEN; i++) {
      int golden_val = golden_reverse_integer(input[i]);
      Asserts.assertEquals(output[i], golden_val,
                          "Test failure, input: " + input[i] +
                          ", actual: " + output[i] +
                          ", expected: " + golden_reverse_integer(input[i]));
    }
  }
}
