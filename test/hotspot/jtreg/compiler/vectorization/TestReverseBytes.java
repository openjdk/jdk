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
 * @bug 8288112
 * @summary Auto-vectorization of ReverseBytes operations.
 * @requires vm.compiler2.enabled
 * @requires (os.simpleArch == "x64" & vm.cpu.features ~= ".*avx2.*") | os.simpleArch == "AArch64" |
 *           (os.simpleArch == "riscv64" & vm.cpu.features ~= ".*zvbb.*")
 * @library /test/lib /
 * @run driver compiler.vectorization.TestReverseBytes
 */

package compiler.vectorization;

import compiler.lib.ir_framework.*;
import java.util.Random;

public class TestReverseBytes {
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
      TestFramework.runWithFlags("-XX:-TieredCompilation",
                                  "-XX:CompileThresholdScaling=0.3");
      System.out.println("PASSED");
  }

  @Test
  @IR(counts = {IRNode.REVERSE_BYTES_VL, "> 0"})
  public void test_reverse_bytes_long(long[] lout, long[] linp) {
      for (int i = 0; i < lout.length; i+=1) {
          lout[i] = Long.reverseBytes(linp[i]);
      }
  }

  @Run(test = {"test_reverse_bytes_long"}, mode = RunMode.STANDALONE)
  public void kernel_test_reverse_bytes_long() {
      setup();
      for (int i = 0; i < ITERS; i++) {
          test_reverse_bytes_long(lout , linp);
      }
  }

  @Test
  @IR(counts = {IRNode.REVERSE_BYTES_VI, "> 0"})
  public void test_reverse_bytes_int(int[] iout, int[] iinp) {
      for (int i = 0; i < iout.length; i+=1) {
          iout[i] = Integer.reverseBytes(iinp[i]);
      }
  }

  @Run(test = {"test_reverse_bytes_int"}, mode = RunMode.STANDALONE)
  public void kernel_test_reverse_bytes_int() {
      setup();
      for (int i = 0; i < ITERS; i++) {
          test_reverse_bytes_int(iout , iinp);
      }
  }

  @Test
  @IR(counts = {IRNode.REVERSE_BYTES_VS, "> 0"})
  public void test_reverse_bytes_short(short[] sout, short[] sinp) {
      for (int i = 0; i < sout.length; i+=1) {
          sout[i] = Short.reverseBytes(sinp[i]);
      }
  }

  @Run(test = {"test_reverse_bytes_short"}, mode = RunMode.STANDALONE)
  public void kernel_test_reverse_bytes_short() {
      setup();
      for (int i = 0; i < ITERS; i++) {
          test_reverse_bytes_short(sout , sinp);
      }
  }

  @Test
  @IR(counts = {IRNode.REVERSE_BYTES_VS, "> 0"})
  public void test_reverse_bytes_char(char[] cout, char[] cinp) {
      for (int i = 0; i < cout.length; i+=1) {
          cout[i] = Character.reverseBytes(cinp[i]);
      }
  }

  @Run(test = {"test_reverse_bytes_char"}, mode = RunMode.STANDALONE)
  public void kernel_test_reverse_bytes_char() {
      setup();
      for (int i = 0; i < ITERS; i++) {
          test_reverse_bytes_char(cout , cinp);
      }
  }
}
