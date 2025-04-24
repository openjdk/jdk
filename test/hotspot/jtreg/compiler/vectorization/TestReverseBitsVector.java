/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8290034
 * @summary Auto-vectorization of Reverse bit operation.
 * @requires vm.compiler2.enabled
 * @requires (os.simpleArch == "x64" & vm.cpu.features ~= ".*avx2.*") |
 *           os.arch == "aarch64" |
 *           (os.arch == "riscv64" & vm.cpu.features ~= ".*zbkb.*" & vm.cpu.features ~= ".*zvbb.*")
 * @library /test/lib /
 * @run driver compiler.vectorization.TestReverseBitsVector
 */

package compiler.vectorization;

import compiler.lib.ir_framework.*;
import java.util.Random;

public class TestReverseBitsVector {
  private static final int ARRLEN = 1024;
  private static final int ITERS  = 11000;

  private static long [] linp;
  private static long [] lout;
  private static int  [] iinp;
  private static int  [] iout;
  private static short [] sinp;
  private static short [] sout;
  private static char [] cinp;
  private static char [] cout;

  public static void setup() {
      Random r = new Random(1024);
      linp = new long[ARRLEN];
      lout = new long[ARRLEN];
      iinp = new int[ARRLEN];
      iout = new int[ARRLEN];
      sinp = new short[ARRLEN];
      sout = new short[ARRLEN];
      cinp = new char[ARRLEN];
      cout = new char[ARRLEN];
      for(int i = 0; i < ARRLEN; i++) {
          linp[i] = r.nextLong();
          iinp[i] = r.nextInt();
          sinp[i] = (short)r.nextInt();
          cinp[i] = (char)r.nextInt();
      }
  }

  public static void main(String args[]) {
      setup();
      TestFramework.runWithFlags("-XX:-TieredCompilation");
      System.out.println("PASSED");
  }

  @Test
  @IR(counts = {IRNode.REVERSE_VL, "> 0"})
  public void test_reverse_long1(long[] lout, long[] linp) {
      for (int i = 0; i < lout.length; i+=1) {
          lout[i] = Long.reverse(linp[i]);
      }
  }

  @Run(test = {"test_reverse_long1"}, mode = RunMode.STANDALONE)
  public void kernel_test_reverse_long1() {
      setup();
      for (int i = 0; i < ITERS; i++) {
          test_reverse_long1(lout , linp);
      }
  }

  @Test
  @IR(failOn = {IRNode.REVERSE_VL, IRNode.REVERSE_L})
  public void test_reverse_long2(long[] lout, long[] linp) {
      for (int i = 0; i < lout.length; i+=1) {
          lout[i] = Long.reverse(Long.reverse(linp[i]));
      }
  }

  @Run(test = {"test_reverse_long2"}, mode = RunMode.STANDALONE)
  public void kernel_test_reverse_long2() {
      setup();
      for (int i = 0; i < ITERS; i++) {
          test_reverse_long2(lout , linp);
      }
  }

  @Test
  @IR(failOn = {IRNode.REVERSE_VL, IRNode.REVERSE_L})
  public void test_reverse_long3(long[] lout, long[] linp) {
      for (int i = 0; i < lout.length; i+=1) {
          lout[i] = Long.reverse(linp[i] ^ linp[i]);
      }
  }

  @Run(test = {"test_reverse_long3"}, mode = RunMode.STANDALONE)
  public void kernel_test_reverse_long3() {
      setup();
      for (int i = 0; i < ITERS; i++) {
          test_reverse_long3(lout , linp);
      }
  }

  @Test
  @IR(counts = {IRNode.REVERSE_VI, "> 0"})
  public void test_reverse_int1(int[] iout, int[] iinp) {
      for (int i = 0; i < iout.length; i+=1) {
          iout[i] = Integer.reverse(iinp[i]);
      }
  }

  @Run(test = {"test_reverse_int1"}, mode = RunMode.STANDALONE)
  public void kernel_test_reverse_int1() {
      setup();
      for (int i = 0; i < ITERS; i++) {
          test_reverse_int1(iout , iinp);
      }
  }

  @Test
  @IR(failOn = {IRNode.REVERSE_VI, IRNode.REVERSE_I})
  public void test_reverse_int2(int[] iout, int[] iinp) {
      for (int i = 0; i < iout.length; i+=1) {
          iout[i] = Integer.reverse(Integer.reverse(iinp[i]));
      }
  }

  @Run(test = {"test_reverse_int2"}, mode = RunMode.STANDALONE)
  public void kernel_test_reverse_int2() {
      setup();
      for (int i = 0; i < ITERS; i++) {
          test_reverse_int2(iout , iinp);
      }
  }

  @Test
  @IR(failOn = {IRNode.REVERSE_VI, IRNode.REVERSE_I})
  public void test_reverse_int3(int[] iout, int[] iinp) {
      for (int i = 0; i < iout.length; i+=1) {
          iout[i] = Integer.reverse(iinp[i] ^ iinp[i]);
      }
  }

  @Run(test = {"test_reverse_int3"}, mode = RunMode.STANDALONE)
  public void kernel_test_reverse_int3() {
      setup();
      for (int i = 0; i < ITERS; i++) {
          test_reverse_int3(iout , iinp);
      }
  }
}
