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
 * @bug 8318221
 * @summary Test ReverseL intrinsic
 *
 * @library /test/lib /
 * @requires os.arch == "riscv64" & vm.cpu.features ~= ".*zbkb.*"
 * @run main/othervm compiler.c2.riscv64.TestLongReverse
 */

package compiler.c2.riscv64;

import compiler.lib.ir_framework.*;
import java.util.Random;
import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;

import static compiler.lib.golden.GoldenReverse.golden_reverse_long;

public class TestLongReverse {

  static Random r = Utils.getRandomInstance();
  static final int ITERS  = 11000;
  static final int ARRLEN = 997;
  static long input[] = new long[ARRLEN];
  static long output[] = new long[ARRLEN];

  public static void main(String args[]) {
    TestFramework.runWithFlags("-XX:-TieredCompilation", "-XX:CompileThresholdScaling=0.3",
                               "-XX:+UnlockDiagnosticVMOptions", "-XX:-UseRVV"); // Only test scalar version
  }

  @Test
  @IR(counts = {IRNode.REVERSE_L, "> 0"})
  static void test_reverse(long[] input, long[] output) {
    for (int i = 0; i < input.length; i+=1) {
      output[i] = Long.reverse(input[i]);
    }
  }

  @Run(test = "test_reverse")
  @Warmup(ITERS)
  static void test(RunInfo runInfo) {
    // Initialize
    for (int i = 0; i < ARRLEN; i++) {
      input[i] = r.nextLong();
    }
    input[0] = 0L;
    input[1] = 1L;
    input[2] = -1L;
    input[3] = Long.MIN_VALUE;
    input[4] = Long.MAX_VALUE;

    test_reverse(input, output);
    // skip test/verify when warming up
    if (runInfo.isWarmUp()) {
      return;
    }

    test_reverse(input, output);

    for (int i = 0; i < ARRLEN; i++) {
      long golden_val = golden_reverse_long(input[i]);
      Asserts.assertEquals(output[i], golden_val,
                          "Test failure, input: " + input[i] +
                          ", actual: " + output[i] +
                          ", expected: " + golden_val);
    }
  }
}
