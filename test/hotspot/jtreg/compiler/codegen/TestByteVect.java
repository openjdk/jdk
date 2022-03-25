/*
 * Copyright (c) 2012, 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 7119644
 * @summary Increase superword's vector size up to 256 bits
 *
 * @run main/othervm/timeout=300 -Xbatch -XX:+IgnoreUnrecognizedVMOptions
 *    -XX:-TieredCompilation -XX:-OptimizeFill
 *    compiler.codegen.TestByteVect
 */

package compiler.codegen;

public class TestByteVect {
  private static final int ARRLEN = 997;
  private static final int ITERS  = 11000;
  private static final int OFFSET = 3;
  private static final int SCALE = 2;
  private static final int ALIGN_OFF = 8;
  private static final int UNALIGN_OFF = 5;

  public static void main(String args[]) {
    System.out.println("Testing Byte vectors");
    int errn = test();
    if (errn > 0) {
      System.err.println("FAILED: " + errn + " errors");
      System.exit(97);
    }
    System.out.println("PASSED");
  }

  static int test() {
    byte[] a1 = new byte[ARRLEN];
    byte[] a2 = new byte[ARRLEN];
    byte[] a3 = new byte[ARRLEN];
    System.out.println("Warmup");
    for (int i=0; i<ITERS; i++) {
      test_ci(a1);
      test_vi(a2, (byte)123);
      test_cp(a1, a2);
      test_2ci(a1, a2);
      test_2vi(a1, a2, (byte)123, (byte)103);
      test_ci_neg(a1);
      test_vi_neg(a2, (byte)123);
      test_cp_neg(a1, a2);
      test_2ci_neg(a1, a2);
      test_2vi_neg(a1, a2, (byte)123, (byte)103);
      test_ci_oppos(a1);
      test_vi_oppos(a2, (byte)123);
      test_cp_oppos(a1, a2);
      test_2ci_oppos(a1, a2);
      test_2vi_oppos(a1, a2, (byte)123, (byte)103);
      test_ci_off(a1);
      test_vi_off(a2, (byte)123);
      test_cp_off(a1, a2);
      test_2ci_off(a1, a2);
      test_2vi_off(a1, a2, (byte)123, (byte)103);
      test_ci_inv(a1, OFFSET);
      test_vi_inv(a2, (byte)123, OFFSET);
      test_cp_inv(a1, a2, OFFSET);
      test_2ci_inv(a1, a2, OFFSET);
      test_2vi_inv(a1, a2, (byte)123, (byte)103, OFFSET);
      test_ci_scl(a1);
      test_vi_scl(a2, (byte)123);
      test_cp_scl(a1, a2);
      test_2ci_scl(a1, a2);
      test_2vi_scl(a1, a2, (byte)123, (byte)103);
      test_cp_alndst(a1, a2);
      test_cp_alnsrc(a1, a2);
      test_2ci_aln(a1, a2);
      test_2vi_aln(a1, a2, (byte)123, (byte)103);
      test_cp_unalndst(a1, a2);
      test_cp_unalnsrc(a1, a2);
      test_2ci_unaln(a1, a2);
      test_2vi_unaln(a1, a2, (byte)123, (byte)103);
      test_addImm127(a1, a2);
      test_addImm(a1, a2, a3);
      test_addImm256(a1, a2);
      test_addImmNeg128(a1, a2);
      test_addImmNeg129(a1, a2);
      test_subImm(a1, a2, a3);
      test_andImm21(a1, a2);
      test_andImm7(a1, a2);
      test_orImm(a1, a2);
      test_xorImm(a1, a2, a3);
    }
    // Initialize
    for (int i=0; i<ARRLEN; i++) {
      a1[i] = -1;
      a2[i] = -1;
    }
    // Test and verify results
    System.out.println("Verification");
    int errn = 0;
    {
      test_ci(a1);
      for (int i=0; i<ARRLEN; i++) {
        errn += verify("test_ci: a1", i, a1[i], (byte)-123);
      }
      test_vi(a2, (byte)123);
      for (int i=0; i<ARRLEN; i++) {
        errn += verify("test_vi: a2", i, a2[i], (byte)123);
      }
      test_cp(a1, a2);
      for (int i=0; i<ARRLEN; i++) {
        errn += verify("test_cp: a1", i, a1[i], (byte)123);
      }
      test_2ci(a1, a2);
      for (int i=0; i<ARRLEN; i++) {
        errn += verify("test_2ci: a1", i, a1[i], (byte)-123);
        errn += verify("test_2ci: a2", i, a2[i], (byte)-103);
      }
      test_2vi(a1, a2, (byte)123, (byte)103);
      for (int i=0; i<ARRLEN; i++) {
        errn += verify("test_2vi: a1", i, a1[i], (byte)123);
        errn += verify("test_2vi: a2", i, a2[i], (byte)103);
      }
      // Reset for negative stride
      for (int i=0; i<ARRLEN; i++) {
        a1[i] = -1;
        a2[i] = -1;
      }
      test_ci_neg(a1);
      for (int i=0; i<ARRLEN; i++) {
        errn += verify("test_ci_neg: a1", i, a1[i], (byte)-123);
      }
      test_vi_neg(a2, (byte)123);
      for (int i=0; i<ARRLEN; i++) {
        errn += verify("test_vi_neg: a2", i, a2[i], (byte)123);
      }
      test_cp_neg(a1, a2);
      for (int i=0; i<ARRLEN; i++) {
        errn += verify("test_cp_neg: a1", i, a1[i], (byte)123);
      }
      test_2ci_neg(a1, a2);
      for (int i=0; i<ARRLEN; i++) {
        errn += verify("test_2ci_neg: a1", i, a1[i], (byte)-123);
        errn += verify("test_2ci_neg: a2", i, a2[i], (byte)-103);
      }
      test_2vi_neg(a1, a2, (byte)123, (byte)103);
      for (int i=0; i<ARRLEN; i++) {
        errn += verify("test_2vi_neg: a1", i, a1[i], (byte)123);
        errn += verify("test_2vi_neg: a2", i, a2[i], (byte)103);
      }
      // Reset for opposite stride
      for (int i=0; i<ARRLEN; i++) {
        a1[i] = -1;
        a2[i] = -1;
      }
      test_ci_oppos(a1);
      for (int i=0; i<ARRLEN; i++) {
        errn += verify("test_ci_oppos: a1", i, a1[i], (byte)-123);
      }
      test_vi_oppos(a2, (byte)123);
      for (int i=0; i<ARRLEN; i++) {
        errn += verify("test_vi_oppos: a2", i, a2[i], (byte)123);
      }
      test_cp_oppos(a1, a2);
      for (int i=0; i<ARRLEN; i++) {
        errn += verify("test_cp_oppos: a1", i, a1[i], (byte)123);
      }
      test_2ci_oppos(a1, a2);
      for (int i=0; i<ARRLEN; i++) {
        errn += verify("test_2ci_oppos: a1", i, a1[i], (byte)-123);
        errn += verify("test_2ci_oppos: a2", i, a2[i], (byte)-103);
      }
      test_2vi_oppos(a1, a2, (byte)123, (byte)103);
      for (int i=0; i<ARRLEN; i++) {
        errn += verify("test_2vi_oppos: a1", i, a1[i], (byte)123);
        errn += verify("test_2vi_oppos: a2", i, a2[i], (byte)103);
      }
      // Reset for indexing with offset
      for (int i=0; i<ARRLEN; i++) {
        a1[i] = -1;
        a2[i] = -1;
      }
      test_ci_off(a1);
      for (int i=OFFSET; i<ARRLEN; i++) {
        errn += verify("test_ci_off: a1", i, a1[i], (byte)-123);
      }
      test_vi_off(a2, (byte)123);
      for (int i=OFFSET; i<ARRLEN; i++) {
        errn += verify("test_vi_off: a2", i, a2[i], (byte)123);
      }
      test_cp_off(a1, a2);
      for (int i=OFFSET; i<ARRLEN; i++) {
        errn += verify("test_cp_off: a1", i, a1[i], (byte)123);
      }
      test_2ci_off(a1, a2);
      for (int i=OFFSET; i<ARRLEN; i++) {
        errn += verify("test_2ci_off: a1", i, a1[i], (byte)-123);
        errn += verify("test_2ci_off: a2", i, a2[i], (byte)-103);
      }
      test_2vi_off(a1, a2, (byte)123, (byte)103);
      for (int i=OFFSET; i<ARRLEN; i++) {
        errn += verify("test_2vi_off: a1", i, a1[i], (byte)123);
        errn += verify("test_2vi_off: a2", i, a2[i], (byte)103);
      }
      for (int i=0; i<OFFSET; i++) {
        errn += verify("test_2vi_off: a1", i, a1[i], (byte)-1);
        errn += verify("test_2vi_off: a2", i, a2[i], (byte)-1);
      }
      // Reset for indexing with invariant offset
      for (int i=0; i<ARRLEN; i++) {
        a1[i] = -1;
        a2[i] = -1;
      }
      test_ci_inv(a1, OFFSET);
      for (int i=OFFSET; i<ARRLEN; i++) {
        errn += verify("test_ci_inv: a1", i, a1[i], (byte)-123);
      }
      test_vi_inv(a2, (byte)123, OFFSET);
      for (int i=OFFSET; i<ARRLEN; i++) {
        errn += verify("test_vi_inv: a2", i, a2[i], (byte)123);
      }
      test_cp_inv(a1, a2, OFFSET);
      for (int i=OFFSET; i<ARRLEN; i++) {
        errn += verify("test_cp_inv: a1", i, a1[i], (byte)123);
      }
      test_2ci_inv(a1, a2, OFFSET);
      for (int i=OFFSET; i<ARRLEN; i++) {
        errn += verify("test_2ci_inv: a1", i, a1[i], (byte)-123);
        errn += verify("test_2ci_inv: a2", i, a2[i], (byte)-103);
      }
      test_2vi_inv(a1, a2, (byte)123, (byte)103, OFFSET);
      for (int i=OFFSET; i<ARRLEN; i++) {
        errn += verify("test_2vi_inv: a1", i, a1[i], (byte)123);
        errn += verify("test_2vi_inv: a2", i, a2[i], (byte)103);
      }
      for (int i=0; i<OFFSET; i++) {
        errn += verify("test_2vi_inv: a1", i, a1[i], (byte)-1);
        errn += verify("test_2vi_inv: a2", i, a2[i], (byte)-1);
      }
      // Reset for indexing with scale
      for (int i=0; i<ARRLEN; i++) {
        a1[i] = -1;
        a2[i] = -1;
      }
      test_ci_scl(a1);
      for (int i=0; i<ARRLEN; i++) {
        int val = (i%SCALE != 0) ? -1 : -123;
        errn += verify("test_ci_scl: a1", i, a1[i], (byte)val);
      }
      test_vi_scl(a2, (byte)123);
      for (int i=0; i<ARRLEN; i++) {
        int val = (i%SCALE != 0) ? -1 : 123;
        errn += verify("test_vi_scl: a2", i, a2[i], (byte)val);
      }
      test_cp_scl(a1, a2);
      for (int i=0; i<ARRLEN; i++) {
        int val = (i%SCALE != 0) ? -1 : 123;
        errn += verify("test_cp_scl: a1", i, a1[i], (byte)val);
      }
      test_2ci_scl(a1, a2);
      for (int i=0; i<ARRLEN; i++) {
        if (i%SCALE != 0) {
          errn += verify("test_2ci_scl: a1", i, a1[i], (byte)-1);
        } else if (i*SCALE < ARRLEN) {
          errn += verify("test_2ci_scl: a1", i*SCALE, a1[i*SCALE], (byte)-123);
        }
        if (i%SCALE != 0) {
          errn += verify("test_2ci_scl: a2", i, a2[i], (byte)-1);
        } else if (i*SCALE < ARRLEN) {
          errn += verify("test_2ci_scl: a2", i*SCALE, a2[i*SCALE], (byte)-103);
        }
      }
      test_2vi_scl(a1, a2, (byte)123, (byte)103);
      for (int i=0; i<ARRLEN; i++) {
        if (i%SCALE != 0) {
          errn += verify("test_2vi_scl: a1", i, a1[i], (byte)-1);
        } else if (i*SCALE < ARRLEN) {
          errn += verify("test_2vi_scl: a1", i*SCALE, a1[i*SCALE], (byte)123);
        }
        if (i%SCALE != 0) {
          errn += verify("test_2vi_scl: a2", i, a2[i], (byte)-1);
        } else if (i*SCALE < ARRLEN) {
          errn += verify("test_2vi_scl: a2", i*SCALE, a2[i*SCALE], (byte)103);
        }
      }
      // Reset for 2 arrays with relative aligned offset
      for (int i=0; i<ARRLEN; i++) {
        a1[i] = -1;
        a2[i] = -1;
      }
      test_vi(a2, (byte)123);
      test_cp_alndst(a1, a2);
      for (int i=0; i<ALIGN_OFF; i++) {
        errn += verify("test_cp_alndst: a1", i, a1[i], (byte)-1);
      }
      for (int i=ALIGN_OFF; i<ARRLEN; i++) {
        errn += verify("test_cp_alndst: a1", i, a1[i], (byte)123);
      }
      test_vi(a2, (byte)-123);
      test_cp_alnsrc(a1, a2);
      for (int i=0; i<ARRLEN-ALIGN_OFF; i++) {
        errn += verify("test_cp_alnsrc: a1", i, a1[i], (byte)-123);
      }
      for (int i=ARRLEN-ALIGN_OFF; i<ARRLEN; i++) {
        errn += verify("test_cp_alnsrc: a1", i, a1[i], (byte)123);
      }
      for (int i=0; i<ARRLEN; i++) {
        a1[i] = -1;
        a2[i] = -1;
      }
      test_2ci_aln(a1, a2);
      for (int i=0; i<ALIGN_OFF; i++) {
        errn += verify("test_2ci_aln: a1", i, a1[i], (byte)-1);
      }
      for (int i=ALIGN_OFF; i<ARRLEN; i++) {
        errn += verify("test_2ci_aln: a1", i, a1[i], (byte)-123);
      }
      for (int i=0; i<ARRLEN-ALIGN_OFF; i++) {
        errn += verify("test_2ci_aln: a2", i, a2[i], (byte)-103);
      }
      for (int i=ARRLEN-ALIGN_OFF; i<ARRLEN; i++) {
        errn += verify("test_2ci_aln: a2", i, a2[i], (byte)-1);
      }
      for (int i=0; i<ARRLEN; i++) {
        a1[i] = -1;
        a2[i] = -1;
      }
      test_2vi_aln(a1, a2, (byte)123, (byte)103);
      for (int i=0; i<ARRLEN-ALIGN_OFF; i++) {
        errn += verify("test_2vi_aln: a1", i, a1[i], (byte)123);
      }
      for (int i=ARRLEN-ALIGN_OFF; i<ARRLEN; i++) {
        errn += verify("test_2vi_aln: a1", i, a1[i], (byte)-1);
      }
      for (int i=0; i<ALIGN_OFF; i++) {
        errn += verify("test_2vi_aln: a2", i, a2[i], (byte)-1);
      }
      for (int i=ALIGN_OFF; i<ARRLEN; i++) {
        errn += verify("test_2vi_aln: a2", i, a2[i], (byte)103);
      }

      // Reset for 2 arrays with relative unaligned offset
      for (int i=0; i<ARRLEN; i++) {
        a1[i] = -1;
        a2[i] = -1;
      }
      test_vi(a2, (byte)123);
      test_cp_unalndst(a1, a2);
      for (int i=0; i<UNALIGN_OFF; i++) {
        errn += verify("test_cp_unalndst: a1", i, a1[i], (byte)-1);
      }
      for (int i=UNALIGN_OFF; i<ARRLEN; i++) {
        errn += verify("test_cp_unalndst: a1", i, a1[i], (byte)123);
      }
      test_vi(a2, (byte)-123);
      test_cp_unalnsrc(a1, a2);
      for (int i=0; i<ARRLEN-UNALIGN_OFF; i++) {
        errn += verify("test_cp_unalnsrc: a1", i, a1[i], (byte)-123);
      }
      for (int i=ARRLEN-UNALIGN_OFF; i<ARRLEN; i++) {
        errn += verify("test_cp_unalnsrc: a1", i, a1[i], (byte)123);
      }
      for (int i=0; i<ARRLEN; i++) {
        a1[i] = -1;
        a2[i] = -1;
      }
      test_2ci_unaln(a1, a2);
      for (int i=0; i<UNALIGN_OFF; i++) {
        errn += verify("test_2ci_unaln: a1", i, a1[i], (byte)-1);
      }
      for (int i=UNALIGN_OFF; i<ARRLEN; i++) {
        errn += verify("test_2ci_unaln: a1", i, a1[i], (byte)-123);
      }
      for (int i=0; i<ARRLEN-UNALIGN_OFF; i++) {
        errn += verify("test_2ci_unaln: a2", i, a2[i], (byte)-103);
      }
      for (int i=ARRLEN-UNALIGN_OFF; i<ARRLEN; i++) {
        errn += verify("test_2ci_unaln: a2", i, a2[i], (byte)-1);
      }
      for (int i=0; i<ARRLEN; i++) {
        a1[i] = -1;
        a2[i] = -1;
      }
      test_2vi_unaln(a1, a2, (byte)123, (byte)103);
      for (int i=0; i<ARRLEN-UNALIGN_OFF; i++) {
        errn += verify("test_2vi_unaln: a1", i, a1[i], (byte)123);
      }
      for (int i=ARRLEN-UNALIGN_OFF; i<ARRLEN; i++) {
        errn += verify("test_2vi_unaln: a1", i, a1[i], (byte)-1);
      }
      for (int i=0; i<UNALIGN_OFF; i++) {
        errn += verify("test_2vi_unaln: a2", i, a2[i], (byte)-1);
      }
      for (int i=UNALIGN_OFF; i<ARRLEN; i++) {
        errn += verify("test_2vi_unaln: a2", i, a2[i], (byte)103);
      }

      // Reset for aligned overlap initialization
      for (int i=0; i<ALIGN_OFF; i++) {
        a1[i] = (byte)i;
      }
      for (int i=ALIGN_OFF; i<ARRLEN; i++) {
        a1[i] = -1;
      }
      test_cp_alndst(a1, a1);
      for (int i=0; i<ARRLEN; i++) {
        int v = i%ALIGN_OFF;
        errn += verify("test_cp_alndst_overlap: a1", i, a1[i], (byte)v);
      }
      for (int i=0; i<ALIGN_OFF; i++) {
        a1[i+ALIGN_OFF] = -1;
      }
      test_cp_alnsrc(a1, a1);
      for (int i=0; i<ALIGN_OFF; i++) {
        errn += verify("test_cp_alnsrc_overlap: a1", i, a1[i], (byte)-1);
      }
      for (int i=ALIGN_OFF; i<ARRLEN; i++) {
        int v = i%ALIGN_OFF;
        errn += verify("test_cp_alnsrc_overlap: a1", i, a1[i], (byte)v);
      }
      for (int i=0; i<ARRLEN; i++) {
        a1[i] = -1;
      }
      test_2ci_aln(a1, a1);
      for (int i=0; i<ARRLEN-ALIGN_OFF; i++) {
        errn += verify("test_2ci_aln_overlap: a1", i, a1[i], (byte)-103);
      }
      for (int i=ARRLEN-ALIGN_OFF; i<ARRLEN; i++) {
        errn += verify("test_2ci_aln_overlap: a1", i, a1[i], (byte)-123);
      }
      for (int i=0; i<ARRLEN; i++) {
        a1[i] = -1;
      }
      test_2vi_aln(a1, a1, (byte)123, (byte)103);
      for (int i=0; i<ARRLEN-ALIGN_OFF; i++) {
        errn += verify("test_2vi_aln_overlap: a1", i, a1[i], (byte)123);
      }
      for (int i=ARRLEN-ALIGN_OFF; i<ARRLEN; i++) {
        errn += verify("test_2vi_aln_overlap: a1", i, a1[i], (byte)103);
      }

      // Reset for unaligned overlap initialization
      for (int i=0; i<UNALIGN_OFF; i++) {
        a1[i] = (byte)i;
      }
      for (int i=UNALIGN_OFF; i<ARRLEN; i++) {
        a1[i] = -1;
      }
      test_cp_unalndst(a1, a1);
      for (int i=0; i<ARRLEN; i++) {
        int v = i%UNALIGN_OFF;
        errn += verify("test_cp_unalndst_overlap: a1", i, a1[i], (byte)v);
      }
      for (int i=0; i<UNALIGN_OFF; i++) {
        a1[i+UNALIGN_OFF] = -1;
      }
      test_cp_unalnsrc(a1, a1);
      for (int i=0; i<UNALIGN_OFF; i++) {
        errn += verify("test_cp_unalnsrc_overlap: a1", i, a1[i], (byte)-1);
      }
      for (int i=UNALIGN_OFF; i<ARRLEN; i++) {
        int v = i%UNALIGN_OFF;
        errn += verify("test_cp_unalnsrc_overlap: a1", i, a1[i], (byte)v);
      }
      for (int i=0; i<ARRLEN; i++) {
        a1[i] = -1;
      }
      test_2ci_unaln(a1, a1);
      for (int i=0; i<ARRLEN-UNALIGN_OFF; i++) {
        errn += verify("test_2ci_unaln_overlap: a1", i, a1[i], (byte)-103);
      }
      for (int i=ARRLEN-UNALIGN_OFF; i<ARRLEN; i++) {
        errn += verify("test_2ci_unaln_overlap: a1", i, a1[i], (byte)-123);
      }
      for (int i=0; i<ARRLEN; i++) {
        a1[i] = -1;
      }
      test_2vi_unaln(a1, a1, (byte)123, (byte)103);
      for (int i=0; i<ARRLEN-UNALIGN_OFF; i++) {
        errn += verify("test_2vi_unaln_overlap: a1", i, a1[i], (byte)123);
      }
      for (int i=ARRLEN-UNALIGN_OFF; i<ARRLEN; i++) {
        errn += verify("test_2vi_unaln_overlap: a1", i, a1[i], (byte)103);
      }
      byte base = (byte) 10;
      for (int i = 0; i < ARRLEN; i++) {
        a1[i] = (byte) 10;
      }
      byte golden = (byte)(base + 127);
      test_addImm127(a1, a2);
      for (int i=0; i<ARRLEN; i++) {
        errn += verify("test_addImm127: a2", i, a2[i], golden);
      }
      test_addImm(a1, a2, a3);
      golden = (byte)(base + 8);
      for (int i=0; i<ARRLEN; i++) {
        errn += verify("test_addImm: a2", i, a2[i], golden);
      }
      golden = (byte) (base + 255);
      for (int i=0; i<ARRLEN; i++) {
        errn += verify("test_addImm: a3", i, a3[i], golden);
      }
      test_addImm256(a1, a2);
      golden = (byte)(base + 256);
      for (int i=0; i<ARRLEN; i++) {
        errn += verify("test_addImm256: a3", i, a2[i], golden);
      }
      test_addImmNeg128(a1, a2);
      golden = (byte)(base + (-128));
      for (int i=0; i<ARRLEN; i++) {
        errn += verify("test_addImmNeg128: a2", i, a2[i], golden);
      }
      test_addImmNeg129(a1, a2);
      golden = (byte)(base + (-129));
      for (int i=0; i<ARRLEN; i++) {
        errn += verify("test_addImmNeg129: a2", i, a2[i], golden);
      }
      // Reset for sub test
      base = (byte) 120;
      for (int i = 0; i < ARRLEN; i++) {
        a1[i] = (byte) 120;
      }
      test_subImm(a1, a2, a3);
      golden = (byte) (base - 56);
      for (int i=0; i<ARRLEN; i++) {
        errn += verify("test_subImm: a2", i, a2[i], golden);
      }
      golden = (byte) (base - 256);
      for (int i=0; i<ARRLEN; i++) {
        errn += verify("test_subImm: a3", i, a3[i], golden);
      }
      test_andImm21(a1, a2);
      golden = (byte) (base & 21);
      for (int i=0; i<ARRLEN; i++) {
        errn += verify("test_andImm21: a2", i, a2[i], golden);
      }
      test_andImm7(a1, a2);
      golden = (byte) (base & 7);
      for (int i=0; i<ARRLEN; i++) {
        errn += verify("test_andImm7: a2", i, a2[i], golden);
      }
      test_orImm(a1, a2);
      golden = (byte) (base | 3);
      for (int i=0; i<ARRLEN; i++) {
        errn += verify("test_orImm: a2", i, a2[i], golden);
      }
      test_xorImm(a1, a2, a3);
      golden = (byte) (base ^ 127);
      for (int i=0; i<ARRLEN; i++) {
        errn += verify("test_xorImm: a2", i, a2[i], golden);
      }
      golden = (byte) (base ^ 255);
      for (int i=0; i<ARRLEN; i++) {
        errn += verify("test_xorImm: a3", i, a3[i], golden);
      }

    }

    if (errn > 0)
      return errn;

    System.out.println("Time");
    long start, end;
    start = System.currentTimeMillis();
    for (int i=0; i<ITERS; i++) {
      test_ci(a1);
    }
    end = System.currentTimeMillis();
    System.out.println("test_ci: " + (end - start));
    start = System.currentTimeMillis();
    for (int i=0; i<ITERS; i++) {
      test_vi(a2, (byte)123);
    }
    end = System.currentTimeMillis();
    System.out.println("test_vi: " + (end - start));
    start = System.currentTimeMillis();
    for (int i=0; i<ITERS; i++) {
      test_cp(a1, a2);
    }
    end = System.currentTimeMillis();
    System.out.println("test_cp: " + (end - start));
    start = System.currentTimeMillis();
    for (int i=0; i<ITERS; i++) {
      test_2ci(a1, a2);
    }
    end = System.currentTimeMillis();
    System.out.println("test_2ci: " + (end - start));
    start = System.currentTimeMillis();
    for (int i=0; i<ITERS; i++) {
      test_2vi(a1, a2, (byte)123, (byte)103);
    }
    end = System.currentTimeMillis();
    System.out.println("test_2vi: " + (end - start));

    start = System.currentTimeMillis();
    for (int i=0; i<ITERS; i++) {
      test_ci_neg(a1);
    }
    end = System.currentTimeMillis();
    System.out.println("test_ci_neg: " + (end - start));
    start = System.currentTimeMillis();
    for (int i=0; i<ITERS; i++) {
      test_vi_neg(a2, (byte)123);
    }
    end = System.currentTimeMillis();
    System.out.println("test_vi_neg: " + (end - start));
    start = System.currentTimeMillis();
    for (int i=0; i<ITERS; i++) {
      test_cp_neg(a1, a2);
    }
    end = System.currentTimeMillis();
    System.out.println("test_cp_neg: " + (end - start));
    start = System.currentTimeMillis();
    for (int i=0; i<ITERS; i++) {
      test_2ci_neg(a1, a2);
    }
    end = System.currentTimeMillis();
    System.out.println("test_2ci_neg: " + (end - start));
    start = System.currentTimeMillis();
    for (int i=0; i<ITERS; i++) {
      test_2vi_neg(a1, a2, (byte)123, (byte)103);
    }
    end = System.currentTimeMillis();
    System.out.println("test_2vi_neg: " + (end - start));

    start = System.currentTimeMillis();
    for (int i=0; i<ITERS; i++) {
      test_ci_oppos(a1);
    }
    end = System.currentTimeMillis();
    System.out.println("test_ci_oppos: " + (end - start));
    start = System.currentTimeMillis();
    for (int i=0; i<ITERS; i++) {
      test_vi_oppos(a2, (byte)123);
    }
    end = System.currentTimeMillis();
    System.out.println("test_vi_oppos: " + (end - start));
    start = System.currentTimeMillis();
    for (int i=0; i<ITERS; i++) {
      test_cp_oppos(a1, a2);
    }
    end = System.currentTimeMillis();
    System.out.println("test_cp_oppos: " + (end - start));
    start = System.currentTimeMillis();
    for (int i=0; i<ITERS; i++) {
      test_2ci_oppos(a1, a2);
    }
    end = System.currentTimeMillis();
    System.out.println("test_2ci_oppos: " + (end - start));
    start = System.currentTimeMillis();
    for (int i=0; i<ITERS; i++) {
      test_2vi_oppos(a1, a2, (byte)123, (byte)103);
    }
    end = System.currentTimeMillis();
    System.out.println("test_2vi_oppos: " + (end - start));

    start = System.currentTimeMillis();
    for (int i=0; i<ITERS; i++) {
      test_ci_off(a1);
    }
    end = System.currentTimeMillis();
    System.out.println("test_ci_off: " + (end - start));
    start = System.currentTimeMillis();
    for (int i=0; i<ITERS; i++) {
      test_vi_off(a2, (byte)123);
    }
    end = System.currentTimeMillis();
    System.out.println("test_vi_off: " + (end - start));
    start = System.currentTimeMillis();
    for (int i=0; i<ITERS; i++) {
      test_cp_off(a1, a2);
    }
    end = System.currentTimeMillis();
    System.out.println("test_cp_off: " + (end - start));
    start = System.currentTimeMillis();
    for (int i=0; i<ITERS; i++) {
      test_2ci_off(a1, a2);
    }
    end = System.currentTimeMillis();
    System.out.println("test_2ci_off: " + (end - start));
    start = System.currentTimeMillis();
    for (int i=0; i<ITERS; i++) {
      test_2vi_off(a1, a2, (byte)123, (byte)103);
    }
    end = System.currentTimeMillis();
    System.out.println("test_2vi_off: " + (end - start));

    start = System.currentTimeMillis();
    for (int i=0; i<ITERS; i++) {
      test_ci_inv(a1, OFFSET);
    }
    end = System.currentTimeMillis();
    System.out.println("test_ci_inv: " + (end - start));
    start = System.currentTimeMillis();
    for (int i=0; i<ITERS; i++) {
      test_vi_inv(a2, (byte)123, OFFSET);
    }
    end = System.currentTimeMillis();
    System.out.println("test_vi_inv: " + (end - start));
    start = System.currentTimeMillis();
    for (int i=0; i<ITERS; i++) {
      test_cp_inv(a1, a2, OFFSET);
    }
    end = System.currentTimeMillis();
    System.out.println("test_cp_inv: " + (end - start));
    start = System.currentTimeMillis();
    for (int i=0; i<ITERS; i++) {
      test_2ci_inv(a1, a2, OFFSET);
    }
    end = System.currentTimeMillis();
    System.out.println("test_2ci_inv: " + (end - start));
    start = System.currentTimeMillis();
    for (int i=0; i<ITERS; i++) {
      test_2vi_inv(a1, a2, (byte)123, (byte)103, OFFSET);
    }
    end = System.currentTimeMillis();
    System.out.println("test_2vi_inv: " + (end - start));

    start = System.currentTimeMillis();
    for (int i=0; i<ITERS; i++) {
      test_ci_scl(a1);
    }
    end = System.currentTimeMillis();
    System.out.println("test_ci_scl: " + (end - start));
    start = System.currentTimeMillis();
    for (int i=0; i<ITERS; i++) {
      test_vi_scl(a2, (byte)123);
    }
    end = System.currentTimeMillis();
    System.out.println("test_vi_scl: " + (end - start));
    start = System.currentTimeMillis();
    for (int i=0; i<ITERS; i++) {
      test_cp_scl(a1, a2);
    }
    end = System.currentTimeMillis();
    System.out.println("test_cp_scl: " + (end - start));
    start = System.currentTimeMillis();
    for (int i=0; i<ITERS; i++) {
      test_2ci_scl(a1, a2);
    }
    end = System.currentTimeMillis();
    System.out.println("test_2ci_scl: " + (end - start));
    start = System.currentTimeMillis();
    for (int i=0; i<ITERS; i++) {
      test_2vi_scl(a1, a2, (byte)123, (byte)103);
    }
    end = System.currentTimeMillis();
    System.out.println("test_2vi_scl: " + (end - start));

    start = System.currentTimeMillis();
    for (int i=0; i<ITERS; i++) {
      test_cp_alndst(a1, a2);
    }
    end = System.currentTimeMillis();
    System.out.println("test_cp_alndst: " + (end - start));
    start = System.currentTimeMillis();
    for (int i=0; i<ITERS; i++) {
      test_cp_alnsrc(a1, a2);
    }
    end = System.currentTimeMillis();
    System.out.println("test_cp_alnsrc: " + (end - start));
    start = System.currentTimeMillis();
    for (int i=0; i<ITERS; i++) {
      test_2ci_aln(a1, a2);
    }
    end = System.currentTimeMillis();
    System.out.println("test_2ci_aln: " + (end - start));
    start = System.currentTimeMillis();
    for (int i=0; i<ITERS; i++) {
      test_2vi_aln(a1, a2, (byte)123, (byte)103);
    }
    end = System.currentTimeMillis();
    System.out.println("test_2vi_aln: " + (end - start));

    start = System.currentTimeMillis();
    for (int i=0; i<ITERS; i++) {
      test_cp_unalndst(a1, a2);
    }
    end = System.currentTimeMillis();
    System.out.println("test_cp_unalndst: " + (end - start));
    start = System.currentTimeMillis();
    for (int i=0; i<ITERS; i++) {
      test_cp_unalnsrc(a1, a2);
    }
    end = System.currentTimeMillis();
    System.out.println("test_cp_unalnsrc: " + (end - start));
    start = System.currentTimeMillis();
    for (int i=0; i<ITERS; i++) {
      test_2ci_unaln(a1, a2);
    }
    end = System.currentTimeMillis();
    System.out.println("test_2ci_unaln: " + (end - start));
    start = System.currentTimeMillis();
    for (int i=0; i<ITERS; i++) {
      test_2vi_unaln(a1, a2, (byte)123, (byte)103);
    }
    end = System.currentTimeMillis();
    System.out.println("test_2vi_unaln: " + (end - start));
    start = System.currentTimeMillis();
    for (int i=0; i<ITERS; i++) {
      test_addImm127(a1, a2);
    }
    end = System.currentTimeMillis();
    System.out.println("test_addImm127: " + (end - start));
    start = System.currentTimeMillis();
    for (int i=0; i<ITERS; i++) {
      test_addImm(a1, a2, a3);
    }
    end = System.currentTimeMillis();
    System.out.println("test_addImm: " + (end - start));
    start = System.currentTimeMillis();
    for (int i=0; i<ITERS; i++) {
      test_addImm256(a1, a2);
    }
    end = System.currentTimeMillis();
    System.out.println("test_addImm256: " + (end - start));
    start = System.currentTimeMillis();
    for (int i=0; i<ITERS; i++) {
      test_addImmNeg128(a1, a2);
    }
    end = System.currentTimeMillis();
    System.out.println("test_addImmNeg128: " + (end - start));
    start = System.currentTimeMillis();
    for (int i=0; i<ITERS; i++) {
      test_addImmNeg129(a1, a2);
    }
    end = System.currentTimeMillis();
    System.out.println("test_addImmNeg129: " + (end - start));
    start = System.currentTimeMillis();
    for (int i=0; i<ITERS; i++) {
      test_subImm(a1, a2, a3);
    }
    end = System.currentTimeMillis();
    System.out.println("test_subImm: " + (end - start));
    start = System.currentTimeMillis();
    for (int i=0; i<ITERS; i++) {
      test_andImm7(a1, a2);
    }
    end = System.currentTimeMillis();
    System.out.println("test_andImm7: " + (end - start));
    start = System.currentTimeMillis();
    for (int i=0; i<ITERS; i++) {
      test_orImm(a1, a2);
    }
    end = System.currentTimeMillis();
    System.out.println("test_orImm: " + (end - start));
    start = System.currentTimeMillis();
    for (int i=0; i<ITERS; i++) {
      test_xorImm(a1, a2, a3);
    }
    end = System.currentTimeMillis();

    return errn;
  }

