/*
 * Copyright (c) 2023, Red Hat, Inc. All rights reserved.
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
 * bug 8312440
 * @summary assert(cast != nullptr) failed: must have added a cast to pin the node
 * @run main/othervm -XX:-BackgroundCompilation TestSunkNodeMissingCastAssert
 */


public class TestSunkNodeMissingCastAssert {
  private static int N = 500;
  private static int ia[] = new int[N];
  private static volatile int ib[] = new int[N];

  private static void test() {
    for (int k = 1; k < 200; k++)
      switch (k % 5) {
      case 0:
        ia[k - 1] -= 15;
      case 2:
        for (int m = 0; m < 1000; m++);
      case 3:
        ib[k - 1] <<= 5;
      case 4:
        ib[k + 1] <<= 3;
      }
  }

  public static void main(String[] args) {
    for (int i = 0; i < 20000; i++) {
      test();
    }
  }
}

