/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

/**
 * @test
 * @bug 6892265
 * @summary System.arraycopy unable to reference elements beyond Integer.MAX_VALUE bytes
 *
 * @run main/othervm Test
 */

public class Test {
  static  final int NCOPY = 1;
  static  final int OVERFLOW = 1;
  static  int[] src2 = new int[NCOPY];
  static  int[] dst2;

  static void test() {
    int N;
    int SIZE;

    N = Integer.MAX_VALUE/4 + OVERFLOW;
    System.arraycopy(src2, 0, dst2, N, NCOPY);
    System.arraycopy(dst2, N, src2, 0, NCOPY);
  }

  public static void main(String[] args) {
    try {
      dst2 = new int[NCOPY + Integer.MAX_VALUE/4 + OVERFLOW];
    } catch (OutOfMemoryError e) {
       System.exit(95); // Not enough memory
    }
    System.out.println("warmup");
    for (int i=0; i <11000; i++) {
      test();
    }
    System.out.println("start");
    for (int i=0; i <1000; i++) {
      test();
    }
    System.out.println("finish");
  }

}