  static void test_ci(byte[] a) {
    for (int i = 0; i < a.length; i+=1) {
      a[i] = -123;
    }
  }
  static void test_vi(byte[] a, byte b) {
    for (int i = 0; i < a.length; i+=1) {
      a[i] = b;
    }
  }
  static void test_cp(byte[] a, byte[] b) {
    for (int i = 0; i < a.length; i+=1) {
      a[i] = b[i];
    }
  }
  static void test_2ci(byte[] a, byte[] b) {
    for (int i = 0; i < a.length; i+=1) {
      a[i] = -123;
      b[i] = -103;
    }
  }
  static void test_2vi(byte[] a, byte[] b, byte c, byte d) {
    for (int i = 0; i < a.length; i+=1) {
      a[i] = c;
      b[i] = d;
    }
  }
  static void test_ci_neg(byte[] a) {
    for (int i = a.length-1; i >= 0; i-=1) {
      a[i] = -123;
    }
  }
  static void test_vi_neg(byte[] a, byte b) {
    for (int i = a.length-1; i >= 0; i-=1) {
      a[i] = b;
    }
  }
  static void test_cp_neg(byte[] a, byte[] b) {
    for (int i = a.length-1; i >= 0; i-=1) {
      a[i] = b[i];
    }
  }
  static void test_2ci_neg(byte[] a, byte[] b) {
    for (int i = a.length-1; i >= 0; i-=1) {
      a[i] = -123;
      b[i] = -103;
    }
  }
  static void test_2vi_neg(byte[] a, byte[] b, byte c, byte d) {
    for (int i = a.length-1; i >= 0; i-=1) {
      a[i] = c;
      b[i] = d;
    }
  }
  static void test_ci_oppos(byte[] a) {
    int limit = a.length-1;
    for (int i = 0; i < a.length; i+=1) {
      a[limit-i] = -123;
    }
  }
  static void test_vi_oppos(byte[] a, byte b) {
    int limit = a.length-1;
    for (int i = limit; i >= 0; i-=1) {
      a[limit-i] = b;
    }
  }
  static void test_cp_oppos(byte[] a, byte[] b) {
    int limit = a.length-1;
    for (int i = 0; i < a.length; i+=1) {
      a[i] = b[limit-i];
    }
  }
  static void test_2ci_oppos(byte[] a, byte[] b) {
    int limit = a.length-1;
    for (int i = 0; i < a.length; i+=1) {
      a[limit-i] = -123;
      b[i] = -103;
    }
  }
  static void test_2vi_oppos(byte[] a, byte[] b, byte c, byte d) {
    int limit = a.length-1;
    for (int i = limit; i >= 0; i-=1) {
      a[i] = c;
      b[limit-i] = d;
    }
  }
  static void test_ci_off(byte[] a) {
    for (int i = 0; i < a.length-OFFSET; i+=1) {
      a[i+OFFSET] = -123;
    }
  }
  static void test_vi_off(byte[] a, byte b) {
    for (int i = 0; i < a.length-OFFSET; i+=1) {
      a[i+OFFSET] = b;
    }
  }
  static void test_cp_off(byte[] a, byte[] b) {
    for (int i = 0; i < a.length-OFFSET; i+=1) {
      a[i+OFFSET] = b[i+OFFSET];
    }
  }
  static void test_2ci_off(byte[] a, byte[] b) {
    for (int i = 0; i < a.length-OFFSET; i+=1) {
      a[i+OFFSET] = -123;
      b[i+OFFSET] = -103;
    }
  }
  static void test_2vi_off(byte[] a, byte[] b, byte c, byte d) {
    for (int i = 0; i < a.length-OFFSET; i+=1) {
      a[i+OFFSET] = c;
      b[i+OFFSET] = d;
    }
  }
  static void test_ci_inv(byte[] a, int k) {
    for (int i = 0; i < a.length-k; i+=1) {
      a[i+k] = -123;
    }
  }
  static void test_vi_inv(byte[] a, byte b, int k) {
    for (int i = 0; i < a.length-k; i+=1) {
      a[i+k] = b;
    }
  }
  static void test_cp_inv(byte[] a, byte[] b, int k) {
    for (int i = 0; i < a.length-k; i+=1) {
      a[i+k] = b[i+k];
    }
  }
  static void test_2ci_inv(byte[] a, byte[] b, int k) {
    for (int i = 0; i < a.length-k; i+=1) {
      a[i+k] = -123;
      b[i+k] = -103;
    }
  }
  static void test_2vi_inv(byte[] a, byte[] b, byte c, byte d, int k) {
    for (int i = 0; i < a.length-k; i+=1) {
      a[i+k] = c;
      b[i+k] = d;
    }
  }
  static void test_ci_scl(byte[] a) {
    for (int i = 0; i*SCALE < a.length; i+=1) {
      a[i*SCALE] = -123;
    }
  }
  static void test_vi_scl(byte[] a, byte b) {
    for (int i = 0; i*SCALE < a.length; i+=1) {
      a[i*SCALE] = b;
    }
  }
  static void test_cp_scl(byte[] a, byte[] b) {
    for (int i = 0; i*SCALE < a.length; i+=1) {
      a[i*SCALE] = b[i*SCALE];
    }
  }
  static void test_2ci_scl(byte[] a, byte[] b) {
    for (int i = 0; i*SCALE < a.length; i+=1) {
      a[i*SCALE] = -123;
      b[i*SCALE] = -103;
    }
  }
  static void test_2vi_scl(byte[] a, byte[] b, byte c, byte d) {
    for (int i = 0; i*SCALE < a.length; i+=1) {
      a[i*SCALE] = c;
      b[i*SCALE] = d;
    }
  }
  static void test_cp_alndst(byte[] a, byte[] b) {
    for (int i = 0; i < a.length-ALIGN_OFF; i+=1) {
      a[i+ALIGN_OFF] = b[i];
    }
  }
  static void test_cp_alnsrc(byte[] a, byte[] b) {
    for (int i = 0; i < a.length-ALIGN_OFF; i+=1) {
      a[i] = b[i+ALIGN_OFF];
    }
  }
  static void test_2ci_aln(byte[] a, byte[] b) {
    for (int i = 0; i < a.length-ALIGN_OFF; i+=1) {
      a[i+ALIGN_OFF] = -123;
      b[i] = -103;
    }
  }
  static void test_2vi_aln(byte[] a, byte[] b, byte c, byte d) {
    for (int i = 0; i < a.length-ALIGN_OFF; i+=1) {
      a[i] = c;
      b[i+ALIGN_OFF] = d;
    }
  }
  static void test_cp_unalndst(byte[] a, byte[] b) {
    for (int i = 0; i < a.length-UNALIGN_OFF; i+=1) {
      a[i+UNALIGN_OFF] = b[i];
    }
  }
  static void test_cp_unalnsrc(byte[] a, byte[] b) {
    for (int i = 0; i < a.length-UNALIGN_OFF; i+=1) {
      a[i] = b[i+UNALIGN_OFF];
    }
  }
  static void test_2ci_unaln(byte[] a, byte[] b) {
    for (int i = 0; i < a.length-UNALIGN_OFF; i+=1) {
      a[i+UNALIGN_OFF] = -123;
      b[i] = -103;
    }
  }
  static void test_2vi_unaln(byte[] a, byte[] b, byte c, byte d) {
    for (int i = 0; i < a.length-UNALIGN_OFF; i+=1) {
      a[i] = c;
      b[i+UNALIGN_OFF] = d;
    }
  }
  static void test_addImm127(byte[] a, byte[] b) {
    for (int i = 0; i < a.length; i++) {
      b[i] = (byte) (a[i] + 127);
    }
  }
  static void test_addImm(byte[] a, byte[] b, byte[] c) {
    for (int i = 0; i < a.length; i++) {
      b[i] = (byte) (a[i] + 8);
      c[i] = (byte) (a[i] + 255);
    }
  }
  static void test_addImm256(byte[] a, byte[] b) {
    for (int i = 0; i < a.length; i++) {
      b[i] = (byte) (a[i] + 256);
    }
  }
  static void test_addImmNeg128(byte[] a, byte[] b) {
    for (int i = 0; i < a.length; i++) {
      b[i] = (byte) (a[i] + (-128));
    }
  }
  static void test_addImmNeg129(byte[] a, byte[] b) {
    for (int i = 0; i < a.length; i++) {
      b[i] = (byte) (a[i] + (-129));
    }
  }
  static void test_subImm(byte[] a, byte[] b, byte[] c) {
    for (int i = 0; i < a.length; i++) {
      b[i] = (byte) (a[i] - 56);
      c[i] = (byte) (a[i] - 256);
    }
  }
  static void test_andImm21(byte[] a, byte[] b) {
    for (int i = 0; i < a.length; i++) {
      b[i] = (byte) (a[i] & 21);
    }
  }
  static void test_andImm7(byte[] a, byte[] b) {
    for (int i = 0; i < a.length; i++) {
      b[i] = (byte) (a[i] & 7);
    }
  }
  static void test_orImm(byte[] a, byte[] b) {
    for (int i = 0; i < a.length; i++) {
      b[i] = (byte) (a[i] | 3);
    }
  }
  static void test_xorImm(byte[] a, byte[] b, byte[] c) {
    for (int i = 0; i < a.length; i++) {
      b[i] = (byte) (a[i] ^ 127);
      c[i] = (byte) (a[i] ^ 255);
    }
  }

  static int verify(String text, int i, byte elem, byte val) {
    if (elem != val) {
      System.err.println(text + "[" + i + "] = " + elem + " != " + val);
      return 1;
    }
    return 0;
  }
}
