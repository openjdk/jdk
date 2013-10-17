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

/*
 * @test
 * @bug 8025657
 * @summary Test repeating addExact
 * @compile RepeatTest.java
 * @run main RepeatTest
 *
 */

import java.lang.ArithmeticException;

public class RepeatTest {
  public static void main(String[] args) {
    java.util.Random rnd = new java.util.Random();
    for (int i = 0; i < 50000; ++i) {
      int x = Integer.MAX_VALUE - 10;
      int y = Integer.MAX_VALUE - 10 + rnd.nextInt(5); //rnd.nextInt() / 2;

      int c = rnd.nextInt() / 2;
      int d = rnd.nextInt() / 2;

      int a = addExact(x, y);

      if (a != 36) {
          throw new RuntimeException("a != 0 : " + a);
      }

      int b = nonExact(c, d);
      int n = addExact2(c, d);


      if (n != b) {
        throw new RuntimeException("n != b : " + n + " != " + b);
      }
    }
  }

  public static int addExact2(int x, int y) {
      int result = 0;
      result += java.lang.Math.addExact(x, y);
      result += java.lang.Math.addExact(x, y);
      result += java.lang.Math.addExact(x, y);
      result += java.lang.Math.addExact(x, y);
      return result;
  }

  public static int addExact(int x, int y) {
    int result = 0;
    try {
        result += 5;
        result = java.lang.Math.addExact(x, y);
    } catch (ArithmeticException e) {
        result += 1;
    }
    try {
        result += 6;

        result += java.lang.Math.addExact(x, y);
    } catch (ArithmeticException e) {
        result += 2;
    }
    try {
        result += 7;
        result += java.lang.Math.addExact(x, y);
    } catch (ArithmeticException e) {
        result += 3;
    }
    try {
        result += 8;
        result += java.lang.Math.addExact(x, y);
    } catch (ArithmeticException e) {
        result += 4;
    }
    return result;
  }

  public static int nonExact(int x, int y) {
    int result = x + y;
    result += x + y;
    result += x + y;
    result += x + y;
    return result;
  }
}
