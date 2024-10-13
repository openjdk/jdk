/*
 * Copyright (c) 2023, Intel Corporation. All rights reserved.
 * Intel Math Library (LIBM) Source Code
 *
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
 *
 */

/**
 * @test
 * @bug 8308966
 * @summary Add intrinsic for float/double modulo for x86 AVX2 and AVX512
 * @run main compiler.floatingpoint.FmodTest
 */

 package compiler.floatingpoint;

 import java.lang.Float;

 public class FmodTest {
   static float [] op1 = { 1.2345f, 0.0f, -0.0f, 1.0f/0.0f, -1.0f/0.0f, 0.0f/0.0f };
   static float [] op2 = { 1.2345f, 0.0f, -0.0f, 1.0f/0.0f, -1.0f/0.0f, 0.0f/0.0f };
   static float [][] res = {
      {
        0.0f,
        Float.NaN,
        Float.NaN,
        1.2345f,
        1.2345f,
        Float.NaN,
      },
      {
        0.0f,
        Float.NaN,
        Float.NaN,
        0.0f,
        0.0f,
        Float.NaN,
      },
      {
        -0.0f,
        Float.NaN,
        Float.NaN,
        -0.0f,
        -0.0f,
        Float.NaN,
      },
      {
        Float.NaN,
        Float.NaN,
        Float.NaN,
        Float.NaN,
        Float.NaN,
        Float.NaN,
      },
      {
        Float.NaN,
        Float.NaN,
        Float.NaN,
        Float.NaN,
        Float.NaN,
        Float.NaN,
      },
      {
        Float.NaN,
        Float.NaN,
        Float.NaN,
        Float.NaN,
        Float.NaN,
        Float.NaN,
      },
   };
   public static void main(String[] args) throws Exception {
     float f1, f2, f3;
     boolean failure = false;
     boolean print_failure = false;
     for (int i = 0; i < 100_000; i++) {
       for (int j = 0; j < op1.length; j++) {
         for (int k = 0; k < op2.length; k++) {
           f1 = op1[j];
           f2 = op2[k];
           f3 = f1 % f2;

           if (Float.isNaN(res[j][k])) {
             if (!Float.isNaN(f3)) {
               failure = true;
               print_failure = true;
             }
           } else if (Float.isNaN(f3)) {
             failure = true;
             print_failure = true;
           } else if (f3 != res[j][k]) {
             failure = true;
             print_failure = true;
           }

           if (print_failure) {
             System.out.println( "Actual   " + f1 + " % " + f2 + " = " + f3);
             System.out.println( "Expected " + f1 + " % " + f2 + " = " + res[j][k]);
             print_failure = false;
           }
         }
       }
     }

     if (failure) {
       throw new RuntimeException("Test Failed");
     } else {
       System.out.println("Test passed.");
     }
   }
 }
