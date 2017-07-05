/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

public class Verify {
  public static String throwWord(boolean threw) {
    return (threw ? "threw" : "didn't throw");
  }

  public static void verify(int a, int b) {
    boolean exception1 = false, exception2 = false;
    int result1 = 0, result2 = 0;
    try {
      result1 = testIntrinsic(a, b);
    } catch (ArithmeticException e) {
      exception1 = true;
    }
    try {
      result2 = testNonIntrinsic(a, b);
    } catch (ArithmeticException e) {
      exception2 = true;
    }

    if (exception1 != exception2) {
      throw new RuntimeException("Intrinsic version " + throwWord(exception1) + " exception, NonIntrinsic version " + throwWord(exception2) + " for: " + a + " + " + b);
    }
    if (result1 != result2) {
      throw new RuntimeException("Intrinsic version returned: " + a + " while NonIntrinsic version returned: " + b);
    }
  }

  public static int testIntrinsic(int a, int b) {
    return java.lang.Math.addExact(a, b);
  }

  public static int testNonIntrinsic(int a, int b) {
    return safeAddExact(a, b);
  }

  // Copied java.lang.Math.addExact to avoid intrinsification
  public static int safeAddExact(int x, int y) {
    int r = x + y;
    // HD 2-12 Overflow iff both arguments have the opposite sign of the result
    if (((x ^ r) & (y ^ r)) < 0) {
      throw new ArithmeticException("integer overflow");
    }
    return r;
  }
}
